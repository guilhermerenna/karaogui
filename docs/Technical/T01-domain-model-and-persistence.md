# T01 — Domain Model & Persistence

This document turns the conceptual entities (Business `07-glossary-and-entities.md`)
into a concrete relational schema plus JPA mapping guidance. It defines the tables,
keys, enums, and how each business **invariant** is enforced. Schema is owned by
**Flyway** (versioned SQL); Hibernate runs with `ddl-auto=validate` and never
generates DDL (T00 §2).

Conventions:
- All tables are **scoped to a game**; every table except `game` carries a
  `game_id FK`.
- Primary keys: `game` uses an internal **UUID** `id` (never shown to players; used
  in URLs/topics) plus a separate human-facing **`join_code`** (§2.1a); `performance`
  uses a per-game **auto-increment integer** (Business requirement); other tables use
  UUIDs unless noted.
- Timestamps are `timestamptz`; enums are stored as text with DB `CHECK`/lookup, and
  mapped in JPA as `@Enumerated(EnumType.STRING)`.
- Money-like/score values are integers unless a fractional score is required, in
  which case `numeric`.

---

## 1. Enumerations

### `game_state`
`CREATED` → `ACTIVE` → `OVER`.
(Detailed transitions in T04.)

### `performance_type`
`KARAOKE`, `MAGIC_TRICK`, `DANCE`, `ACTING_MIMIC`, `STANDUP`, `TRIVIA`,
`REVERSE_MIMIC`, `PHYSICAL_CHALLENGE`.

> Note: the Business "Talent Show" is an umbrella, not a stored type — its
> categories (magic/dance/acting/standup) are first-class `performance_type` values.
> A derived/grouping label ("Talent Show") can be presented in the UI without being a
> stored type.

### `physical_challenge_kind`
`BALLOON`, `WATER`, `MARSHMALLOW`, `COOKIE`. Only set when
`performance_type = PHYSICAL_CHALLENGE`. The cyclic ordering is
`BALLOON → WATER → MARSHMALLOW → COOKIE → …` (T05 owns the injection logic).

### `performance_state`
`QUEUED` → `ANNOUNCED` → `CONFIRMING` → `RUNNING` → `LOCKED` (or `SKIPPED`).
(Detailed transitions and timers in T04.)

### `scoring_kind`
Derived from `performance_type`, used by scoring (T05):
- `SUBJECTIVE` — Karaoke, Magic, Dance, Acting/Mimic, Standup, Reverse Mimic
  (85/15 judge/audience split).
- `TRIVIA` — correct + speed − penalty.
- `DETERMINISTIC` — Physical Challenges (winner-takes-points).

### `performer_slot_state`
`PENDING` (announced, not yet confirmed) → `CONFIRMED` | `REPLACED` | `VACATED`.
- `REPLACED` — original performer swapped out (by volunteer or break reassignment);
  the row retains the original player for the **strikethrough** display.
- `VACATED` — slot emptied and not refilled (e.g. performance skipped).

### `judge_role_source`
Why a player is a judge: `ASSIGNED` (normal 3-judge assignment) or `AUTHOR`
(Acting/Mimic, Trivia, Reverse Mimic make the author a judge).

---

## 2. Tables

### 2.1 `game`
The root aggregate.

| Column | Type | Notes |
|--------|------|-------|
| `id` | `uuid` PK | internal ID; **not** shown to players or typed by anyone |
| `join_code` | `char(6)` UNIQUE | the human-facing join code (see §2.1a); stored **without** the display space, uppercase, e.g. `A7K2M9` |
| `state` | `game_state` | default `CREATED` |
| `host_player_id` | `uuid` FK → `player.id` NULLable | set after the host player is created |
| `created_at` | `timestamptz` | |
| `started_at` | `timestamptz` NULL | |
| `ended_at` | `timestamptz` NULL | |

- The **UUID `id` stays the internal primary key** and is used in URLs/topics
  (T00 §5). The **`join_code` is a separate, indexed lookup column** so the
  human-facing code is fully decoupled from the internal ID and from any future
  identity/URL scheme.
- `host_player_id` is nullable only during the brief creation bootstrap; once the
  host player exists it is set and never changed.

### 2.1a Join code format

Players join by typing a short code shown on the TV, so it must be quick and
unambiguous to read from across a room.

