# T03 — Real-time & State Synchronization

This document defines the live channel: the STOMP-over-WebSocket topology, the
snapshot+stream model, the full **event catalog**, how events are **filtered by role
and surface**, and reconnection. It builds on T00 (transport, scoping), T01 (domain),
and T02 (REST commands). REST issues commands; **this layer broadcasts their
consequences and drives timers**.

Core principle: **the backend is authoritative.** Clients render what they receive;
they never compute timers, scores, or transitions locally beyond cosmetic
interpolation (e.g. animating a countdown between server ticks).

---

## 1. Transport & connection

- **Protocol:** STOMP over WebSocket (T00 §2). Endpoint: `/ws`. **[DECISION]** raw
  WebSocket only for now — no SockJS fallback. The target environment is modern mobile
  browsers on home Wi-Fi, where raw WebSocket is universally available; the classic
  SockJS justification (corporate proxies, legacy IE) does not apply, and skipping it
  keeps the client simpler. SockJS is a cheap build-time toggle later (server
  `.withSockJS()` + swap the client socket factory) if real-device testing surfaces a
  connection failure.
- **Handshake auth:** the client presents its **session token** (T02 §2) as a STOMP
  `CONNECT` header (e.g. `Authorization: Bearer <token>`). The backend resolves it to
  `player_id` + `game_id` and attaches that to the WebSocket session. Anonymous
  connects are refused.
- **The TV surface** connects with a **dedicated read-only "display token"** issued
  either at game-create time (legacy flow) or at `POST /api/tv/register` (TV-first
  pairing flow, T02 §4.0). The token marks the WebSocket session `surface=TV`,
  read-only. In the **TV-first flow** the token is issued before any game exists; the
  STOMP session is in a **pending** state with no `gameId` and may only subscribe to
  `/user/queue/tv-ready`. Once the host creates a game with the matching TV code, the
  backend pushes `TV_READY` (§5.0) and the TV transitions to the normal connected
  state, subscribing to all game topics. In both flows the display token may subscribe
  to public/TV-scoped topics only and **cannot invoke any command destination** (§8).

## 2. Surfaces and subscription context

Every connection declares a **surface** so the backend can filter correctly:

- `PHONE` — a player's controller. Receives role-filtered content (notably: **no
  comment events when the player is currently a judge**).
- `TV` — the shared display. Receives the full public feed including all comments.

Surface is bound at connect time (from the token type + a `surface` CONNECT header).
The backend, not the client, enforces what each surface/role may receive (Business
06; T02 §4.8).

## 3. Snapshot + stream model

On connect or reconnect a client:

1. **Fetches a REST snapshot** — `GET /api/games/{gameId}` (T02 §4.1) — giving the
   full current state as visible to its role/surface.
2. **Subscribes** to the game's topics (below) for incremental updates.
3. Applies events **after** the snapshot; each event carries a monotonically
   increasing `seq` per game so the client can detect gaps.

If a gap is detected (missed `seq`), the client **re-fetches the snapshot** rather
than trying to patch — snapshots are cheap and make reconnection deterministic.

## 4. Topic topology

All topics are game-scoped (T00 §5). Destinations:

| Destination | Purpose | Surfaces |
|-------------|---------|----------|
| `/user/queue/tv-ready` | TV pairing: pushed once when a game adopts the TV's join code | pending TV only |
| `/topic/games/{gameId}/state` | game & performance state transitions, timers | TV + PHONE |
| `/topic/games/{gameId}/ranking` | ranking/score updates | TV + PHONE |
| `/topic/games/{gameId}/performers` | performer slot changes (confirm, replace, strikethrough) | TV + PHONE |
| `/topic/games/{gameId}/comments` | comment posted / like changed | **TV + audience PHONEs only** |
| `/topic/games/{gameId}/players` | join/leave, break status | TV + PHONE |
| `/user/queue/games/{gameId}/private` | per-player private messages (your role changed, your turn, your evaluation state) | that PHONE only |

- **Comment suppression for judges:** rather than filter a shared topic per message,
  judges' phones **do not subscribe** to `/comments` while they hold a judge role for
  the current performance; the backend also refuses/withholds `/comments` delivery to
  a judge-surface subscription (defense in depth). When their role changes (next
  performance), subscription is re-evaluated. The **TV always** subscribes to
  `/comments`.
- Private, targeted messages use STOMP **user destinations** (`/user/...`) so only the
  intended player receives them.

## 5. Event catalog

Every event envelope:
```json
{ "seq": 128, "type": "PERFORMANCE_CONFIRMING", "at": "2026-07-17T20:10:00Z", "data": { } }
```

### 5.0 TV pairing — `/user/queue/tv-ready`
- `TV_READY` — `{ gameId, joinCode, joinCodeDisplay }`. Pushed to a pending TV
  STOMP session the moment a host creates (or joins) a game using the TV's join code.
  The TV frontend receives this, stores the `gameId`, then:
  1. Fetches the game snapshot via `GET /api/games/by-code/{joinCode}` using its
     display token.
  2. Subscribes to all normal game topics.
  3. Renders the lobby.

  This destination is **only reachable while the display token has no gameId** (pending
  state). Once the game is linked, the TV transitions to the normal subscription model
  and this destination is no longer used.

### 5.1 State & timers — `/state`
- `GAME_STARTED` — game went `ACTIVE`.
- `PERFORMANCE_ANNOUNCED` — `{ performanceId, gameLocalNumber, type, performers[] }`;
  performance paused in the background, "who will perform" box (Business 02 §a).
