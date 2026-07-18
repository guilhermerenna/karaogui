# T07 — Non-Functional, Cross-Cutting & Parallelization Plan

This is the final core Technical document. It covers the concerns that span all of
T01–T06 — concurrency & consistency, the identity seam, media constraints,
observability, testing, security, configuration — and then lays out **how the build is
divided into an MVP spine followed by parallel workstreams** (the README's "spin up
several agents in parallel" step).

It assumes T00–T06 (mechanism) and T08 (tuning dial board) are settled.

---

## 1. Concurrency & consistency

The engine (T04 §7) performs state transitions in **single transactions** with
**optimistic locking** (`@Version` on `player`, `performance`, `rating`, `comment`,
T01 §3). This section pins the strategy the earlier docs deferred here.

### 1.1 Hot rows & contention points

| Contended row | Racing operations | Strategy |
|---------------|-------------------|----------|
| `performer_slot` | two volunteers for the same slot; break-reassign vs. volunteer | conditional update guarded on slot `state`+version; loser gets `409` (T02 §4.5), winner reflected via `SLOT_REPLACED` |
| `performance` | confirm→RUNNING vs. deadline→SKIPPED; lock transition | `@Version`; the transition that loses the race re-reads and no-ops if already terminal |
| `rating` / `rating_score` | audience edits vs. lock-in | upsert under the performance's state; once `LOCKED`, upserts rejected `409 PERFORMANCE_LOCKED` |
| `player.score` | concurrent score adds (lock-in, like bonus) | `@Version` retry; score deltas are additive so a retry re-applies cleanly |
| `comment.like_count` | concurrent likes | `comment_like` PK guarantees one row; count maintained in the same tx, `@Version` on `comment` |

### 1.2 Approach

- **[DECISION]** default to **optimistic locking + retry** (short, bounded retry loop
  on `OptimisticLockException`) rather than pessimistic `SELECT … FOR UPDATE`. Party-game
  contention is low-to-moderate and bursty; optimistic keeps transactions short and
  avoids lock-wait pileups.
- Use **short serializable-isolation sections** only where a read-modify-write must be
  atomic and cannot be expressed as a single conditional update — currently just the
  **slot claim** (volunteer/reassign). Everything else is a single UPDATE or an
  optimistic retry.
- Transitions are **idempotent on terminal state**: a scheduler sweep firing after a
  slot already confirmed simply finds `RUNNING` and no-ops (T04 §3).
- **Scheduler singleton:** the timer sweep and challenge-injection job must not
  double-fire across instances. MVP is single-instance so this is moot; multi-instance
  (post-MVP) uses a DB-backed lock (e.g. ShedLock) — **[DECISION]** deferred until we
  actually run more than one backend.

### 1.3 Event ordering vs. transactions

- The per-game `seq` (T03 §7) is assigned **inside** the transaction that produces the
  state change, and events are published **after commit** so subscribers never see an
  event for an uncommitted change. On rollback, no event is emitted.

---

## 2. Identity seam (interim → OAuth2)

Formalizes the T00 §3 / T01 §2.3 seam so OAuth2 lands later without domain churn.

- Everything downstream depends only on a resolved **`PlayerIdentity` → `player_id` +
  `game_id`**. The engine, scoring, and assignment never inspect *how* a caller
  authenticated.
- **Interim:** an `AuthenticationResolver` maps `Authorization: Bearer <token>` →
  `player_session.token_hash` → `player_id`/`game_id`. The **display token** resolves to
  a read-only TV principal bound to `game_id` with **no** `player_id` and no command
  authority (T02 §2, T03 §1).
- **Future:** a second resolver maps a Google subject → a durable identity → `player_id`.
  Adding it is a new resolver + tables; **no change** to controllers, engine, or schema
  of `player`.
- Tokens are opaque, unguessable, stored **hashed** (`token_hash`), never logged.

---

## 3. Media & uploads

Finalizes the contract T02 §4.10 / T06 §8 left open.

- **Endpoint:** `POST /api/games/{gameId}/uploads` (`multipart/form-data`) → `{uploadId, url}`.
- **Constraints:** `image/png|jpeg|webp`; max **5 MB**; reject others `400`. Dimensions
  capped by server-side downscale to a sane avatar size.
- **[DECISION] MVP storage:** local filesystem volume mounted into the backend container,
  served under `/media/**`. The `url` is relative to the backend. Swapping to object
  storage (S3/GCS) later changes only the storage adapter behind the `uploadId`→`url`
  mapping — **[DECISION]** keep an `UploadStore` interface so the backend is
  storage-agnostic.
- **YouTube / reference video:** stored as URLs on `performance` (T01 §2.4); no proxying,
  the frontends embed directly (T06 §8).
- **MVP simplification:** picture upload is **deferred** — players are name-only in the
  MVP (see §7). The endpoint/contract stays as specified for post-MVP.

---

## 4. Observability

- **Structured logging** (JSON) with a `gameId` MDC on every request/event so a whole
  game's activity is greppable. **Never** log tokens or `token_hash`.
- **Correlation:** each STOMP event and REST command carries/derives the `gameId`; the
  `seq` is logged on publish for gap diagnosis.
- **Metrics** (Micrometer, post-MVP wiring): active games, connected sessions per
  surface, performances by state, timer-expiry counts, optimistic-retry counts (a spike
  signals contention).
- **Health:** Spring Actuator `/health` (DB + Flyway) used by the compose healthcheck
  (T00 §7).
- **[DECISION]** metrics/dashboards are **post-MVP**; structured logging + health are in
  from Phase 0 because they cost little and aid debugging the spine.

---

## 5. Testing strategy

| Layer | What | Tooling |
|-------|------|---------|
| Domain/unit | scoring math (T05 §3–5), assignment weighting (T05 §1), state-machine guards (T04) | JUnit; **seeded RNG** so assignment is deterministic in tests |
| Persistence | schema ↔ JPA (`ddl-auto=validate`), composite keys, constraints | **Testcontainers Postgres** (real DB, per T00 — no H2, avoids dialect drift) |
| REST | endpoint contracts, auth/role gating, error codes | `@SpringBootTest` + MockMvc/WebTestClient |
| Real-time | subscribe → command → event; judge comment suppression; `seq` gap → re-snapshot | STOMP test client against an embedded server |
| Engine flow | full Karaoke loop incl. timers (with a **controllable clock**) | integration test driving the state machine end-to-end |
| Frontend | contracts types, scoreboard paging, join-code mask, role views | Jest/Karma + component tests; Playwright e2e for the core loop (post-MVP) |

- **Feedback-driven decisions to honor:** integration tests hit a **real Postgres**
  (Testcontainers), not mocks — mock/prod divergence is the exact failure class we avoid.
- **Controllable clock:** the engine reads time via a `Clock` bean so timer expiries are
  testable without real waits.

---

## 6. Security (cross-cutting)

- **Scoping:** a token for game A cannot touch game B — enforced on every REST call and
  STOMP subscribe (T00 §5, T03 §8).
- **Role gating:** derived from `performer_slot`/`judge_assignment` per performance
  (T02 §5); checked server-side on every gated command.
- **Judge comment suppression:** server-side on subscription (T03 §2/§8), not client
  honesty.
- **Display token:** read-only; rejected by all command destinations (T02 §2).
- **Rate limiting (post-MVP):** comments, likes, join attempts get a lightweight
  per-token bucket to blunt spam — **[DECISION]** deferred; not needed to confirm
  direction, and the party-scale trust model is low-risk for the MVP.
- **Input validation:** lean backend (T02 §3); frontends do strict UX validation (join
  mask, T06 §6).

---

## 7. Configuration & environments

- All tunables under a single **`karaogui.*`** namespace via `@ConfigurationProperties`
  (T08 is the dial board). Bound from `application.yml` + env vars; Spring profiles
  (`local`, etc.).
- **`local` profile** wires the docker-compose Postgres and the local media volume.
- **MVP flags:** a couple of coarse feature toggles keep deferred features cleanly off
  without dead code paths in the way — e.g. `karaogui.features.likePoints=false`
  (display-only likes for MVP, §like-points), `karaogui.features.pictureUpload=false`,
  `karaogui.features.challengeJob=false`, `karaogui.features.randomAssignment=false`.
  Post-MVP flips these on as the workstreams land.

---

## 8. Build phasing (MVP spine → confirm direction)

The MVP proves the **core loop end-to-end for Karaoke only**, run via one-command
docker-compose. Breadth is deferred to §9. (Full rationale agreed with the product
owner; summarized here as the build contract.)

| Phase | Deliverable | Testable outcome |
|-------|-------------|------------------|
| **0 — Skeleton** | Gradle SB4.1/Java25 backend; Angular workspace (`tv`,`phone`,libs); docker-compose (Postgres+backend); Flyway V1–V5; empty snapshot endpoint | `docker compose up` boots clean; migrations apply |
| **1 — Lobby (REST)** | create/join/start/snapshot; token auth; scoping; error model; join-code masked input; TV player list | create on one phone, join on another, both show on TV |
| **2 — Real-time backbone** | STOMP `/ws`, snapshot+stream, `seq`, `/state` `/ranking` `/performers` `/players`; reconnect/re-snapshot | a join appears live on TV + phones, no refresh |
| **3 — Karaoke loop** | performance state machine + persisted timers (30s/15s/skip); confirm/volunteer; judge eval (baseline+per-performer); audience rating; 85/15 scoring; ranking recompute; TV announce/countdown/embed/scoreboard; role-aware phone | full Karaoke run; scores land; ranking moves live — **direction confirmed here** |
| **4 — Close loop** | end-prompt MORE/OVER; results; paginated cycling scoreboard; comments feed + likes (**display-only, no point bonus**); containerize frontends | complete mini-session, demoable end-to-end |

**MVP deferrals (off via §7 flags or simply not built):** other 7 performance types,
biased random assignment + RANDOM slots, break/reassignment/strikethrough, comment
like **points**, challenge-injection job, picture upload, rate-limiting, multi-instance
scheduler lock, metrics dashboards.

**Sequencing note:** Phases 0–4 settle the T01/T02/T03 contracts, so they are built as a
coherent spine — lightly parallel at most (backend spine ‖ frontend shell against
mocked contracts), but **not** fanned out to many agents yet. Contracts must stabilize
before breadth.

---

## 9. Parallelization plan (post-MVP)

Once the spine holds and direction is confirmed, breadth fans out into **independent
workstreams** that slot into the proven spine along stable contracts. These map well to
parallel agents; the dependency notes prevent collisions.

| Stream | Scope | Depends on | Touches (write) | Contract stability |
|--------|-------|------------|-----------------|--------------------|
| **A — Assignment & availability** | biased random assignment (T05 §1) + RANDOM slots; break/reassignment/strikethrough (T04 §4) | spine; `performer_slot`, `player.on_break_until` | assignment service, break command, engine announce hook | new service + engine hook; no schema change |
| **B — Subjective types** | Magic, Dance, Acting/Mimic, Standup, Reverse Mimic: per-type submission validation + criteria (T05 §3.1) | spine scoring | submission validators, criteria catalog | additive; shares scoring engine with Karaoke |
| **C — Trivia** | `trivia_answer` table + per-answer capture + speed scoring (T05 §4) | spine; Flyway **V6** | new table, trivia scoring, judge answer-visibility | **owns V6 migration** (coordinate with D) |
| **D — Physical Challenges** | deterministic scoring + `@Scheduled` injection job (T05 §7) + `winner_player_id` | spine; Flyway **V6** | `performance.winner_player_id`, challenge job, confirm-only judging | **shares V6 with C** — one migration author |
| **E — Engagement polish** | like **points** bonus; picture upload (`UploadStore`); T08 tuning wired to config; rate-limiting | spine; §3 upload; §6 | scoring like-hook, upload adapter, config props, rate filter | additive; flips §7 flags on |

**Coordination rules for parallel agents:**
- **Migrations are serialized:** C and D both need `V6`; **one** agent authors
  `V6__trivia_scoring.sql` (the `trivia_answer` table **and** `winner_player_id`), the
  other rebases onto it. No two agents create the same/overlapping migration version.
- **Contracts are frozen at the MVP boundary:** streams add *new* DTOs/events, never
  mutate existing MVP ones without a contracts-lib change reviewed by all.
- **Shared surfaces (engine announce hook, scoring dispatcher) get a thin extension
  point** in the MVP so A–D plug in rather than edit the same method.
- **Flags (§7)** let each stream ship dark and flip on when green, avoiding half-wired
  features blocking the others.

---

## 10. Open items resolved / remaining

Resolved here: locking strategy (§1), identity seam concretely (§2), media constraints
& store interface (§3), testing approach incl. Testcontainers + controllable clock (§5),
rate-limiting deferral (§6), MVP phasing & parallelization (§8–9).

Remaining for build time:
- Exact retry bounds / backoff for optimistic conflicts → tune under load.
- STOMP heartbeat & reconnect backoff values → tune on real devices (T03/T06).
- Multi-instance scheduler lock (ShedLock) → only when we run >1 backend.
- Object-storage adapter → when we outgrow the local media volume.
