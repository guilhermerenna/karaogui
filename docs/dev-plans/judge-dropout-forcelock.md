# Dev Plan — Judge-Dropout Force-Lock + YouTube Link Validation

**Status:** backend implemented; docs updated. This plan is the handoff for a follow-up
agent to verify, finish any frontend polish, and run the end-to-end test plan.

---

## 1. Problem being solved

A performance in `RUNNING` locks (`RUNNING → LOCKED`) only when **all judges submit**.
If a judge drops out (page refresh, phone dies, never loaded), condition (2) never
becomes true, the performance stalls in `RUNNING` forever, and the whole game freezes —
nobody can advance it.

**Fix (Option A):** every `RUNNING` performance carries a `judging_deadline_at`. A
periodic sweep force-locks it when the deadline passes, scoring with whatever judge
evaluations exist (missing judges contribute the neutral 5.0 default — already in the
scorer, so no NaN/zero). All-judges-submitted still locks *early*; the deadline is only
the safety net.

**Deadline basis:** the backend can't detect song-end, so it fetches the YouTube video
**duration** at queue time via the YouTube Data API v3. Deadline =
`started_at + songLength + JUDGING_GRACE` (90 s). When the duration is unknown, it uses
`YOUTUBE_FALLBACK_CEILING` (8 min) instead of `songLength`.

**Bonus:** the same queue-time lookup rejects links that can't play — a *definite*
bad-video verdict (no video id, video not found, not embeddable) returns `409`. A mere
*failure to reach the API* is soft and lets the submission through (so a quota wipe-out
never blocks all queueing).

---

## 2. What is already done (backend + docs)

### Config & secrets
- `application.yml` — `karaogui.youtube.{api-key,judging-grace,fallback-ceiling}`
  (api-key bound from `${YOUTUBE_API_KEY:}`, grace `90s`, ceiling `8m`).
- `KaraoguiProperties.java` — `Youtube(String apiKey, Duration judgingGrace, Duration fallbackCeiling)` record.
- `docker-compose.yml` — passes `YOUTUBE_API_KEY: ${YOUTUBE_API_KEY:-}` to the backend.
- Root `.env` (git-ignored via `.gitignore` line 27) holds the real key; `.env.example`
  documents the variable. `docker-compose` (v1 CLI on this machine) auto-loads `.env`.

### Schema
- `V11__performance_judging_deadline.sql` — adds `performance.duration_seconds bigint NULL`
  and `performance.judging_deadline_at timestamptz NULL`.
- `Performance.java` — matching `durationSeconds` / `judgingDeadlineAt` fields + accessors.

### Lookup client
- `YoutubeMetadataClient.java` — `fetch(url)` returns a `LookupResult` with `Outcome`
  `OK` / `REJECTED` / `UNAVAILABLE`. Rejects `NOT_A_YOUTUBE_VIDEO`, `VIDEO_NOT_FOUND`,
  `VIDEO_NOT_EMBEDDABLE`. Any exception or blank key ⇒ `UNAVAILABLE` (soft). Parses
  ISO-8601 `contentDetails.duration` → seconds.

### Engine
- `PerformanceService.queuePerformance` — calls `youtubeClient.fetch(...)`; if
  `isRejected()`, throws `GameStateException(reasonCode, friendlyMessage)` (→ `409`).
  Otherwise stores `durationSeconds` if present.
- `PerformanceService.startPerformance` — on `CONFIRMING → RUNNING` sets
  `judgingDeadlineAt = now + (durationSeconds ?: fallbackCeiling) + judgingGrace`.
- `PerformanceService.forceLockExpired(id)` — `@Transactional`; no-ops unless `RUNNING`,
  else runs the normal `lockPerformance` path (partial scores OK).
- `PerformanceRepository.findByStateAndJudgingDeadlineAtBefore(state, instant)`.
- `PerformanceTimerJob.tick()` (5 s `fixedDelay`) — now also sweeps `RUNNING`
  performances past `judgingDeadlineAt` and calls `forceLockExpired`.

### Docs updated
- **T04** §2.1 (two RUNNING→LOCKED rows), §2.2 (dropout safety-net paragraph), §3
  (judging-deadline bullet), §5 header.
- **T05** §3.2 (partial/absent judges → 5.0), §8 (V11 Flyway), new **§11** (lookup client
  + deadline sweep + config), §12 (renumbered open items).
- **T08** §1 (judgingGrace/fallbackCeiling rows + apiKey note), §6 cross-ref row.
- **T02** §4.4 (409 rejection code table + envelope example).

---

## 3. What the follow-up agent still needs to do

The frontend **already** surfaces the 409 message: `performance.ts` reads
`err.error?.error?.message` into `queueError()` and renders it under the queue form
(lines ~244/264/458-460). So the reject flow needs **no code change** — just verify it.

Remaining work is **verification only** (plus optional polish):