- `PERFORMANCE_CONFIRMING` — `{ performanceId, confirmDeadlineAt, replacementOpensAt }`;
  starts the **30s** window; `replacementOpensAt = start + 15s`. Clients render the
  countdown from `confirmDeadlineAt` (server-authoritative).
- `REPLACEMENT_OPEN` — emitted at the 15s mark; volunteer button becomes active.
- `PERFORMANCE_STARTED` — all slots confirmed; performance is running.
- `PERFORMANCE_SKIPPED` — window expired without enough confirmations.
- `PERFORMANCE_LOCKED` — performers done + all judges submitted; ratings frozen,
  score finalized (triggers a `ranking` event too).
- `QUEUE_EMPTY_PROMPT` — queue drained; host is prompted "more or over?" (Business 02
  §7). Carries no queue contents.
- `GAME_ENDED` — game went `OVER`; results available.
- `NEXT_UP` *(optional)* — `{ performanceId, type }` to show "what's next" in a corner
  while current hasn't started (Business 02 §6). Intentionally minimal.

### 5.2 Performer slots — `/performers`
- `SLOT_CONFIRMED` — `{ performanceId, slotId, playerId }`.
- `SLOT_REPLACED` — `{ performanceId, slotId, originalPlayerId, currentPlayerId }`.
  Drives the **strikethrough** display: original struck through, replacement beneath
  (Business 02/05).
- `SLOT_VACATED` — a slot emptied (e.g. on skip).
- `RANDOM_SLOTS_FILLED` — `{ performanceId, slots:[{slotId, playerId}] }` — emitted
  when the engine fills RANDOM slots at announcement (assignment result; T05).

### 5.3 Ranking — `/ranking`
- `RANKING_UPDATED` — the full ordered list (or first page + total) after any score
  change. Clients page/cycle locally (`MAX_DISPLAYED_PLAYER`, ~5s, T06). Score is the
  only player-facing metric (participation count is never in these events).

### 5.4 Comments — `/comments` *(TV + audience phones only)*
- `COMMENT_POSTED` — `{ commentId, performanceId, authorPlayerId, authorName, body, createdAt, likeCount }`.
- `COMMENT_LIKE_CHANGED` — `{ commentId, likeCount }` (and the author's small point
  bonus surfaces via a `RANKING_UPDATED` when applicable, T05).
- **Not delivered to judge-surface subscriptions.**

### 5.5 Players — `/players`
- `PLAYER_JOINED` — `{ playerId, displayName, pictureUrl }` (late joiners appear
  live; score 0).
- `PLAYER_BREAK_CHANGED` — `{ playerId, onBreak, onBreakUntil }`.

### 5.6 Private — `/user/.../private`
- `YOUR_ROLE` — `{ performanceId, role: PERFORMER|JUDGE|AUDIENCE }` so the phone can
  switch its UI (and, for judges, re-evaluate comment subscription).
- `YOU_MUST_CONFIRM` — you are a performer; the 30s window is open.
- `YOU_WERE_REPLACED` — your unconfirmed slot was taken by a volunteer.
- `YOUR_EVALUATION_STATE` — save/submit acknowledgements for judges.
- `RATING_LOCKED` — your rating for the current performance is now frozen.

## 6. Role/surface delivery matrix

| Event group | TV | Audience phone | Judge phone | Performer phone |
|-------------|:--:|:--------------:|:-----------:|:---------------:|
| `/state` | ✅ | ✅ | ✅ | ✅ |
| `/performers` | ✅ | ✅ | ✅ | ✅ |
| `/ranking` | ✅ | ✅ | ✅ | ✅ |
| `/players` | ✅ | ✅ | ✅ | ✅ |
| `/comments` | ✅ | ✅ | ❌ | ✅* |
| `/user private` | — | own | own | own |

\* Performers receive comments (their phone may de-emphasize them while performing —
a UI choice, T06); only **judges** are hard-blocked from the comment stream on phone
(Business 06). Role is per **current performance** and can change between
performances.

## 7. Delivery, ordering, reliability

- **Ordering:** per-game monotonic `seq` stamped by the backend; clients apply in
  order and re-snapshot on gap.
- **At-least-once vs at-most-once:** events are best-effort broadcasts; because state
  is reconcilable via snapshot, occasional loss is tolerated (client re-snapshots on
  gap). Commands (T02) are the source of truth, not event receipt.
- **Timers are data, not messages:** the client is told the **deadline timestamp**
  (`confirmDeadlineAt`, `onBreakUntil`), not a stream of tick messages. The
  authoritative expiry is enforced by the engine (T04); the client only renders the
  countdown.
- **Backpressure/large feeds:** the comment topic can be busy; the backend may
  coalesce rapid `COMMENT_LIKE_CHANGED` for the same comment.

## 8. Security of subscriptions

- A connection may only subscribe to topics for **its** `gameId` (from the token);
  cross-game subscription attempts are rejected (T00 §5).
- Judge comment suppression is enforced **server-side** on subscription, not left to
  client honesty.
- The TV/display token is **read-only**: it may subscribe but cannot invoke any
  message-mapped command destinations.

## 9. Open items for later docs
- Exact **snapshot** payload schema (shared with T02 `GET /games/{id}`) — finalize
  field-by-field alongside DTOs.
- Whether to enable **SockJS** fallback and heartbeats tuning → build-time.
- Coalescing/rate policy for likes and comments → **T07**.
- Display-token issuance details (created with the game) → confirm in **T04/T07**.
