# T02 — REST API Contract

This document defines the REST surface: conventions, authentication, the error model,
and every endpoint grouped by concern, with request/response shapes and validation.
It builds on T00 (transport, identity, scoping) and T01 (domain model). Live/push
concerns are **not** here — they are in T03; REST covers **commands** and
**snapshot reads**.

Guiding rules:
- **Commands over REST, live updates over STOMP.** A successful command returns the
  updated resource, but the authoritative broadcast of consequences (ranking change,
  new comment, timer) goes out over STOMP (T03).
- **DTOs never expose backend-only fields** (T01): a player's
  `performance_participation_count`, session `token_hash`, and trivia `answer` (except
  to judges) are never serialized to non-authorized clients.
- **Everything is game-scoped** under `/api/games/{gameId}/...` where `{gameId}` is
  the internal UUID (T00 §5). The only non-scoped endpoints are game creation,
  join-by-code, and TV registration.

---

## 1. Conventions

- **Base path:** `/api`.
- **Content type:** `application/json` for all bodies except picture upload
  (`multipart/form-data`).
- **IDs in paths:** `{gameId}` = game UUID; `{performanceId}` = the performance's
  storage id; `{commentId}` etc. are UUIDs.
- **Timestamps:** ISO-8601 UTC (`2026-07-17T20:10:00Z`).
- **Idempotency:** create/join use server-generated IDs; confirm/volunteer/like are
  naturally idempotent (repeating a like or a confirm is a no-op, not an error).
- **Versioning:** unversioned for now; if needed later, prefix `/api/v1`.

## 2. Authentication & authorization

- Identity is the **interim disposable per-game session token** (T00 §3). After a
  successful **join** (or **create**), the response includes a `sessionToken`.
- Clients send it on every subsequent request as
  `Authorization: Bearer <sessionToken>`.
- The backend resolves the token → `player_id` + `game_id` (via `player_session`,
  T01 §2.3) and enforces:
  - the token's `game_id` matches the `{gameId}` in the path (else `403`);
  - **role checks** for role-gated actions (e.g. only a judge with a
    `judge_assignment` may submit an evaluation; only the host may start the game).
- **Join code** is public and only used to *find* a game to join; it never appears in
  `Authorization`.
- **Display token** (T03 §1): a separate read-only credential returned at create time
  for the TV surface. It authenticates WebSocket subscription to public/TV-scoped
  topics only and **cannot invoke any REST command or message destination**; REST
  command endpoints reject it. Its sole purpose is to let the TV connect and observe.

## 3. Error model

Uniform error body:

```json
{
  "error": {
    "code": "PERFORMANCE_LOCKED",
    "message": "This performance is locked; ratings can no longer change.",
    "details": {}
  }
}
```

Status codes:
- `400` validation error (malformed body, out-of-range values).
- `401` missing/invalid session token.
- `403` token/role not authorized for this game or action.
- `404` resource not found — **including a join code that doesn't match any game**
  (the backend fails gracefully here per the join-code decision; no special-casing).
