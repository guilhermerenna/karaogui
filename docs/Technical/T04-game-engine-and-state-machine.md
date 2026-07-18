# T04 — Game Engine & Lifecycle State Machine

This document specifies the **server-authoritative** game engine: the game and
performance state machines, the timers that drive them, and exactly when scores lock
and the ranking recomputes. It orchestrates the commands from T02 and the events from
T03. Assignment and scoring *algorithms* are referenced here but defined in T05.

Principle: **every transition happens on the server.** Clients request actions (T02)
and observe results (T03); they never decide a transition. Timer expiries are decided
by the engine, not by any client clock.

---

## 1. Game state machine

States (T01 `game_state`): `CREATED → ACTIVE → OVER`.

```
 CREATED ──start(host)──> ACTIVE ──resolve(OVER, host)──> OVER
                            │  ▲
                queue drains│  │resolve(MORE, host)
                            ▼  │
                     (QUEUE_EMPTY_PROMPT)
```

| From | Event/command | To | Guard |
|------|---------------|----|-------|
| CREATED | `POST /start` (host) | ACTIVE | caller is host; emits `GAME_STARTED` |
| ACTIVE | queue becomes empty | ACTIVE (prompting) | emits `QUEUE_EMPTY_PROMPT` |
| ACTIVE (prompting) | resolve `MORE` (host) | ACTIVE | awaits new submissions |
| ACTIVE (prompting) | resolve `OVER` (host) | OVER | emits `GAME_ENDED`, results available |

- "Prompting" is a transient condition of `ACTIVE`, not a separate persisted state:
  when the queue has no runnable performance, the engine raises the prompt and pauses
  performance progression until the host resolves it (or a new performance is
  submitted, which the engine treats as an implicit `MORE`). **[DECISION]** allow a
  new submission to auto-resolve the prompt as `MORE`.
- Joining is allowed in `CREATED` and `ACTIVE` (T02 §4.1); never in `OVER`.

## 2. Performance lifecycle state machine

States (T01 `performance_state`):
`QUEUED → ANNOUNCED → CONFIRMING → RUNNING → LOCKED`, with `SKIPPED` as a terminal
branch from `CONFIRMING`.

```
QUEUED ─pick next─> ANNOUNCED ─(begin confirm)─> CONFIRMING ─┬─ all confirmed ─> RUNNING ─ performers done
                                                             │                              & all judges submitted
                                                             └─ deadline, not enough ─> SKIPPED        │
                                                                                                       ▼
                                                                                                    LOCKED
```

### 2.1 Transitions & timers

Config constants (T00 §6): `READY_CONFIRM_WINDOW=30s`, `REPLACEMENT_OPEN_AFTER=15s`.

| From → To | Trigger | Engine actions | Events (T03) |
|-----------|---------|----------------|--------------|
| QUEUED → ANNOUNCED | engine picks the next runnable performance | **fill RANDOM slots** via assignment (T05), excluding on-break/skipped players; build performer list | `PERFORMANCE_ANNOUNCED`, `RANDOM_SLOTS_FILLED` |
| ANNOUNCED → CONFIRMING | engine begins the ready window | set `confirm_deadline_at = now+30s`; `replacementOpensAt = now+15s`; notify performers privately | `PERFORMANCE_CONFIRMING`, `YOU_MUST_CONFIRM` |
| (within CONFIRMING) | 15s elapsed | open volunteering for unconfirmed slots | `REPLACEMENT_OPEN` |
| (within CONFIRMING) | performer confirms | mark slot `CONFIRMED` | `SLOT_CONFIRMED` |
| (within CONFIRMING) | volunteer replaces | swap slot: keep `original_player_id`, set `current_player_id`, `REPLACED`+confirmed | `SLOT_REPLACED` |
| CONFIRMING → RUNNING | **all slots confirmed** (before deadline) | start performance | `PERFORMANCE_STARTED` |
| CONFIRMING → SKIPPED | **deadline reached, not all confirmed** | vacate slots; move on | `PERFORMANCE_SKIPPED`, `SLOT_VACATED` |
| RUNNING → LOCKED | performers done **and** all judges submitted | compute score (T05); add to players; freeze ratings | `PERFORMANCE_LOCKED`, `RANKING_UPDATED` |

### 2.2 "Performers done"

`RUNNING → LOCKED` needs both conditions (Business 02 §f):
1. **Performers done** — signaled how depends on type:
   - **Deterministic (Physical Challenges):** judges record the winner (T05); that
     recording marks performers done.
   - **Subjective/Trivia:** **[DECISION]** the host (as a player) or the performance's
     natural completion advances it; simplest is a "performers done" signal available
     to the host/author. Finalize the exact trigger in build — the state machine only
     requires the boolean.
2. **All judges submitted** — every `judge_assignment` has an `evaluation` with
   `submitted_at` set.

For **judge-less** types (none currently require zero judges except deterministic
challenges, which still use confirm-only judges), condition (2) is vacuously true.

### 2.3 One performance at a time

The engine runs **at most one** performance past `ANNOUNCED` at a time. The queue
(`performance.queue_position`, T01) is advanced only when the current performance
reaches a terminal state (`LOCKED` or `SKIPPED`).

