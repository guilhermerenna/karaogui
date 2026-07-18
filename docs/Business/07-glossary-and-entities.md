# Glossary & Entities

A shared vocabulary for KaraoGUI, plus the core business concepts. This describes the
concepts and their relationships from a product perspective — **not** database
schemas, data types, or keys, which belong to the Technical documentation.

## Glossary

- **Game** — one play session. Has a unique ID and a join URL. Everything (players,
  performances, scores) belongs to a single game.
- **Host** — the player who creates and starts the game. Otherwise a regular player.
- **Player** — a participant in a game. Has a display name, an optional picture, an
  individual score (the only player-facing metric), and an internal count of
  performances taken part in (used only by the assignment algorithm, never shown to
  players).
- **Role** — a player's relationship to a **specific** performance: **performer**,
  **judge**, or **audience**. Roles change from performance to performance.
- **Performer** — a player taking part in the performance being scored (1–4 of them).
- **Judge** — a player who gives a structured **Evaluation** of a performance.
- **Audience** — everyone who is neither performer nor judge for the current
  performance; may give one **Rating** and interacts with comments.
- **Performance** — a single scored act (karaoke, magic trick, trivia, challenge,
  etc.), played from the queue.
- **Performance type / category** — the kind of performance, which sets performer
  counts, judge needs, author rules, and scoring.
- **Evaluation** — a judge's structured, whole-then-individual scoring of a
  performance. Judges submit one Evaluation each.
- **Rating** — an audience member's single scored input for a performance, with a
  computed **Total Score**; changeable until the performance locks in.
- **Total Score (of a Rating)** — an automatically computed value derived from the
  Rating's individual sub-scores; its composition varies by performance type.
- **Score (of a Player)** — a player's cumulative points across the game.
- **Ranking** — the ordered standings of players by **total score** (the only
  player-facing metric), visible at all times. Paginates when players exceed
  `MAX_DISPLAYED_PLAYER` (initially 5), cycling pages every ~5 seconds.
- **Comment** — a live message attached to the current performance, shown on the TV.
- **Like** — a lightweight positive reaction to a comment; grants the comment's
  author a very small point bonus.
- **Queue** — the ordered list of upcoming performances.
- **Random slot** — a performer position filled by the biased assignment algorithm.
- **Volunteer** — a player who actively steps in to replace an unconfirmed/absent
  performer during the replacement window.
- **Break** — a 15-minute state in which a player can't be picked (and can't be made
  a judge); reassigns any slots they held.
- **Skip** — a player's opt-out of a specific performance's random assignment.
- **Physical Challenge** — a deterministic, auto-queued performance (Balloon, Water,
  Marshmallow, Cookie) with a rules-based winner.

## Core entities (conceptual)

> The following describes concepts and relationships only. Attributes are listed to
> convey meaning, not to prescribe data types or keys.

### Game
The top-level entity and the app's main concept.
- Has a unique identifier and a join URL.
- Owns all players, performances, and the queue for that session.
- Has a lifecycle state (created → active → over).

### Player
A participant, belonging to exactly one game.
- Display name; optional uploaded picture.
- Individual score (player-facing); internal count of performances participated in
  (backend-only, feeds assignment — never displayed to players).
- Transient states: on-break, and per-performance skips.
- One player per game is the host.

### Performance
A single scored act, belonging to one game.
- A type/category that governs its rules.
- A set of performers (1–4), composed of pre-determined players and/or random slots
  per the type's rules.
- Zero or three judges (or type-specific judge rules; some types make the author a
  judge; physical challenges use confirm-only judges).
- Type-specific content (e.g., a YouTube link, an acting reference video, trivia
  question-and-answer pairs).
- A lifecycle: announced → confirming → running → locked/over (or skipped).
- A resulting performance score that feeds player scores.

### Rating
An audience member's scored input on a performance.
- Conceptually keyed by the **player + performance** pair, which guarantees **one
  Rating per audience member per performance** (and **zero** for judges, who give
  Evaluations instead).
- Carries type-specific detail plus an automatically computed **Total Score**.
- Editable until the performance locks in, then frozen.

### Evaluation
A judge's structured scoring of a performance.
- One per judge per performance.
- Whole-group baseline plus optional per-performer adjustments, submitted together.
- Judges do not also give a Rating for the same performance.

### Comment
A live message attached to the current performance.
- Authored by a player; shown on the TV.
- Can receive likes; accumulated likes grant the author a small point bonus.

### Like
A reaction to a comment.
- Attributed to the reacting player and the target comment.
- Drives the small author point bonus.

### Queue entry
A pending performance's place in line.
- Ordered; physical challenges are auto-appended on a recurring cadence in a fixed
  cyclic order.

## Relationships at a glance

```
Game (1) ──── owns ────> (many) Player
Game (1) ──── owns ────> (many) Performance ──> ordered in ──> Queue

Performance (1) ── has ──> (1–4) Performer  [Player in a role]
Performance (1) ── has ──> (0 or 3*) Judge   [Player in a role]  *type-specific
Performance (1) ── receives ──> (many) Rating  [one per audience Player]
Performance (1) ── receives ──> (many) Evaluation [one per Judge]
Performance (1) ── has ──> (many) Comment ── receives ──> (many) Like

Player (1) ── gives ──> (0..1) Rating   per Performance (audience only)
Player (1) ── gives ──> (0..1) Evaluation per Performance (judge only)
Player (1) ── authors ──> (many) Comment
Player (1) ── gives ──> (many) Like
```

## Invariants (business rules to preserve)

- A **Rating** exists only for an **audience** player on a given performance; a judge
  never has a Rating for that performance.
- Each audience player has **at most one** Rating per performance.
- Each judge has **exactly one** Evaluation per performance they judge.
- Every player and every performance belongs to **exactly one game.**
- A player **on a break** is never assigned as performer or judge; existing
  assignments are reassigned.
- A player who **skipped** a performance is not randomly assigned to it.
- **Performing** is the dominant point source; likes are minor by design.
- The **only** player-facing metric is **total score** (via the Ranking). The
  performance-participation count is **backend-only** and must never be surfaced to
  players.
