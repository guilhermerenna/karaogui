# T05 — Assignment, Scoring & Scheduled Jobs

This document defines the **algorithms** the earlier docs deferred: the biased random
assignment, the per-type scoring math (including the exact criteria keys used in
`evaluation_score` / `rating_score`), trivia answer capture and speed timing, comment
like points, and the recurring Physical-Challenge injection job. It is the concrete
counterpart to T04, which owns *when* these run; T05 owns *what they compute*.

All point values here are **externalized configuration** (T00 §6) unless noted —
the numbers below are initial values, tunable without code changes.

---

## 1. Biased random assignment

Used when the engine fills `RANDOM` performer slots at `ANNOUNCED` (T04 §2.1). The
business intent (Business 05 §1): **strongly** favor players who have performed
little, **gently** favor players with fewer points.

### 1.1 Eligible pool

A player is eligible for a performance's random slot iff **all** hold:
- not on break: `on_break_until IS NULL OR on_break_until <= now()`;
- has not skipped this performance: no `player_skip` row for `(performance_id, player_id)`;
- not already a performer or judge **on this performance** (no `performer_slot` /
  `judge_assignment`);
- (for author-cannot-perform types) not the `author_player_id`.

If the eligible pool is smaller than the number of open random slots, fill what is
possible; the remaining slots stay open for volunteers (T04 §4) and may lead to a
`SKIPPED` at the deadline.

### 1.2 Weight formula

Each eligible player gets a **selection weight**; slots are drawn **without
replacement** using weighted sampling (a player already drawn for slot *k* is removed
before drawing slot *k+1*).

Let, for a player *p*:
- `perf(p)` = `performance_participation_count` (backend-only, T01 §2.2),
- `score(p)` = `player.score`,
- `maxPerf` = the max `perf` across the eligible pool,
- `maxScore` = the max `score` across the eligible pool.

```
weight(p) = 1
          + W_PERF  * (maxPerf  - perf(p))          // primary: seldom-performers boosted
          + W_SCORE * (maxScore - score(p)) / SCORE_NORM   // secondary: low-scorers gently boosted
```

- The **base `1`** guarantees every eligible player retains a non-zero chance (never
  forced out entirely).
- `(maxPerf - perf(p))` makes a 1-performance player far heavier than a 5-performance
  one; this is the **primary** lever, so `W_PERF` dominates.
- The score term is normalized by `SCORE_NORM` so raw point magnitudes don't swamp the
  participation signal, and scaled by a **smaller** `W_SCORE`.
- **Late joiners** (`perf=0, score=0`) land near the top weight naturally — no special
  case (Business 05).

Config constants (T00 §6 catalog to be extended):

| Constant | Meaning | Initial |
|----------|---------|---------|
| `ASSIGN_W_PERF` | primary participation weight | **10** |
| `ASSIGN_W_SCORE` | secondary low-score weight | **1** |
| `ASSIGN_SCORE_NORM` | score normalization divisor | **10** |

> **[DECISION]** linear weighting (not exponential). It is transparent, easy to reason
> about, and the base `1` + dominant `W_PERF` already produce a strong participation
> bias. Revisit only if play-testing shows the bias is too weak/strong.

### 1.3 Determinism & fairness notes

- Sampling uses a seeded RNG per draw for testability; the seed is not persisted.
- Ties in weight are broken by the RNG, not by `player_id` order (avoids alphabetical bias).
- The result is emitted as `RANDOM_SLOTS_FILLED` (T03 §5.2) and persisted as
  `performer_slot` rows with `origin=RANDOM`.

---

## 2. Scoring overview

`scoring_kind` (T01 §1) selects the algorithm:

| `scoring_kind` | Types | Algorithm |
|----------------|-------|-----------|
| `SUBJECTIVE` | Karaoke, Magic, Dance, Acting/Mimic, Standup, Reverse Mimic | §3 — 85/15 judge/audience |
| `TRIVIA` | Trivia | §4 — correct + speed − penalty |
| `DETERMINISTIC` | Physical Challenges | §5 — winner-takes-points |

