# T06 — Frontend Architecture

This document specifies the two Angular frontends — the **TV** (display-only) and the
**Phone** (per-player controller) — and the shared workspace that keeps their
contracts identical. It consumes the REST commands (T02) and STOMP events (T03) and
renders the server-authoritative state (T04); it computes **no** authoritative game
logic, only cosmetic interpolation (countdown animation, scoreboard paging).

Core principle (T00 §1): **the backend is the source of truth.** The frontends render
what they receive and issue commands; they never decide timers, scores, or
transitions.

---

## 1. Workspace layout

A single Angular workspace with two applications and shared libraries (T00 §9):

```
karaogui-frontend/
  apps/
    tv/            # display-only, shared big screen
    phone/         # per-player controller
  libs/
    contracts/     # DTO + event type definitions (mirror T01/T02/T03)
    realtime/      # STOMP client wrapper, subscription manager, seq-gap handling
    api/           # typed REST client (one method per T02 endpoint)
    ui/            # shared presentational components (scoreboard, avatars, countdown)
```

- **`contracts`** is the single source of shape truth on the client: TypeScript
  interfaces for every DTO (T02) and every event envelope + `data` payload (T03). Both
  apps import from here so a contract change breaks both at compile time.
- **`realtime`** owns the STOMP connection, the snapshot+stream reconciliation, and the
  `seq`-gap → re-snapshot logic (T03 §3/§7). Apps subscribe to typed streams, not raw
  frames.
- **`api`** is a thin typed wrapper over the REST endpoints; it attaches the
  `Authorization: Bearer <token>` header (session token on phone, display token on TV).
- **[DECISION]** Angular CLI workspace (not Nx) — two apps + libs is well within CLI's
  multi-project support and avoids an extra tool. Revisit if the build graph grows.

---

## 2. Shared connection model

Both apps follow the **snapshot + stream** pattern (T00 §4, T03 §3):

1. On load, acquire a token (phone: from join/create; TV: from the QR/link, §5.3).
2. `GET /api/games/{gameId}` for the full snapshot as visible to this surface/role.
3. Open the STOMP connection (`surface` CONNECT header = `PHONE`|`TV`) and subscribe to
   the surface's topics.
4. Apply events after the snapshot, tracking `seq`; **on a gap, re-fetch the snapshot**
   rather than patching (T03 §3).

The `realtime` lib exposes an observable store (Angular signals) that merges snapshot +
events into a single reactive game state the components render from.

---

## 3. TV app (display-only)

Connects with the **read-only display token** (T03 §1). Subscribes to public/TV
topics: `/state`, `/ranking`, `/performers`, `/comments` (TV sees **all** comments),
`/players`. Takes **no** user input and can invoke no command (the display token is
rejected by command destinations).

### 3.1 Screen regions

- **Now performing / announcement** — the "who will perform" box on `PERFORMANCE_ANNOUNCED`;
  the confirmation countdown on `PERFORMANCE_CONFIRMING`; the running state on
  `PERFORMANCE_STARTED`.
- **Performer list with strikethrough** — driven by `/performers` (T03 §5.2). On
  `SLOT_REPLACED`, render `originalPlayerId` **struck through** with `currentPlayerId`
  beneath (Business 02/05).
- **Paginated scoreboard** — §4.
- **Comment feed** — live `COMMENT_POSTED` / `COMMENT_LIKE_CHANGED`; the TV shows the
  full feed (never suppressed).
- **Media** — YouTube embed for karaoke/dance; the acting/mimic `referenceVideoUrl`
  plays **after** (Business 03).
- **Join affordance** — the `joinCodeDisplay` (`A7K 2M9`) shown prominently so new
  players can join at any time.

### 3.2 Countdown rendering

The TV renders the confirmation countdown from `confirmDeadlineAt` (T03 §5.1) — pure
client interpolation between the server-given deadline and `now()`. It never decides
expiry; the `PERFORMANCE_SKIPPED`/`PERFORMANCE_STARTED` event is authoritative (T04).

---

## 4. Paginated scoreboard (shared `ui` component)

Implements Business 04 / T00 §6. Consumes `RANKING_UPDATED` (T03 §5.3).

- Shows at most `MAX_DISPLAYED_PLAYER` (**5**) entries per page in rank order.
- If `totalPlayers > MAX_DISPLAYED_PLAYER`, split into pages (page 1 = ranks 1–5,
  page 2 = 6–10, …).
- **Auto-cycle:** each page displays for `SCOREBOARD_PAGE_INTERVAL` (~**5s**), then the
  next replaces it in place; after the last page, **loop to the first**.
- Paging is **client-side cosmetic** (a timer/index over the received list); a new
  `RANKING_UPDATED` replaces the data and the cycle continues from a stable index.
- **Only score is shown** — never `performance_participation_count` (it is not even in
  the event payload, T03 §5.3).
- The endpoint `GET /ranking?page=` exists for reloads/late joins (T02 §4.2), but
  steady-state paging is driven by the pushed full list.

The same component runs on the phone (a compact variant), so the paging rule lives in
one place.

---

## 5. Phone app (per-player controller)

Connects with the player **session token** (`surface=PHONE`). Role-aware: what it
shows and allows depends on the player's **role for the current performance**
(`YOUR_ROLE`, T03 §5.6), which can change every performance.

### 5.1 Role-aware views

