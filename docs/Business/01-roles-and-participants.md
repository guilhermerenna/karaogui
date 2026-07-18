# Roles & Participants

KaraoGUI has one **host** per game and any number of **players**. Beyond that, every
player takes on a **role** that changes from performance to performance. This document
describes who these people are and what each can do.

## The Host

The host is the person who sets up the game. Importantly, **the host is also a
regular player** — they perform, judge, rate, and comment like everyone else.

The host's only special responsibilities are:

- **Creating the game**, which generates the unique game ID and the join URL.
- **Starting the game**, which opens it up for play.

After starting, the host has no elevated powers over gameplay — they are just
another participant. There is exactly one host per game.

## Players

A player is anyone taking part in a game. To take part, a person joins the game
(via the join URL/code) and sets up a simple profile:

- A **display name** (how they appear on the TV, ranking, comments, etc.).
- A **profile picture** they can upload.

Key facts about players:

- Each player **belongs to exactly one game**.
- Every player has an **individual score**, which is the only player-facing metric:
  it drives the **Ranking**.
- The backend also tracks **how many performances each player has taken part in**,
  but this count is **internal only** — it feeds the biased assignment algorithm and
  is **never shown to players** or highlighted anywhere in the experience.
- **There is no maximum** number of players.
- Players can **join mid-game** (see Game Lifecycle). Late joiners start with a
  score of 0 and 0 performances, which makes them especially likely to be picked
  for random slots — a deliberate way to welcome them in quickly.

## Roles (per performance)

A player's **role is not fixed** — it is defined relative to each individual
performance. The same person might perform in one, judge the next, and simply watch
the one after. The three roles are:

### Performer

Someone taking part in the performance being scored. Depending on the performance
type, there can be **1 to 4 performers**. Performers may be:

- **Pre-determined** — chosen explicitly by whoever submitted the performance, or
- **"Random"** — slots the game fills using its biased random assignment (see
  Engagement Mechanics), or
- **A mix** of both.

Performers confirm they're ready before the performance starts, and may be replaced
by volunteers if they don't confirm in time or are on a break.

### Judge

Someone who evaluates a performance. Judges give a structured **Evaluation** (not a
simple rating), and their input is the primary driver of a performance's score.

- A performance that needs judges has **3 judges** (except where a type specifies
  otherwise — e.g. some types make the submission's author an automatic judge, and
  physical challenges use judges only to confirm the rules and pick a winner).
- **Judges do not give a Rating** — evaluating and rating are mutually exclusive for
  a given performance.
- Some performance types are scored **automatically/deterministically** and **need
  no judges** at all.
- A player who is **on a break must not be made a judge.**
- On their phones, **judges do not see the live comments** (to avoid influencing
  their evaluation); they can still see comments on the TV.

### Audience

Everyone who is neither a performer nor a judge for the current performance. The
audience is deliberately **active, not passive**:

- Each audience member may submit **one Rating** for the performance (optional).
- They can **change that rating freely** until the performance is locked in.
- They can **post comments** and **like** others' comments on the live feed.
- Not rating **never blocks the game** — if an audience member doesn't rate, their
  input simply isn't counted and play moves on.

## Role exclusivity within a performance

For any single performance, a player is in exactly one of these positions:

- If they are a **performer**, they don't judge or rate that performance.
- If they are a **judge**, they give an Evaluation and do **not** give a Rating.
- Otherwise they are **audience** and may give one Rating.

This exclusivity is what guarantees, for example, that a Rating exists for a player
only when that player was genuinely an audience member for that performance.

## Summary table

| Capability                        | Host | Performer | Judge | Audience |
|-----------------------------------|:----:|:---------:|:-----:|:--------:|
| Create & start the game           |  ✅  |     —     |   —   |    —     |
| Perform                           |  ✅* |    ✅     |   —   |    —     |
| Give an Evaluation                |  ✅* |     —     |  ✅   |    —     |
| Give a Rating                     |  ✅* |     —     |   —   |    ✅    |
| Post & like comments              |  ✅  |    ✅     |  ✅   |    ✅    |
| See comments on their **phone**   |  ✅  |    ✅     |  ❌   |    ✅    |
| Take a break / skip               |  ✅  |    ✅     |  ✅   |    ✅    |

\* The host participates in these **as a regular player**, according to whichever
role they hold for a given performance.