All scoring runs **at lock-in** (`RUNNING → LOCKED`, T04 §5), inside the lock
transaction, and produces a per-performer point contribution added to `player.score`.

---

## 3. Subjective scoring (85 / 15)

### 3.1 Criteria keys

Criteria are the `criterion` values stored in `evaluation_score` and `rating_score`
(T01 §2.8/§2.10). **[DECISION]** a small shared criteria set keyed by type, each scored
on an integer **1–10** scale.

| Type | Judge evaluation criteria | Audience rating criteria |
|------|---------------------------|--------------------------|
| Karaoke | `PITCH`, `ENERGY`, `STAGE_PRESENCE` | `OVERALL` |
| Dance | `CHOREOGRAPHY`, `ENERGY`, `STAGE_PRESENCE` | `OVERALL` |
| Magic trick | `DIFFICULTY`, `EXECUTION`, `SHOWMANSHIP` | `OVERALL` |
| Acting/Mimic | `ACCURACY`, `EXPRESSIVENESS`, `STAGE_PRESENCE` | `OVERALL` |
| Standup | `HUMOR`, `DELIVERY`, `ORIGINALITY` | `OVERALL` |
| Reverse Mimic | `ACCURACY`, `CREATIVITY`, `STAGE_PRESENCE` | `OVERALL` |

- Audience keeps a single `OVERALL` (1–10) to keep rating friction-free (Business 04);
  judges use the richer per-type set.
- Criteria keys live in a backend enum/catalog shared with the DTOs (T02 §4.6/§4.7),
  so frontends render the right sliders per type (T06).

### 3.2 Judge component (whole-then-individual)

Each judge's evaluation stores a **baseline** row (`subject_player_id IS NULL`) per
criterion plus optional **per-performer override** rows (T01 §2.8). Resolve a judge
*j*'s score for performer *p* on criterion *c*:

```
value(j, p, c) = override(j, p, c)  if present
               else baseline(j, c)
```

A judge's score **for performer p** is the mean across that type's judge criteria:

```
judgeScore(j, p) = avg over criteria c of value(j, p, c)          // 1..10
```

The performer's **aggregate judge score** averages across all submitted judges:

```
J(p) = avg over judges j of judgeScore(j, p)                      // 1..10
```

- **Partial / absent judges (force-lock).** When a performance is force-locked at its
  judging deadline (T04 §2.2) rather than by all judges submitting, only the judges who
  actually submitted contribute to `J(p)`. If **no** judge submitted, the judge
  component defaults to a neutral **5.0** (mid-scale). Any single missing criterion
  within a submitted evaluation likewise falls back to **5.0**. This keeps a
  judge-dropout from producing a `NaN`/zero score — the performance still locks with a
  sensible number and the game proceeds.

### 3.3 Audience component

Each audience rating's `total_score` is the mean of its `rating_score` sub-scores
(here just `OVERALL`, so `total_score = OVERALL`), on the same 1–10 scale. The
performer's **aggregate audience score** is the mean across all raters:

```
A = avg over ratings r of r.total_score                          // 1..10
```

- Audience rates the **performance**, not individuals (single `OVERALL`), so `A` is the
  same for every performer of the performance.
- If there are **no ratings**, the audience term is dropped and the judge component
  takes the full weight (renormalize to 100% judges) — audience silence never zeroes a
  score.

### 3.4 Combine and award

Per performer *p*, on a 1–10 scale:

```
raw(p)   = SUBJECTIVE_JUDGE_WEIGHT * J(p) + SUBJECTIVE_AUDIENCE_WEIGHT * A
         = 0.85 * J(p) + 0.15 * A                                // (0.85*J if no ratings)
points(p) = round(raw(p) * SUBJECTIVE_POINT_SCALE)
```

