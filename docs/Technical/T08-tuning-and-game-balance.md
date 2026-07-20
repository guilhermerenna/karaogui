# T08 — Tuning & Game-Balance Reference

**Purpose:** a single, flat catalog of every value that changes how the game *feels* —
assignment bias, scoring magnitudes, timers, pacing. These are the knobs you reach for
**after** the game runs and you want to rebalance dynamics; they are **not** wired into
code as literals. This doc is the authoritative index; the algorithm docs (T05) and
architecture doc (T00 §6) reference these names.

## How these are configured

- All values are **externalized configuration** — Spring `@ConfigurationProperties`
  bound from `application.yml` + environment variables (T00 §6). Never hard-coded.
- Grouped under a single `karaogui.*` config namespace so they are trivial to find and
  override per environment/profile without a rebuild.
- Changing any value here requires **no code change** — edit config, restart (or, where
  a live-reload mechanism exists, refresh). Schema and logic are unaffected.

**[DECISION]** keep this document as the human-facing balance guide even though the
constants also appear in T00/T05; those docs describe *mechanism*, this one is the
*dial board*. When a value changes, update it here first.

---

## 1. Pacing & timers *(how fast the game moves)*

| Key | Meaning | Initial | Effect if increased |
|-----|---------|---------|---------------------|
| `karaogui.timers.readyConfirmWindow` | performer ready-confirmation window | **30 s** | more time to confirm; slower pacing, fewer skips |
| `karaogui.timers.replacementOpenAfter` | when volunteers may replace unconfirmed performers | **15 s** | volunteers wait longer; original performers get more grace |
| `karaogui.timers.breakDuration` | "on a break" no-pick window | **15 min** | longer opt-out; fewer eligible players for assignment |
| `karaogui.youtube.judgingGrace` | extra time after the song for judges to finish before the RUNNING force-lock | **90 s** | judges get longer after the song ends; a dropped judge stalls the game longer |
| `karaogui.youtube.fallbackCeiling` | RUNNING force-lock ceiling when the song duration is unknown (API unavailable / not a video) | **8 min** | performances with unknown duration run longer before force-locking |
| `karaogui.scoreboard.pageInterval` | how long each scoreboard page shows before cycling | **~5 s** | slower scoreboard rotation |

> **YouTube lookup (T05 §11).** `karaogui.youtube.apiKey` is **not** a balance dial — it's a
> credential bound from the `YOUTUBE_API_KEY` env var (git-ignored `.env`). Blank ⇒ every
> lookup is *UNAVAILABLE*, so durations fall back to `judgingGrace` + `fallbackCeiling` and
> link validation degrades to the phone preview. The two timers above *are* dials.

## 2. Scoreboard display

| Key | Meaning | Initial | Effect if increased |
|-----|---------|---------|---------------------|
| `karaogui.scoreboard.maxDisplayedPlayer` | max players per scoreboard page | **5** | more players per page, fewer pages |

## 3. Assignment bias *(who gets pulled into random slots)*

The strongest dynamics lever — these decide how aggressively under-participants are favored.

| Key | Meaning | Initial | Effect if increased |
|-----|---------|---------|---------------------|
| `karaogui.assignment.wPerf` | **primary** participation weight | **10** | seldom-performers picked even more strongly |
| `karaogui.assignment.wScore` | **secondary** low-score weight | **1** | low-scorers gently favored more |
| `karaogui.assignment.scoreNorm` | score normalization divisor | **10** | dampens the score term (larger = weaker score influence) |

> Formula (T05 §1.2): `weight = 1 + wPerf·(maxPerf−perf) + wScore·(maxScore−score)/scoreNorm`.
> The base `1` guarantees everyone keeps a non-zero chance. `wPerf` should stay well
> above `wScore` to preserve "participation first, points second."

## 4. Scoring magnitudes *(how many points each thing is worth)*

### 4.1 Subjective (Karaoke, Magic, Dance, Acting/Mimic, Standup, Reverse Mimic)

| Key | Meaning | Initial | Effect if increased |
|-----|---------|---------|---------------------|
| `karaogui.scoring.subjectiveJudgeWeight` | judge share of the score | **0.85** | judges matter more, audience less |
| `karaogui.scoring.subjectiveAudienceWeight` | audience share of the score | **0.15** | audience matters more (keep the two summing to 1.0) |
| `karaogui.scoring.subjectivePointScale` | 1–10 rating → points multiplier | **10** (⇒ up to 100 pts) | bigger swings per subjective performance |

### 4.2 Trivia

| Key | Meaning | Initial | Effect if increased |
|-----|---------|---------|---------------------|
| `karaogui.scoring.triviaCorrectPoints` | base points per correct answer | **5** | trivia rewards correctness more |
| `karaogui.scoring.triviaSpeedBonus` | bonus by finish rank among correct `[1st,2nd,3rd,4th]` | **[3, 2, 1, 0]** | speed matters more relative to correctness |
| `karaogui.scoring.triviaWrongPenalty` | deduction per wrong answer | **2** | wrong answers hurt more |

### 4.3 Deterministic (Physical Challenges)

| Key | Meaning | Initial | Effect if increased |
|-----|---------|---------|---------------------|
| `karaogui.scoring.physicalChallengePoints` | winner award (all-or-nothing) | **50** | challenges swing the ranking harder |

### 4.4 Engagement bonus

| Key | Meaning | Initial | Effect if increased |
|-----|---------|---------|---------------------|
| `karaogui.scoring.likePoints` | points to a comment author per like | **1** | comment engagement rivals performing (keep tiny by design) |

## 5. Challenge injection *(how often auto-challenges appear)*

| Key | Meaning | Initial | Effect if increased |
|-----|---------|---------|---------------------|
| `karaogui.challenge.jobInterval` | how often the injector checks the queue | **~60 s** | slower detection of the threshold |
| `karaogui.challenge.threshold` | other-type performances since last challenge before injecting | **4** | challenges appear **less** often |

Cyclic order is fixed (`BALLOON → WATER → MARSHMALLOW → COOKIE → …`, T05 §7.2) and is a
**rule, not a tunable**.

---

## 6. Cross-reference

| This doc's group | Mechanism defined in |
|------------------|----------------------|
| Pacing & timers | T04 (engine), T00 §6 |
| YouTube lookup & force-lock | T05 §11, T04 §2.2/§3 |
| Scoreboard | T06 §4 (frontend paging), Business 04 |
| Assignment bias | T05 §1 |
| Subjective scoring | T05 §3 |
| Trivia scoring | T05 §4 |
| Deterministic scoring | T05 §5 |
| Like points | T05 §6 |
| Challenge injection | T05 §7 |

## 7. Tuning workflow (post-launch)

1. Run a session; observe which dynamic feels off (e.g. "same people always perform",
   "challenges dominate the score", "scoreboard flips too fast").
2. Find the relevant key above; adjust in config for the target profile.
3. Restart the backend (config bind on startup). No code, schema, or migration change.
4. Record the change and its observed effect so balance history is traceable.

> Sensible starting bounds (guardrails, not hard limits): keep
> `subjectiveJudgeWeight + subjectiveAudienceWeight = 1.0`; keep `assignment.wPerf >
> assignment.wScore`; keep `scoring.likePoints` small relative to
> `subjectivePointScale` so likes never rival performing.