| Current role | Phone shows |
|--------------|-------------|
| **Audience** | comment feed + post box, like buttons, **rating** control for the current performance, scoreboard |
| **Performer** | your confirmation UI (`YOU_MUST_CONFIRM` + 30s countdown), then a performing view; comments may be de-emphasized (a UI choice) |
| **Judge** | evaluation UI (whole-then-individual), trivia answers when applicable; **comment feed hidden** and `/comments` **not subscribed** (T03 §2/§6) |
| **Host** (any of the above +) | start-game control (in `CREATED`), the end-prompt "More / Over" resolver on `QUEUE_EMPTY_PROMPT` |

Role transitions re-evaluate the comment subscription (judges unsubscribe; non-judges
resubscribe) — enforced server-side too (T03 §8), the client just reflects it.

### 5.2 Command surfaces (→ T02 endpoints)

- Confirm ready → `POST …/confirm`; volunteer (after 15s, on `REPLACEMENT_OPEN`) →
  `POST …/volunteer`.
- Submit performance → `POST …/performances` (type-specific form; validation mirrors
  T02 §4.4 client-side, backend authoritative).
- **Queue karaoke (search-first)** — the queue form is a **video-library search** rather
  than a raw URL box. The player types into a debounced search bar → `GET /api/videos?q=`
  (6/page, pager via `hasMore`). Each result row shows the thumbnail plus `songTitle`,
  `artist`, `videoName` — **blank/null lines are omitted**. Selecting a row + performers
  and hitting **Queue** submits `POST …/performances` with `videoId`. The host also sees a
  🗑️ per row → `DELETE /api/videos/{id}` then re-search.
- **Import to library (sub-flow)** — an "Import video to Library" button opens a modal
  with a YouTube URL box plus optional song title / artist inputs, and a preview embed
  (URL→preview→confirm). Confirm → `POST /api/videos`; on success the list re-searches so
  the new video is immediately selectable. Import and queue are **distinct steps**: import
  adds to the shared library, then the user selects it and queues.
- Save/submit evaluation → `PUT …/evaluation` (`submit:false` autosave, `submit:true`
  finalize).
- Submit/change/withdraw rating → `PUT`/`DELETE …/rating`.
- Post comment / like / unlike → `POST …/comments`, `POST`/`DELETE …/comments/{id}/like`.
- Take/end break → `POST`/`DELETE …/players/me/break`; skip → `POST …/skip`.

Commands are optimistic-light: the button disables on send and the **STOMP event** (or
the command's `200`/`4xx`) is the confirmation, not local state. Idempotent commands
(confirm, like) are safe to retry.

### 5.3 Onboarding & tokens

- **Join:** enter `joinCode` in the `A1A 1A1` mask (§6) + display name + optional
  picture (`POST …/uploads` → `POST /join`), receive and store the `sessionToken`
  (localStorage, per-game).
- **Create (host):** `POST /api/games` → store `sessionToken`; also receive the
  `displayToken` and render the **"Open on TV" QR / short link** that carries the
  display token to the TV browser (T03 §1). **[DECISION]** the link encodes `gameId` +
  `displayToken`; the TV app reads them from the URL on load.
- **Reconnect:** the stored session token re-authenticates; the app re-snapshots then
  resubscribes (§2).

---

## 6. Join-code input (frontend-strict validation)

Per T01 §2.1a, usability protection lives on the frontend:

- Input enforces the fixed mask **`A1A 1A1`** (positions 1/3/5 letters, 2/4/6 digits).
- Accepts **only** the Crockford alphabet — 22 letters (A–Z minus I, L, O, U) and
  digits 2–9 — **in the correct positions**.
- **Invalid characters are rejected as typed** and the field shows a **red error
  state** (Business/T01 requirement).
- Input is uppercased; the display space is inserted automatically for readability.
- The backend does **not** re-validate the mask (fails gracefully with `404`, T02 §3),
  so this is the primary guardrail — but it is UX only, never a security boundary.

---

## 7. State management & rendering

- **Angular signals** for reactive state; the `realtime` store exposes signals derived
  from snapshot + merged events. Components are largely presentational.
- **No client-side authority:** timers render from server deadlines; scores/ranks come
  from events; role comes from `YOUR_ROLE`. The client never computes a score,
  transition, or eligibility.
- **Reconnection** is deterministic via re-snapshot (§2); components tolerate a brief
  snapshot swap without losing scroll/focus where practical.

---

## 8. Media handling

- **YouTube:** embedded player for karaoke/dance `youtubeUrl`; acting/mimic
  `referenceVideoUrl` embedded and played **after** the performance (Business 03).
- **Pictures:** `POST …/uploads` returns `{uploadId, url}`; the app references
  `uploadId` at join/create and renders `pictureUrl` thereafter. Size/type limits are
  a backend concern (T07); the client shows a friendly error on rejection.

---

## 9. Responsibilities summary

| Concern | TV | Phone |
|---------|:--:|:-----:|
| Token type | display (read-only) | session (player) |
| Subscribes `/comments` | ✅ (all) | ✅ audience/performer, ❌ judge |
| Issues commands | ❌ | ✅ (role-gated) |
| Paginated scoreboard | ✅ (primary) | ✅ (compact) |
| Strikethrough performer list | ✅ | ✅ (own performance context) |
| Countdown rendering | ✅ cosmetic | ✅ cosmetic |
| Media playback | ✅ | optional |

## 10. Open items for later docs
- Exact snapshot DTO field list shared with `contracts` → finalize with T02/T03 DTOs.
- Whether the phone de-emphasizes vs. hides comments while performing → UX polish.
- QR/link format specifics (deep-link scheme, expiry of display token) → T07.
- STOMP heartbeat / reconnect backoff tuning → T07.
