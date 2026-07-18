# KaraoGUI — Business Overview

## What it is

KaraoGUI is a live party game centered on **performances**. People at a gathering
take turns performing (singing karaoke, doing magic tricks, dancing, telling
jokes, competing in physical challenges, and more). Every performance is scored,
everyone accumulates an individual score, and a **live ranking** is visible at all
times. The goal is to keep the whole room engaged — as performers, as judges, or
as an interactive audience.

The game is designed for a single physical space (a living room, a party) where a
big screen is shared by everyone and each person uses their own phone to take part.

## The three surfaces

The experience is delivered through three coordinated surfaces:

1. **The TV (shared display).** A big-screen view everyone in the room can see. It
   shows the currently running performance, who is performing, the live comment
   feed, the current ranking, and — at the bottom, at all times — a **join URL** so
   new people can hop in. This surface is display-only; nobody interacts with it
   directly.

2. **The phone (player controller).** Each participant uses their own phone. This is
   where they join a game, set up their profile, submit performances, confirm they
   are ready to perform, volunteer to replace absent performers, judge or rate,
   post and like comments, take a break, and skip. What a player sees on their phone
   depends on their role in the current performance.

3. **The backend (game engine).** The behind-the-scenes service that holds the
   single source of truth for each game: players, performances, scores, the
   performance queue, and all the rules. It runs the game clock, applies the
   scoring rules, drives the biased random assignment, handles breaks and
   reassignments, and periodically injects physical challenges into the queue.

## The core loop

At a high level, one game plays out like this:

1. A **host** creates a game. The game gets a unique ID and a join URL/code.
2. People **join** on their phones — including after the game has already started.
3. Players **submit performances** (a karaoke song, a magic trick, a trivia set,
   etc.), choosing who performs — specific people, "random" slots the game fills,
   or a mix.
4. Performances run one at a time from a **queue**. For each one: the performers
   are announced, they confirm they are ready, the performance happens, judges
   evaluate and the audience rates, and comments fly live on the TV.
5. Scores update and the **ranking** refreshes.
6. When the queue empties, the game asks whether to **continue** (more
   performances) or **end**.
7. On ending, the game shows **final results** and a shareable ranking.

## Design principles

These themes run through the whole product and should guide every decision:

- **Maximize participation.** The single most important goal. Mechanics like biased
  random assignment deliberately pull in people who haven't performed much, so
  everyone gets a turn.
- **Never force anyone.** Participation is encouraged, never mandatory. Players can
  skip a specific performance and can take a break without penalty.
- **The audience is not passive.** Watching still counts — the audience rates
  performances and interacts with a live comment feed, and can even earn a little
  score for funny comments.
- **Always show the standings.** The ranking is visible at all times to keep the
  competition alive and fun — and it's driven purely by each player's **total
  score**, the only player-facing metric. On busy games it paginates and cycles so
  it stays visible without crowding the screen.
- **Keep the flow moving.** Absent or unresponsive people never stall the game;
  timers, replacements, and skips keep performances rolling.
- **Performing is the main way to earn points.** Other sources (like comment likes)
  exist for fun and engagement but are intentionally minor.

## Scope of the Business docs

This documentation set describes KaraoGUI from a **business/product perspective** —
what the game is, who takes part, how it plays, how scoring works, and the rules
that make it engaging. It intentionally leaves data types, APIs, algorithms, and
implementation choices to the Technical documentation phase that follows.

## Document map

- **00 — Overview** (this document): the product at a glance.
- **01 — Roles & Participants:** the host, players, and the performer/judge/audience
  roles.
- **02 — Game Lifecycle:** creating, joining, the performance flow, the queue, and
  ending a game.
- **03 — Performance Types:** every performance category and its rules.
- **04 — Scoring & Ranking:** how points are earned and standings are computed.
- **05 — Engagement Mechanics:** biased random assignment, skipping, breaks, and
  auto-queued challenges.
- **06 — Comments & Interaction:** the live comment feed, likes, and visibility
  rules.
- **07 — Glossary & Entities:** shared vocabulary and the core business concepts.