- `409` conflict / illegal state transition (e.g. starting an already-started game,
  rating a locked performance, joining a slot that's already confirmed).
- `422` semantically invalid command (e.g. performer rules for the type violated).

The frontend does its own strict input validation (e.g. join-code mask, red error
state — T06); the backend keeps validation lean and leans on these codes.

---

## 4. Endpoints

### 4.0 TV pairing

#### `POST /api/tv/register` — register a TV session *(no auth)*
Called by the TV frontend before any game exists. The backend generates a unique
6-char join code and a display token, stores them in `pending_tv_session` (T01
§2.15), and returns them to the TV.

Response `200`:
```json
{
  "joinCode": "A7K2M9",
  "joinCodeDisplay": "A7K 2M9",
  "displayToken": "opaque-display-token"
}
```

The TV shows `joinCodeDisplay` on screen and connects to STOMP using the
`displayToken`. Because no game exists yet, the STOMP session is in a **pending**
state (no `gameId` attached); the backend auth layer recognizes pending display
tokens and allows the connection but restricts subscriptions to the
`/user/queue/tv-ready` destination only (T03 §1).

When a host creates a game using this join code (§4.1), the backend pushes a
`TV_READY` event to the TV via `/user/queue/tv-ready` and the TV navigates into the
game lobby.

Pending sessions expire after 30 minutes with no game attached; the TV should re-call
this endpoint if the code expires.

### 4.1 Game lifecycle

#### `POST /api/games` — create a game
Creates a game in `CREATED` state and the **host player** in one step; returns the
host's session token.

Request:
```json
{
  "host": { "displayName": "Sam", "pictureUploadId": "b1f...", "?": "picture optional" },
  "tvCode": "A7K2M9"
}
```
`tvCode` is optional. If provided and matches a `pending_tv_session`:
- The pending row's join code is adopted as the game's `join_code`.
- The pending display token is moved to `game_display_token` and linked to the new game.
- The pending row is deleted — all in the same transaction.
- A `TV_READY` event is pushed via STOMP to the waiting TV (T03 §5.0).

If `tvCode` is omitted, a fresh random join code and display token are generated as
normal. If `tvCode` does not match any pending session, `404 TV_SESSION_NOT_FOUND` is
returned.

Response `201`:
```json
{
  "gameId": "e2c1...-uuid",
  "joinCode": "A7K2M9",
  "joinCodeDisplay": "A7K 2M9",
  "state": "CREATED",
  "you": { "playerId": "9a3...-uuid", "displayName": "Sam", "isHost": true },
  "sessionToken": "opaque-token",
  "displayToken": "opaque-display-token"
}
```
Notes: `joinCode` is normalized; `joinCodeDisplay` is the `A1A 1A1` grouped form for
the TV (T01 §2.1a). `sessionToken` authenticates the host player; `displayToken` is a
separate **read-only** credential for the TV surface (T03 §1) — returned for
completeness (e.g. if the host wants to open the TV link manually), but when `tvCode`
was provided the TV already has its token and this field is redundant. Picture
handling: either omit, or reference a prior upload (§4.7).

#### `POST /api/games/join` — join by code (public)
The only place a **join code** is accepted.

Request:
```json
{ "joinCode": "A7K2M9", "player": { "displayName": "Ana", "pictureUploadId": null } }
```
- Backend normalizes (strip space, uppercase) and looks up the code. **Miss ⇒ `404`**
  `GAME_NOT_FOUND` (graceful fail; no mask re-validation).
- Allowed while game is `CREATED` or `ACTIVE` (mid-game join supported). If `OVER`
  ⇒ `409 GAME_OVER`.

Response `201`: same shape as create's response minus host flag (unless they are the
host), including a fresh `sessionToken`. New players start `score=0` (participation
count is tracked internally, **not returned**).

#### `POST /api/games/{gameId}/start` — start the game *(host only)*
- `CREATED → ACTIVE`. `409` if not `CREATED`. `403` if caller isn't the host.
- Response `200`: the game summary with `state=ACTIVE`.

#### `POST /api/games/{gameId}/end-prompt/resolve` — continue or end *(host only)*
Answers the "more or over?" prompt raised when the queue empties (Business 02 §7).
```json
{ "decision": "MORE" }   // or "OVER"
```
- `MORE`: game stays `ACTIVE`, awaiting new submissions.
- `OVER`: `ACTIVE → OVER`; results become available (§4.9). `409` if not currently
  prompting.

#### `GET /api/games/{gameId}` — game snapshot
The **snapshot** half of the snapshot+stream pattern (T00 §4). Returns current game
state, the current performance (if any) with its performer slots and timers, the
ranking (first page), and recent comments as visible to the caller's role. Used on
connect/reconnect before subscribing (T03).

### 4.2 Players & ranking

#### `GET /api/games/{gameId}/players` — list players
Returns player summaries: `playerId`, `displayName`, `pictureUrl`, `score`,
`isHost`, `onBreak` (derived boolean). **Never** includes participation count.

#### `GET /api/games/{gameId}/players/me` — my player
The caller's own view; still omits the participation count (backend-only).

#### `GET /api/games/{gameId}/ranking?page=0` — ranking page
Paginated by `MAX_DISPLAYED_PLAYER` (default 5, T00 §6). The TV cycles pages client-
side (T06), but the endpoint supports paging for reloads/late joins.
```json
{ "page": 0, "pageSize": 5, "totalPlayers": 12,
  "entries": [ { "rank": 1, "playerId": "...", "displayName": "Ana", "score": 42 } ] }
```

### 4.3 Player state: break & skip