| Constant | Meaning | Initial |
|----------|---------|---------|
| `SUBJECTIVE_JUDGE_WEIGHT` | judge share | **0.85** (T00 §6) |
| `SUBJECTIVE_AUDIENCE_WEIGHT` | audience share | **0.15** (T00 §6) |
| `SUBJECTIVE_POINT_SCALE` | 1–10 → points multiplier | **10** (⇒ up to 100 pts) |

Each performer receives `points(p)` added to `player.score`; every performer who
actually performed gets `performance_participation_count += 1` (T04 §5).

---

## 4. Trivia scoring

Trivia is driven by answer correctness and speed, judged against the author-supplied
answer (Business 03/04). The 4 random performers each answer the 10 questions; judges
see the answer and mark each performer's response.

### 4.1 Answer & timing capture

Per question, per performer, the engine records a `trivia_answer`:

| Column | Type | Notes |
|--------|------|-------|
| `id` | `uuid` PK | |
| `performance_id` | `bigint` FK | |
| `game_id` | `uuid` FK | |
| `trivia_question_id` | `uuid` FK → `trivia_question.id` | |
| `performer_player_id` | `uuid` FK → `player.id` | which of the 4 performers |
| `judged_correct` | `boolean` | set by a judge against the known answer |
| `answered_at` | `timestamptz` NULL | when this performer's answer was locked in, for speed ranking |
| UNIQUE | | `(trivia_question_id, performer_player_id)` |