**Format — fixed mask `A1A 1A1`:**
- Six characters in a strict **letter–digit–letter digit–letter–digit** pattern
  (positions 1/3/5 letters, 2/4/6 digits).
- Displayed with a single space grouping the two triples: **`A7K 2M9`**.
- Stored normalized (no space, uppercase): `A7K2M9`.

**Alphabet (Crockford-style, ambiguity removed at generation):**
- **Letters:** `A–Z` **excluding `I`, `L`, `O`, `U`** → 22 letters.
  - `I`/`L` excluded (look like `1`), `O` excluded (looks like `0`), `U` excluded to
    avoid accidental profanity.
- **Digits:** `2–9` (excluding `0` and `1`) → 8 digits.
- Because these glyphs never appear in any code, there is **no ambiguity to resolve**
  on input — no look-alike mapping is needed. A code can only ever contain
  unambiguous characters.

**Generation:**
- Codes are **randomly generated** (never sequential — sequential codes are guessable
  and enumerable).
- Insert relies on the `UNIQUE` constraint; on collision, **retry** with a new random
  code.
- **[DECISION]** avoid immediately reusing a code from a game in `OVER` state, so a
  newcomer doesn't accidentally land in a just-finished game. Simplest approach: keep
  `OVER` games (and their codes) around; a fresh random code will almost never
  collide.

**Validation split (frontend vs. backend):**
- **Frontend (strict):** the join input **rejects invalid characters as they are
  typed** and shows a **red error state** — it only accepts the 22 letters / 8 digits
  in the correct positions of the `A1A 1A1` mask. This is where usability protection
  lives (T06).
- **Backend (simple, fail-gracefully):** the backend does **not** re-validate the
  mask for now. It simply normalizes input (strip space, uppercase) and looks up
  `join_code`. A miss returns a plain **"game not found"** — no special error
  handling, no checksum. This keeps the already-complex backend lean; stricter
  server-side validation can be added later if needed.

**No checksum** is used (a wrong code simply fails the lookup; the cost is trivial for
a party game).

**Case-insensitivity:** input is uppercased before lookup; the stored code is
uppercase and the `UNIQUE` index is effectively case-insensitive by normalization.

### 2.2 `player`
A participant, scoped to one game. **Identity is disposable/per-game** (T00 §3): the
domain does **not** couple Player to the join token — the token lives in a separate
session table so OAuth2 can replace it later without touching `player`.

| Column | Type | Notes |
|--------|------|-------|
| `id` | `uuid` PK | |
| `game_id` | `uuid` FK → `game.id` | |
| `display_name` | `text` | |
| `picture_url` | `text` NULL | uploaded picture reference (storage in T07) |
| `score` | `integer` | cumulative, default 0 — **player-facing** |
| `performance_participation_count` | `integer` | default 0 — **backend-only**, feeds assignment (never exposed via player-facing APIs; see T02) |
| `on_break_until` | `timestamptz` NULL | non-null and in the future ⇒ on break |
| `is_host` | `boolean` | convenience flag; the authoritative host is `game.host_player_id` |
| `joined_at` | `timestamptz` | late joiners start with score 0 / count 0 |

Indexes: `(game_id)`, `(game_id, score DESC)` for the ranking.

### 2.3 `player_session`
Interim disposable identity — the **only** place the join token lives.

| Column | Type | Notes |
|--------|------|-------|
| `id` | `uuid` PK | |
| `player_id` | `uuid` FK → `player.id` UNIQUE | |
| `game_id` | `uuid` FK → `game.id` | denormalized for fast scoping checks |
| `token_hash` | `text` UNIQUE | store a **hash** of the opaque token, never the raw token |
| `created_at` | `timestamptz` | |

> OAuth2 seam: when Google OAuth2 arrives, a parallel `identity`/`account` table maps
> a Google subject to players; `player_session` (or its successor) resolves either
> mechanism to a `player_id`. Domain logic depends only on `player_id`.

### 2.4 `performance`
A single scored act. **PK is a per-game auto-increment integer** (Business §2).

| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint` PK | per-game sequence (see note below) |
| `game_id` | `uuid` FK → `game.id` | |
| `type` | `performance_type` | |
| `physical_kind` | `physical_challenge_kind` NULL | set iff `type=PHYSICAL_CHALLENGE` |
| `author_player_id` | `uuid` FK → `player.id` NULL | null for auto-queued physical challenges |
| `state` | `performance_state` | default `QUEUED` |
| `queue_position` | `integer` | ordering within the game's queue |
| `youtube_url` | `text` NULL | karaoke/dance |
| `reference_video_url` | `text` NULL | acting/mimic (played after) |
| `performance_score` | `numeric` NULL | finalized at LOCKED (feeds player scores) |
| `announced_at` / `confirm_deadline_at` / `started_at` / `locked_at` | `timestamptz` NULL | engine timers (T04) |

Per-game auto-increment: the Business rule is an integer that increments **within a
game**. Implementation options (final choice in build):
- **[DECISION]** simplest: a global `bigint` identity PK for storage + a separate
  `game_local_number integer` computed per game (unique within `game_id`) shown to
  users. This avoids per-game sequences while satisfying "integer auto-increment
  within the game." The docs below assume this: storage PK `id bigint`, plus
  `game_local_number` UNIQUE per `(game_id)`.

Add column:
| `game_local_number` | `integer` | UNIQUE `(game_id, game_local_number)`; the user-visible performance number |

Indexes: `(game_id, queue_position)`, `(game_id, state)`.

### 2.5 `performer_slot`
One row per performer position (1–4 per performance). Captures pre-determined vs.
random, confirmation, and **reassignment history** for the strikethrough display.

| Column | Type | Notes |
|--------|------|-------|
| `id` | `uuid` PK | |
| `game_id` | `uuid` FK | |
| `performance_id` | `bigint` FK → `performance.id` | |
| `slot_index` | `integer` | 0..3, ordering of slots |
| `origin` | `text` | `PREDETERMINED` or `RANDOM` |
| `original_player_id` | `uuid` FK → `player.id` NULL | who was first assigned (for strikethrough) |
| `current_player_id` | `uuid` FK → `player.id` NULL | who actually performs (null if vacated) |
| `state` | `performer_slot_state` | PENDING/CONFIRMED/REPLACED/VACATED |
| `confirmed_at` | `timestamptz` NULL | |

- **Strikethrough display** (Business 02/05): when `state=REPLACED`, the TV shows
  `original_player_id` struck through with `current_player_id` beneath. Both columns
  are retained deliberately.
- Constraints: UNIQUE `(performance_id, slot_index)`. A player may not occupy two
  slots of the same performance (enforced in service + partial unique index on
  `(performance_id, current_player_id)` where `current_player_id` not null).

### 2.6 `judge_assignment`
One row per judge on a performance (typically 3; author-as-judge types include the
author).

| Column | Type | Notes |
|--------|------|-------|
| `id` | `uuid` PK | |
| `game_id` | `uuid` FK | |
| `performance_id` | `bigint` FK | |
| `judge_player_id` | `uuid` FK → `player.id` | |
| `source` | `judge_role_source` | ASSIGNED or AUTHOR |
| UNIQUE | | `(performance_id, judge_player_id)` |

### 2.7 `evaluation`
A judge's structured evaluation. **One per judge per performance.**

| Column | Type | Notes |
|--------|------|-------|
| `id` | `uuid` PK | |
| `game_id` | `uuid` FK | |
| `performance_id` | `bigint` FK | |
| `judge_player_id` | `uuid` FK → `player.id` | |
| `submitted_at` | `timestamptz` NULL | null while in progress |
| UNIQUE | | `(performance_id, judge_player_id)` |

### 2.8 `evaluation_score`
The whole-then-individual detail (Business 04): a baseline plus per-performer
adjustments. Modeled as per-performer rows so individual scores are queryable.

| Column | Type | Notes |
|--------|------|-------|
| `id` | `uuid` PK | |
| `evaluation_id` | `uuid` FK → `evaluation.id` | |
| `subject_player_id` | `uuid` FK → `player.id` NULL | the evaluated performer; NULL row = the whole-group baseline |
| `criterion` | `text` | criterion key (per-type criteria catalogued in T05) |
| `value` | `numeric` | |
| UNIQUE | | `(evaluation_id, subject_player_id, criterion)` |

> The "evaluate whole first, then drill down per performer" flow (Business 04) is
> captured by a baseline row (`subject_player_id IS NULL`) plus optional per-performer
> override rows. Composition into the performance score is T05's concern.

### 2.9 `rating`
An audience member's single rating. **Composite PK = (performance_id, player_id)** —
this is the Business-mandated key that guarantees one rating per audience player.

| Column | Type | Notes |
|--------|------|-------|
| `performance_id` | `bigint` FK | PK part |
| `player_id` | `uuid` FK → `player.id` | PK part |
| `game_id` | `uuid` FK | |
| `total_score` | `numeric` | **computed** from sub-scores (T05); stored for locking |
| `locked` | `boolean` | default false; set true when performance LOCKED |
| `created_at` / `updated_at` | `timestamptz` | editable until locked |
| PK | | `(performance_id, player_id)` |

### 2.10 `rating_score`
The individual sub-scores behind a rating's `total_score` (composition varies by
type; T05).

| Column | Type | Notes |
|--------|------|-------|
| `performance_id` | `bigint` | FK part |
| `player_id` | `uuid` | FK part |
| `criterion` | `text` | |
| `value` | `numeric` | |
| PK | | `(performance_id, player_id, criterion)` |
| FK | | `(performance_id, player_id)` → `rating` |

### 2.11 `comment`
A live message on a performance.

| Column | Type | Notes |
|--------|------|-------|
| `id` | `uuid` PK | |
| `game_id` | `uuid` FK | |
| `performance_id` | `bigint` FK | |
| `author_player_id` | `uuid` FK → `player.id` | |
| `body` | `text` | |
| `created_at` | `timestamptz` | |
| `like_count` | `integer` | denormalized cache of `comment_like` rows (default 0) |

Index: `(performance_id, created_at)` for feed ordering.

### 2.12 `comment_like`
One like per player per comment.

| Column | Type | Notes |
|--------|------|-------|
| `comment_id` | `uuid` FK → `comment.id` | PK part |
| `player_id` | `uuid` FK → `player.id` | PK part |
| `game_id` | `uuid` FK | |
| `created_at` | `timestamptz` | |
| PK | | `(comment_id, player_id)` |

- One like per player per comment is enforced by the PK. `comment.like_count` is
  maintained transactionally (and drives the author's like points, T05).

### 2.13 `player_skip`
A player's opt-out of a specific performance's random assignment.

| Column | Type | Notes |
|--------|------|-------|
| `performance_id` | `bigint` FK | PK part |
| `player_id` | `uuid` FK → `player.id` | PK part |
| `game_id` | `uuid` FK | |
| `created_at` | `timestamptz` | |
| PK | | `(performance_id, player_id)` |

- Assignment (T05) excludes players with a matching `player_skip` row.

### 2.14 Trivia content: `trivia_question`
Author submits 10 question+answer pairs (Business 03 update). Answers are visible to
judges only during play (enforced at the API/real-time layer, T02/T03).

| Column | Type | Notes |
|--------|------|-------|
| `id` | `uuid` PK | |
| `performance_id` | `bigint` FK | |
| `game_id` | `uuid` FK | |
| `ordinal` | `integer` | 1..10 |
| `question` | `text` | |
| `answer` | `text` | author-provided correct answer |
| UNIQUE | | `(performance_id, ordinal)` |

> Trivia per-contestant answer capture and speed timing (for scoring) are modeled
> alongside T05's scoring; the minimal content table lives here.

### 2.15 `pending_tv_session`
A pre-minted display token waiting to be claimed by a game. Created when a TV calls
`POST /api/tv/register` before any game exists. Deleted atomically when `POST
/api/games` adopts the code.

| Column | Type | Notes |
|--------|------|-------|
| `join_code` | `char(6)` PK | same format as `game.join_code`; uniqueness guaranteed against active games at generation time |
| `display_token_hash` | `text` UNIQUE | hashed display token returned to the TV at register time |
| `created_at` | `timestamptz` | used to expire stale pending sessions (TTL ~30 min, enforced by a cleanup job or on-read check) |

- The join code is generated by the backend (same `JoinCodeGenerator` as games) so
  there are never collisions with active game codes.
- On `POST /api/games`: if the request carries a `tvCode` that matches a
  `pending_tv_session`, the backend **adopts** that code as the game's `join_code`,
  moves the display token to `game_display_token`, and deletes the pending row — all
  in the same transaction. If no `tvCode` is provided, normal random code generation
  applies and a new display token is minted as before.
- A stale pending row (TV opened but no game ever created) is harmless — the code
  simply stays reserved until cleaned up.

---

## 3. JPA / Hibernate mapping guidance

- Entities map 1:1 to the tables above. Aggregates: **Game** is the root; Performance
  is a sub-aggregate. Cross-aggregate references use IDs, not deep object graphs,
  to keep transactions small and avoid loading whole games.
- Enums: `@Enumerated(EnumType.STRING)`.
- Composite keys (`rating`, `rating_score`, `comment_like`, `player_skip`): use
  `@EmbeddedId` or `@IdClass`. **[DECISION]** prefer `@EmbeddedId` with a small
  `@Embeddable` key class for clarity.
- Fetch strategy: `LAZY` for all associations; load collections explicitly per
  use-case (T02/T04) rather than eager graphs.
- Optimistic locking: add `@Version` (`version bigint`) to `player`, `performance`,
  `rating`, and `comment` to guard concurrent updates (score changes, rating edits,
  like counts, state transitions). Concurrency strategy detailed in T07.
- The backend-only `performance_participation_count` and `player_session` are
  **never** serialized into player-facing DTOs (T02 defines DTOs that omit them).

---

## 4. Invariant enforcement matrix

How each Business invariant (07 §Invariants) is guaranteed:

| Invariant | Enforcement |
|-----------|-------------|
| One rating per audience player per performance | `rating` composite PK `(performance_id, player_id)` |
| Judges give **zero** ratings | Service rule: a player with a `judge_assignment` for a performance cannot create a `rating`; belt-and-suspenders check in the rating command (T02/T04) |
| Exactly one evaluation per judge per performance | `evaluation` UNIQUE `(performance_id, judge_player_id)` + judge must have a `judge_assignment` |
| Every player/performance belongs to one game | `game_id` FK on every table; all APIs scope by game (T00 §5) |
| On-break player never performer/judge | Assignment (T05) filters `on_break_until > now()`; break action reassigns existing `performer_slot`s and forbids new judge rows |
| Skipped player not randomly assigned | Assignment excludes players in `player_skip` for that performance |
| One like per player per comment | `comment_like` composite PK |
| Ratings frozen at lock-in | `rating.locked` set true on performance `LOCKED`; rating edits rejected when locked |
| Participation count is backend-only | Column exists but is excluded from all player-facing DTOs; only assignment reads it |
| Performer count 1–4 & per-type rules | Validated at submission/assignment (T02/T05); `performer_slot.slot_index` bounded 0..3 |

---

## 5. Flyway migrations

- Location: `src/main/resources/db/migration`, versioned `V1__...sql`,
  `V2__...sql`, …
- Initial migration set (proposed):
  - `V1__core_game_player.sql` — `game`, `player`, `player_session`.
  - `V2__performance_and_slots.sql` — `performance`, `performer_slot`,
    `judge_assignment`.
  - `V3__evaluation_rating.sql` — `evaluation`, `evaluation_score`, `rating`,
    `rating_score`.
  - `V4__comments_likes.sql` — `comment`, `comment_like`.
  - `V10__pending_tv_session.sql` — `pending_tv_session`.
- Enums are created as Postgres `CREATE TYPE ... AS ENUM` (or text + `CHECK`
  constraint — **[DECISION]** default to native `ENUM` types for readability;
  revisit if enum churn becomes painful, since altering PG enums is awkward).
- `ddl-auto=validate` ensures the JPA model and the Flyway-built schema stay in sync;
  a mismatch fails startup.

---

## 6. Entity–table map (quick reference)

| Concept (Business) | Table(s) |
|--------------------|----------|
| TV pairing | `pending_tv_session` |
| Game | `game` |
| Player | `player` (+ `player_session` for interim identity) |
| Performance | `performance` (+ type-specific `trivia_question`, URLs on `performance`) |
| Performer (role) | `performer_slot` |
| Judge (role) | `judge_assignment` |
| Evaluation | `evaluation` + `evaluation_score` |
| Rating | `rating` + `rating_score` |
| Comment | `comment` |
| Like | `comment_like` |
| Queue | `performance.queue_position` (+ ordering) |
| Skip | `player_skip` |
| Break | `player.on_break_until` |

---

## 7. Open items for later docs
- Exact **criteria keys** per performance type (the `criterion` values in
  `evaluation_score` / `rating_score`) → **T05**.
- Trivia per-answer capture + speed timing for scoring → **T05**.
- Media/picture **storage backend** and URL scheme → **T07**.
- Concurrency/locking strategy specifics (`@Version` usage, serializable sections)
  → **T07**.
- Whether performance numbering uses a true per-game sequence vs. the
  `game_local_number` approach in §2.4 → confirm at build time.