1. **Build + test the backend** — confirm nothing regressed.
2. **Run the E2E test plan** (§4) against a live stack.
3. *(Optional polish, only if time allows)* Show a live judging-deadline countdown on the
   TV during `RUNNING`, driven by `judgingDeadlineAt` from the snapshot. Not required for
   MVP — skip unless asked.

Do **not** re-architect the soft-dependency behavior: API failures must never block
queueing. Only definite bad-video verdicts reject.

---

## 4. Test plan

### 4.0 Preconditions
- Real `YOUTUBE_API_KEY` present in root `.env` (already placed). Without it, both link
  tests below become "UNAVAILABLE" (soft-pass), so the reject test would NOT reject —
  the key must be live for 4.3 to be meaningful.
- Follow **CLAUDE.md → "Starting services"** exactly:
  ```bash
  # repo root
  docker-compose down && docker-compose up --build -d   # --build is mandatory
  docker-compose ps                                      # backend must be "healthy"
  curl http://localhost:8080/actuator/health            # {"status":"UP"}
  # frontend
  pkill -f "ng serve" 2>/dev/null; sleep 1
  cd frontend
  npx ng serve --project phone --port 4201 --no-open &
  npx ng serve --project tv    --port 4200 --no-open &
  # wait for both "Application bundle generation complete"
  ```
- Phone http://localhost:4201 · TV http://localhost:4200 · Backend http://localhost:8080

### 4.1 Backend unit/integration tests
```bash
cd backend && ./gradlew test
```
Expect **BUILD SUCCESSFUL**. (Integration tests use a blank test key ⇒ lookups are
UNAVAILABLE ⇒ `https://youtu.be/test`-style links pass through — this is correct, not a
fluke.)

### 4.2 Player setup for a full run
- **3 players** minimum: open the phone app in 3 browser tabs/profiles (or 3 devices).
  - Player A = host (creates the game).
  - Players B and C join with the game code.
- This gives enough bodies for one performer + at least one judge, so a real
  `RUNNING → LOCKED` can happen and the force-lock path can be exercised.

### 4.3 Un-embeddable / bad link — expect **rejection**
- Test URL: `https://www.youtube.com/watch?v=3WLy3AblvmQ`
- As any player, open the queue form, paste the URL, submit.
- **Expected:** the queue is **rejected** — the phone shows the friendly message
  ("This YouTube link can't be used. Please check the video and try another.") under the
  form (from the `409` envelope). No performance is created.
- Backend log shows the lookup and the reject reason code
  (`VIDEO_NOT_EMBEDDABLE` or `VIDEO_NOT_FOUND` depending on the video's current status).

### 4.4 Working link — expect **successful queue + duration-driven deadline**
- Test URL: `https://www.youtube.com/watch?v=Hjx9TJQlBsM`
- Queue it, confirm the preview, submit.
- **Expected:** performance is created (`state=QUEUED`). In the DB (or backend log),
  `performance.duration_seconds` is populated with the real video length.
- Advance it: performers confirm → `RUNNING`. Verify `judging_deadline_at` ≈
  `started_at + duration_seconds + 90s`.
  ```bash
  docker-compose exec postgres psql -U karaogui -d karaogui \
    -c "select id, state, duration_seconds, started_at, judging_deadline_at from performance order by id desc limit 5;"
  ```

### 4.5 Judge-dropout force-lock — the core fix
- With the working performance in `RUNNING` and at least one judge assigned:
  **do NOT submit the judge evaluation** (simulate a dropout — close/refresh the judge's
  tab).
- Wait until `judging_deadline_at` passes (to test fast without waiting for a full song,
  temporarily set `karaogui.youtube.fallbackCeiling` low, e.g. `20s`, and use a link with
  unknown duration — or just wait out the real deadline).
- **Expected:** within one 5 s sweep after the deadline, the performance auto-locks
  (`PERFORMANCE_LOCKED` + `RANKING_UPDATED`), the scoreboard updates, and the game
  proceeds. The missing judge contributed the neutral 5.0 default — no stall, no error.

### 4.6 Happy path still locks early
- Repeat 4.4 but this time **do** submit all judge evaluations before the deadline.
- **Expected:** the performance locks immediately on the last submission (not at the
  deadline). The deadline sweep then no-ops for it.

### 4.7 Soft-fail sanity (optional)
- Temporarily blank `YOUTUBE_API_KEY` in `.env`, `docker-compose up --build -d` again.
- Queue the 4.3 "bad" link: it now **passes through** (UNAVAILABLE ⇒ soft), gets no
  duration, and its deadline uses the 8 min fallback ceiling. Confirms API failures never
  block queueing. Restore the key afterward.

---

## 5. Rollback / safety notes
- The force-lock is additive; if it misbehaves, disabling the second block in
  `PerformanceTimerJob.tick()` reverts to the old behavior (no RUNNING sweep).
- The exposed API key from earlier chat should be **rotated** in Google Cloud console
  before any public deployment; `.env` keeps it out of git but chat history retained it.