#### `POST /api/games/{gameId}/players/me/break` — take a break
Sets `on_break_until = now + BREAK_DURATION` (15 min). Re-activation resets the
window (Business 05). Triggers reassignment of any slots the player currently holds
(engine, T04). Response `200`: `{ "onBreakUntil": "..." }`.

#### `DELETE /api/games/{gameId}/players/me/break` — end break early
Clears `on_break_until`. Idempotent.

#### `POST /api/games/{gameId}/performances/{performanceId}/skip` — skip this one
Records a `player_skip` (T01 §2.13) so the caller isn't randomly assigned to this
performance. Idempotent. `409` if the performance already passed the assignment phase
(no effect).

### 4.4 Submitting performances

#### `POST /api/games/{gameId}/performances` — submit a performance
Body varies by `type`; common envelope:
```json
{
  "type": "KARAOKE",
  "performers": [
    { "kind": "PLAYER", "playerId": "..." },
    { "kind": "RANDOM" }
  ],
  "content": { "youtubeUrl": "https://youtu.be/..." }
}
```
Per-type validation (from Business 03; full rules in T05) enforced here, returning
`422` on violation. Highlights:
- **Karaoke / Dance:** 1–4 performers (PLAYER and/or RANDOM); `content.youtubeUrl`
  required; judges required.
- **Magic trick:** ≥1 PLAYER performer + 1–3 additional (PLAYER or RANDOM).
- **Acting/Mimic:** `content.referenceVideoUrl` required; author auto-added as a
  judge; author cannot also be a performer here only if type dictates (Acting allows
  performing) — see T05.
- **Standup:** 1–2 PLAYER performers; **RANDOM not allowed** ⇒ `422` if any RANDOM.
- **Trivia:** author cannot perform (author omitted from `performers`); exactly **4
  RANDOM**; `content.questions` = 10 `{question, answer}` pairs (answers stored,
  visible to judges only — §4.6/T03).
- **Reverse Mimic:** author cannot perform; `performers` = N RANDOM where N∈1..4.
- **Physical Challenge:** **cannot be submitted** via this endpoint ⇒ `422`
  `NOT_SUBMITTABLE` (engine injects them, T05).

Response `201`: the created performance with its `gameLocalNumber`, `queuePosition`,
`state=QUEUED`, and the resolved performer slots (RANDOM slots are filled at
announcement time by the engine, not here).

#### `GET /api/games/{gameId}/performances/{performanceId}` — performance detail
Role-filtered: trivia `answer`s included **only** if the caller is a judge on it.

### 4.5 Performer confirmation & volunteering

#### `POST /api/games/{gameId}/performances/{performanceId}/confirm` — I'm ready
Caller must hold a `performer_slot` on this performance in `CONFIRMING` state.
- Sets their slot `CONFIRMED`. Idempotent.
- `409` if not in the confirmation window or caller isn't a performer here.

#### `POST /api/games/{gameId}/performances/{performanceId}/volunteer` — replace an absentee
Available only **after `REPLACEMENT_OPEN_AFTER` (15s)** into the window, for
unconfirmed slots (Business 02 §c).
```json
{ "slotId": "..." }   // optional; if omitted, backend picks an eligible unconfirmed slot
```
- Atomically replaces the absent performer: sets `original_player_id` (kept for
  strikethrough), `current_player_id = caller`, `state=REPLACED/CONFIRMED`.
- `409` if the slot was already taken/confirmed (lost the race) or window not open.
- Eligibility: caller not on break, not already a performer/judge here, hasn't
  skipped.

### 4.6 Judging (evaluations)

#### `GET /api/games/{gameId}/performances/{performanceId}/evaluation/mine` — my working evaluation
Returns the judge's in-progress evaluation scaffold: the performer list, the criteria
for this type (T05), the whole-group baseline + any per-performer overrides, and —
because the caller is a judge — the trivia **answers** if applicable.

#### `PUT /api/games/{gameId}/performances/{performanceId}/evaluation` — save/submit evaluation *(judge only)*
Whole-then-individual model (Business 04 / T01 §2.8):
```json
{
  "baseline": [ { "criterion": "STAGE_PRESENCE", "value": 8 } ],
  "perPerformer": [
    { "playerId": "...", "criterion": "STAGE_PRESENCE", "value": 9 }
  ],
  "submit": true
}
```
- `submit:false` saves progress; `submit:true` finalizes (`evaluation.submitted_at`
  set). One evaluation per judge per performance (T01 UNIQUE).