## 3. Timer management

- Timers are **deadline timestamps persisted on the performance** (`confirm_deadline_at`,
  etc.), not in-memory only — so a backend restart can recover pending expiries.
- A lightweight **scheduler** (Spring `@Scheduled` sweep, or per-performance scheduled
  task) checks for due deadlines and fires the transition:
  - **Confirmation deadline:** at `confirm_deadline_at`, if not all confirmed →
    `SKIPPED`; if all confirmed earlier, the confirm handler already moved to
    `RUNNING` (the deadline check then no-ops).
  - **Replacement open:** at `+15s`, emit `REPLACEMENT_OPEN`.
- **Break expiry** (`on_break_until`) is likewise deadline-based; the engine treats a
  player as available again once the timestamp passes (no explicit event needed beyond
  `PLAYER_BREAK_CHANGED` on set/clear).
- **Clock authority:** all deadlines are server time; clients only render countdowns
  from the timestamps (T03 §7).

## 4. Break & reassignment flow

When a player takes a break (T02 §4.3) or their break makes them ineligible:

1. Set `on_break_until = now + BREAK_DURATION` (re-activation resets it, Business 05).
2. **Reassign any active assignments:**
   - **Performer slots** they hold in a not-yet-`RUNNING` performance: the engine
     reassigns the slot. During `CONFIRMING`, this surfaces as the same
     volunteer/replacement path — the original name is kept (`original_player_id`) and
     shown struck through with the replacement beneath (`SLOT_REPLACED`, Business
     02/05). If no immediate replacement exists, the slot stays open for volunteers
     until the deadline.
   - **Judge assignments:** a player on break **must not** be a judge (Business
     01/05). If assigned and then on break, the engine replaces them with another
     eligible judge (assignment, T05).
3. Emit `PLAYER_BREAK_CHANGED`; affected performers/judges get private notices
   (`YOU_WERE_REPLACED`, `YOUR_ROLE`).

Skips are simpler: a `player_skip` row (T01) removes the player from **random
eligibility** for that one performance (T05); no reassignment needed unless they were
already a random pick before skipping (then treated like a break-style reassignment
for that slot).

## 5. Scoring lock-in & ranking recompute

On `RUNNING → LOCKED`:

1. **Freeze ratings:** set `rating.locked = true` for all ratings on the performance;
   reject further rating edits (`409 PERFORMANCE_LOCKED`, T02 §4.7). Emit
   `RATING_LOCKED` privately to raters.
2. **Compute the performance score** (T05): subjective 85/15, trivia
   correct+speed−penalty, or deterministic winner.
3. **Apply to players:** add each performer's share to `player.score`; increment
   `player.performance_participation_count` for those who actually performed
   (backend-only; drives future assignment).
4. **Emit** `PERFORMANCE_LOCKED` and `RANKING_UPDATED`.

Like-point bonuses (T05) update `player.score` independently as likes arrive and emit
their own `RANKING_UPDATED`; they are not tied to performance lock-in.

## 6. Auto-queued Physical Challenges (engine hook)

The recurring **challenge-injection job** (Business 05; algorithm in T05) runs on the
engine side:
- On its interval (~60s), it counts other-type performances since the last Physical
  Challenge; when ≥ `PHYSICAL_CHALLENGE_THRESHOLD` (4), it **appends** the next
  challenge (cyclic Balloon→Water→Marshmallow→Cookie) to the queue as a `QUEUED`
  performance with `author_player_id = null`.
- These enter the same performance state machine as everything else; their 4 random
  performers are filled at `ANNOUNCED` like any RANDOM slots.

## 7. Concurrency & consistency (engine-level)

- State transitions are performed in **single transactions** with **optimistic
  locking** (`@Version`, T01 §3) on `performance` and `player`. Concurrent commands
  that lose the version race retry or return `409`.
- **Confirm/volunteer races** (two volunteers for the same slot): guarded by the
  slot's version / a conditional update on `performer_slot`; the loser gets `409`
  (T02 §4.5) and `SLOT_REPLACED` reflects the winner.
- **Rating edits vs lock:** the lock transaction and rating upserts contend on the
  performance; once `LOCKED`, upserts are rejected. Detailed strategy in **T07**.

## 8. Engine responsibilities summary

| Concern | Owned here (T04) | Defined elsewhere |
|---------|------------------|-------------------|
| Game/performance transitions | ✅ | — |
| Timer expiry (confirm/replace/break) | ✅ | constants T00 |
| RANDOM slot filling trigger | ✅ (when) | assignment logic T05 |
| Break reassignment orchestration | ✅ | eligible-pick logic T05 |
| Score lock-in & ranking recompute trigger | ✅ (when) | score math T05 |
| Challenge injection scheduling | ✅ (hook) | cadence/order logic T05 |
| Command endpoints | — | T02 |
| Event broadcasts | — | T03 |

## 9. Open items for later docs
- Exact **"performers done"** trigger per subjective/trivia type → confirm in build
  (§2.2).
- Whether a new submission during the end-prompt **auto-resolves** `MORE` → confirm
  (§1).
- Precise **locking strategy** (optimistic vs. short serializable sections) for
  hot rows → **T07**.