> **[DECISION]** add `trivia_answer` as a T05-owned table (Flyway `V6__trivia_scoring.sql`,
> extending T01 §5). It was flagged as open in T01 §7 ("per-answer capture + speed
> timing").

### 4.2 Per-question points

For each question `q` and performer `p`:

```
if not judged_correct(q, p):  q_points = -TRIVIA_WRONG_PENALTY
else:
    speedRank = rank of answered_at(q, p) among correct answers for q (1 = fastest)
    q_points = TRIVIA_CORRECT_POINTS + TRIVIA_SPEED_BONUS[speedRank]
```

- Speed bonus is a **descending table** by finish order among *correct* answerers, so
  faster correct answers earn more (Business 04). Wrong answers get no speed bonus and
  incur the penalty.
- A performer who does not answer a question scores `0` for it (neither correct nor
  penalized) — **[DECISION]** no-answer is not a wrong answer.

### 4.3 Performer total

```
points(p) = sum over the 10 questions of q_points(q, p)          // may be negative; floored at 0 when applied
```

| Constant | Meaning | Initial |
|----------|---------|---------|
| `TRIVIA_CORRECT_POINTS` | base per correct answer | **5** |
| `TRIVIA_SPEED_BONUS` | bonus by finish rank among correct | **[3, 2, 1, 0]** (1st..4th) |
| `TRIVIA_WRONG_PENALTY` | deduction per wrong answer | **2** |

- **[DECISION]** a performer's applied contribution is `max(0, points(p))` — trivia can
  cost you relative points within the round but never drives a player's cumulative
  score negative.

---

## 5. Deterministic scoring (Physical Challenges)

Judges only **confirm** and record the **winner** (Business 04); no criteria, no 85/15.

```
winner recorded  ⇒ points(winner) = PHYSICAL_CHALLENGE_POINTS ; others = 0
no winner (e.g. all cookies fall) ⇒ everyone scores 0
```

- The "performers done" signal for deterministic types **is** the judges' winner
  recording (T04 §2.2). Recording the winner both marks performers done and supplies
  the score input.
- Per-challenge winner *determination* rules (balloon first-to-burst, cookie 3→2→1,
  etc.) are Business 03 §5; the engine stores only the resulting `winner_player_id`
  (nullable — null ⇒ no winner ⇒ all zero).

| Constant | Meaning | Initial |
|----------|---------|---------|
| `PHYSICAL_CHALLENGE_POINTS` | winner award | **50** |

Winner capture: **[DECISION]** store `winner_player_id uuid NULL` on `performance`
(only meaningful when `type=PHYSICAL_CHALLENGE`), set at confirm time
(Flyway `V6__trivia_scoring.sql` also adds this column).

---

## 6. Comment like points

A minor engagement bonus (Business 04) applied **independently** of performance
lock-in (T04 §5):

```
on like added   (comment_like inserted) ⇒ author.score += LIKE_POINTS ; emit RANKING_UPDATED
on like removed (comment_like deleted)  ⇒ author.score -= LIKE_POINTS ; emit RANKING_UPDATED
```

- One like per player per comment is enforced by the `comment_like` PK (T01 §2.12), so
  the bonus cannot be farmed by re-liking.
- Applied in the same transaction that maintains `comment.like_count`.
- `LIKE_POINTS = 1` (T00 §6). By design this must never rival performing.

---

## 7. Challenge-injection scheduled job

The recurring job (Business 05 §5; engine hook in T04 §6) that appends Physical
Challenges to the queue.

### 7.1 Trigger & cadence

- Spring `@Scheduled(fixedDelayString = "${PHYSICAL_CHALLENGE_JOB_INTERVAL}")`,
  ~**60s** (T00 §6). Runs per **ACTIVE** game.
- Counts other-type performances **since the last Physical Challenge** in the queue
  (by `queue_position` / `game_local_number`). When that count `>= PHYSICAL_CHALLENGE_THRESHOLD`
  (**4**), append the next challenge.

### 7.2 Cyclic ordering

```
order = [BALLOON, WATER, MARSHMALLOW, COOKIE]
next   = order[ (challengesInjectedSoFar) mod 4 ]
```

- Appended as a `QUEUED` `performance` with `type=PHYSICAL_CHALLENGE`,
  `physical_kind=next`, `author_player_id=null` (T01 §2.4).
- Its 4 `RANDOM` performer slots are filled later at `ANNOUNCED` by §1, like any other
  performance — the job only enqueues.

### 7.3 Idempotency & concurrency

- The job runs in a short transaction and re-checks the threshold inside it, so two
  overlapping sweeps cannot double-inject (optimistic guard on the last-challenge
  marker).
- If the game is `OVER` or currently prompting on an empty queue (T04 §1), the job
  no-ops.

---

## 8. Flyway addition

Extends the T01 §5 migration set:

- `V6__trivia_scoring.sql` — `trivia_answer` table (§4.1) + `performance.winner_player_id` column (§5).
- `V11__performance_judging_deadline.sql` — `performance.duration_seconds` (nullable; the
  YouTube video length fetched at queue time, §11) + `performance.judging_deadline_at`
  (nullable; the RUNNING force-lock deadline, T04 §3).

---

## 9. Constants catalog (T05 additions)

All values below are **externalized configuration** (not code literals). The full,
human-facing **dial board** for every balance value — with meaning, initial value, and
"effect if increased" — is **T08 (Tuning & Game-Balance Reference)**; T08 is
authoritative when a value changes. The list here is just what T05 introduces:

| Constant | Meaning | Initial |
|----------|---------|---------|
| `ASSIGN_W_PERF` | assignment: primary participation weight | 10 |
| `ASSIGN_W_SCORE` | assignment: secondary low-score weight | 1 |
| `ASSIGN_SCORE_NORM` | assignment: score normalization divisor | 10 |
| `SUBJECTIVE_POINT_SCALE` | subjective: 1–10 → points | 10 |
| `TRIVIA_CORRECT_POINTS` | trivia: per correct | 5 |
| `TRIVIA_SPEED_BONUS` | trivia: bonus by rank `[3,2,1,0]` | [3,2,1,0] |
| `TRIVIA_WRONG_PENALTY` | trivia: per wrong | 2 |
| `PHYSICAL_CHALLENGE_POINTS` | deterministic: winner award | 50 |

(`SUBJECTIVE_JUDGE_WEIGHT`, `SUBJECTIVE_AUDIENCE_WEIGHT`, `LIKE_POINTS`,
`PHYSICAL_CHALLENGE_JOB_INTERVAL`, `PHYSICAL_CHALLENGE_THRESHOLD` already in T00 §6.)
All are catalogued and explained for tuning in **T08**.

---

## 11. YouTube metadata lookup & the RUNNING force-lock timer

Karaoke/Dance performances embed a YouTube video. Two concerns are handled at **queue
time** by a best-effort call to the **YouTube Data API v3** `videos.list` endpoint
(`part=contentDetails,status`, quota cost 1 unit/call):

1. **Song duration** → drives the RUNNING judging-deadline timer (T04 §3), so a dropped
   judge can never stall the game (T04 §2.2).
2. **Embeddability & existence** → reject links that can't play *before* they enter the
   queue, complementing the phone-side visual preview.

### 11.1 The metadata client (`YoutubeMetadataClient`)

`fetch(youtubeUrl)` returns one of three outcomes:

| Outcome | When | Caller behavior (queue) |
|---------|------|-------------------------|
| **REJECTED** | URL has no parseable video id (`NOT_A_YOUTUBE_VIDEO`); API returns empty `items` — video deleted/private/typo (`VIDEO_NOT_FOUND`); `status.embeddable == false` (`VIDEO_NOT_EMBEDDABLE`) | Reject the submission → `409` with the reason code (T02 §4.4) |
| **UNAVAILABLE** | API could not be consulted: missing key, quota exhausted (403 `quotaExceeded`), network/5xx | **Allow through** with no duration; the timer falls back to `YOUTUBE_FALLBACK_CEILING` |
| **OK** | Real, embeddable video | Queue it; store `duration_seconds` on the performance |

> **[DECISION]** the API is a **soft** dependency. Only a *definite bad-video verdict*
> blocks a submission; a *failure to reach the API* never does — otherwise a quota
> wipe-out would block all queueing until the daily reset (midnight PT). Link validation
> degrades gracefully to the phone preview, and song timing degrades to the fixed
> ceiling.

Duration is parsed from the ISO-8601 `contentDetails.duration` (e.g. `PT4M13S`) via
`java.time.Duration.parse` → seconds, persisted in `performance.duration_seconds`.

### 11.2 Deadline computation & sweep

At `CONFIRMING → RUNNING` (T04 §2.1) the engine sets:

```
judging_deadline_at = started_at
                    + (duration_seconds ?: YOUTUBE_FALLBACK_CEILING)
                    + JUDGING_GRACE
```

The existing `PerformanceTimerJob` sweep (5s `fixedDelay`, T04 §3) — already responsible
for the `CONFIRMING` skip timer — additionally queries `RUNNING` performances past
`judging_deadline_at` and force-locks them (§3.2 partial-score path).

### 11.3 Configuration

`karaogui.youtube.*` (T08 §1):

| Key | Meaning | Initial |
|-----|---------|---------|
| `karaogui.youtube.apiKey` | Data API v3 key; bound from `YOUTUBE_API_KEY` env (blank ⇒ every lookup is UNAVAILABLE) | *(blank)* |
| `karaogui.youtube.judgingGrace` | extra time after the song for judges to finish before force-lock | **90 s** |
| `karaogui.youtube.fallbackCeiling` | RUNNING ceiling when duration is unknown | **8 min** |

The key is supplied via a git-ignored root `.env` file (`YOUTUBE_API_KEY=...`), which
`docker-compose` loads automatically; `.env.example` documents the variable.

---

## 12. Open items for later docs
- Whether trivia **speed** should use absolute answer time vs. finish-rank among
  correct answerers → currently rank-based (§4.2); revisit if it feels unfair with
  few correct answers.
- Exact per-challenge **winner-determination** UI/flow for judges (how the winner is
  entered) → T06.
- Play-tested tuning of assignment weights and point scales → **T08** (dial board).
