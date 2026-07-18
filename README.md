# Original specs

Development Plan — KaraoGUI

  Guiding principle

  Build one vertical slice (Karaoke, all three surfaces, live) that proves the whole spine, then widen. Every phase ends in something you can run with one command and click through. We defer breadth (other 7 types, biased assignment, breaks, likes, challenge job) until
  the spine is confirmed.

  ---
  MVP scope (Phase 1–4): the "confirm direction" build

  In: create game → host QR to TV → join by code → submit Karaoke → engine announces → 30s confirm (+15s volunteer) → run → judges evaluate (whole-then-individual) + audience rate → lock → 85/15 score → ranking updates live on TV & phone. End-prompt → results.

  Explicitly out of MVP (stubbed or deferred): Magic/Dance/Acting/Standup/Trivia/Reverse Mimic/Physical types, biased random assignment (MVP uses pre-picked performers only, no RANDOM slots), break/reassignment, comment likes' point bonus, the challenge-injection job,
  picture upload (name-only), OAuth seam polish, rate-limiting.

  ---
  Phase 0 — Skeleton & one-command bring-up

  - Gradle Spring Boot 4.1 / Java 25 project; Angular workspace (tv, phone, libs contracts/realtime/api/ui).
  - docker-compose: Postgres + backend (+ Flyway on startup). Frontends via dev server initially, containerized at Phase 4.
  - Flyway V1–V5 schema exactly as T01. Health check + GET /api/games/{id} returning an empty snapshot.
  - Testable: docker compose up boots clean; migrations apply; snapshot endpoint responds.

  Phase 1 — Lobby (REST spine)

  - POST /games (create + host + sessionToken + displayToken), POST /games/join, POST /games/{id}/start, GET /games/{id} snapshot.
  - Session-token auth filter; game-scoping guard; error model (T02 §3).
  - Phone: create/join screens, join-code masked input (T06 §6). TV: shows join code + player list.
  - Testable: create on one phone, join on another, both appear on TV. No real-time yet (poll/refresh).

  Phase 2 — Real-time backbone

  - STOMP /ws, surfaces, snapshot+stream, seq, /state /ranking /performers /players topics; realtime lib with reconnect/re-snapshot.
  - TV auto-updates on join (PLAYER_JOINED) without refresh.
  - Testable: a join instantly appears on TV and all phones — proves the push spine.

  Phase 3 — Karaoke performance loop (the heart)

  - Submit Karaoke (pre-picked performers, YouTube URL); performance state machine QUEUED→ANNOUNCED→CONFIRMING→RUNNING→LOCKED + SKIPPED (T04).
  - Server timers (persisted deadlines + scheduler sweep): 30s confirm, 15s volunteer-open, skip-on-expiry.
  - Confirm / volunteer commands; judge evaluation (baseline + per-performer, save/submit); audience rating (upsert, lock on LOCKED).
  - Scoring: subjective 85/15 (T05 §3); ranking recompute + RANKING_UPDATED.
  - TV: announcement box, countdown, YouTube embed, running state, live scoreboard. Phone: role-aware views (performer confirm / judge eval / audience rate).
  - Testable: full Karaoke run start-to-finish; scores land; ranking moves live. This is the "confirm direction" moment.

  Phase 4 — Close the loop + polish MVP

  - End-prompt resolve (MORE/OVER), results screen, paginated cycling scoreboard (5/page, ~5s).
  - Comments feed + likes (post/like; judge phone suppression) — likes count but hold the point-bonus if you want to keep Phase 3 clean.
  - Containerize frontends; finalize one-command bring-up.
  - Testable: a complete mini-session you can demo end-to-end.

  ---
  Post-MVP (after you confirm direction) — widen in parallel

  Once the spine holds, these are largely independent workstreams good for parallel agents:
  - A) Biased random assignment (T05 §1) + RANDOM slots + break/reassignment/strikethrough.
  - B) Remaining subjective types (Magic, Dance, Acting/Mimic, Standup, Reverse Mimic) — mostly per-type validation + criteria.
  - C) Trivia (needs trivia_answer table, per-answer capture, speed scoring — T05 §4).
  - D) Physical Challenges + the @Scheduled injection job (T05 §7) + winner_player_id.
  - E) Engagement polish: like-points bonus, picture upload, T08 tuning wired to config, rate-limiting.

  ---
  How this maps to the README's "spin up several agents in parallel"

  The MVP (Phase 0–4) is built as a coherent spine — I'd keep that mostly sequential (or lightly parallel: backend spine ‖ frontend shell) because the contracts must settle first. Post-MVP A–E is where parallel agents shine, since they slot into a proven spine along
  stable T01/T02/T03 contracts. That parallelization detail is exactly what T07 was going to formalize.