- `403` if caller isn't a judge on this performance; `409` if already submitted or
  performance not in an evaluable state.
- When **all** judges have submitted and performers are done, the engine locks the
  performance (T04) — that lock is not a client call.

### 4.7 Rating (audience)

#### `PUT /api/games/{gameId}/performances/{performanceId}/rating` — submit/change my rating
```json
{ "scores": [ { "criterion": "OVERALL", "value": 7 } ] }
```
- Upserts the caller's `rating` (composite key player+performance, T01 §2.9);
  `total_score` recomputed server-side (T05). Freely changeable **until locked**.
- `403` if caller is a judge or performer on this performance (they don't rate).
- `409 PERFORMANCE_LOCKED` once the performance is `LOCKED`.
- Not rating is fine — there is simply no row; never blocks the flow.

#### `DELETE /api/games/{gameId}/performances/{performanceId}/rating` — withdraw my rating
Allowed until locked. Idempotent.

### 4.8 Comments & likes

#### `POST /api/games/{gameId}/performances/{performanceId}/comments` — post a comment
Body `{ "body": "🔥 great note!" }`. Any player may post. Returns the created comment.
The broadcast to TV/audience (and **suppression to judges' phones**, Business 06) is
handled by T03; judges may still post.

#### `GET /api/games/{gameId}/performances/{performanceId}/comments` — list comments
Role-filtered surface read for reconnect. **Judges receive an empty/omitted feed on
their phone context** (the same suppression as the live channel); TV context and
audience get the full feed. (The caller's *surface* is conveyed per T03; default
phone context applies here.)

#### `POST /api/games/{gameId}/comments/{commentId}/like` — like a comment
Idempotent; one like per player per comment (T01 `comment_like` PK). Increments
`like_count`; the tiny author point bonus (`LIKE_POINTS`) is applied per T05.
Response `200`: `{ "commentId": "...", "likeCount": 12, "likedByMe": true }`.

#### `DELETE /api/games/{gameId}/comments/{commentId}/like` — unlike
Idempotent. Removes the like and decrements the count (and reverses the point bonus,
T05).

### 4.9 Results

#### `GET /api/games/{gameId}/results` — final results *(available when OVER)*
Full ranking (all pages), performance history summary, and a shareable payload.
`409 GAME_NOT_OVER` if state isn't `OVER`.

### 4.10 Media upload (pictures)

#### `POST /api/games/{gameId}/uploads` — upload a picture
`multipart/form-data` with an image part. Returns `{ "uploadId": "...", "url": "..." }`
to reference from create/join/profile. Constraints (size/type/storage backend) are
defined in T07; this endpoint's contract is stable regardless.

---

## 5. Role-gating summary

| Action | Who may call |
|--------|--------------|
| Create game | anyone (becomes host) |
| Join by code | anyone (game not `OVER`) |
| Start game | host |
| Resolve end-prompt | host |
| Submit performance | any player (type rules apply) |
| Confirm ready | a performer of that performance |
| Volunteer | any eligible non-break, non-participant player |
| Submit evaluation | a judge assigned to that performance |
| Submit/change rating | audience for that performance (not judge/performer) |
| Post comment / like | any player |
| Take break / skip | any player (self) |

Role membership for a performance is derived from `performer_slot` /
`judge_assignment` (T01); the backend checks it on every gated call.

---

## 6. What REST intentionally does NOT do

- It does not push timers, countdowns, or state transitions — those are STOMP events
  (T03). A client learns "confirmation window closes at T" from the snapshot and the
  live channel, not by polling.
- It does not run assignment or scoring; those are engine/algorithm concerns
  (T04/T05) triggered by commands and timers.
- It does not expose backend-only data (participation count, token hashes, or trivia
  answers to non-judges).

---

## 7. Open items for later docs
- Exact per-type **criteria keys** in evaluation/rating bodies → **T05**.
- Precise **snapshot** payload shape and how surface context (TV vs phone) is
  signaled for comment filtering → **T03**.
- Upload **size/type limits** and storage → **T07**.
- Rate-limiting of comments/likes/join attempts → **T07**.
