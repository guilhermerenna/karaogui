# Game Lifecycle

This document walks through a game from creation to conclusion, including how people
join, how a single performance runs, how the queue works, and how a game ends.

## 1. Creating a game

The host creates a game. On creation:

- The game receives a **unique ID** (automatically generated).
- A **join URL/code** is produced so others can join.

At this point the game exists but play hasn't begun.

## 2. Starting a game

The host **starts** the game, which makes it **active** and opens play. From here on,
performances can be submitted and run, and people can keep joining.

## 3. Joining

Anyone can join by opening the join URL and setting up a profile (display name +
optional picture).

- People can join **before or after** the game starts — joining mid-game is fully
  supported.
- A **late joiner** starts with **0 score and 0 performances**, which makes them
  highly eligible for random assignment (a deliberate "welcome in" effect).
- The join URL is shown on the **TV at all times**, at the bottom of the screen, so
  newcomers can always get in.

## 4. Submitting performances

Players submit performances from their phones. A submission specifies:

- The **type/category** of performance (see Performance Types).
- The **performers**, expressed as a combination of specific existing players and/or
  "random" slots — subject to the rules of that performance type (some require the
  submitter to perform, some forbid it, some allow all-random, etc.).
- Any type-specific content (e.g. a YouTube link for karaoke, the questions for
  trivia).

Submitted performances go into the **queue** to be played in turn. (Physical
challenges are special: nobody submits them — the game injects them automatically.
See below and Engagement Mechanics.)

## 5. The performance flow

Performances run **one at a time**. Each one follows this sequence:

### a. Announcement (performance paused in the background)

The performance is shown **paused** in the background of the TV, with a box in front
announcing **who will perform**. This is the same whether the performers were
pre-picked by the submitter or filled in "randomly" by the game.

### b. Ready confirmation — 30-second window

A **30-second timer** begins, asking the performers to confirm they are ready. The
final performer list is the manually picked performer(s) **plus** the random slots,
composed according to the performance type's rules.

### c. Replacement window — after 15 seconds

**15 seconds** into the confirmation window, any performers who **haven't confirmed
yet** become replaceable. At this point:

- **No new random players are selected.** Only **volunteers** can step in.
- Any player may press a **"Volunteer"** button to **immediately replace** an
  absent (unconfirmed) performer. Volunteering both performs the replacement and
  confirms the volunteer in one action.

This is also how a performer who is **on a break** gets handled — the game reassigns
their slot, and the display shows the original name **struck through** with the
replacement's name beneath it, so everyone can see who was originally assigned and
who actually performed.

### d. Start or skip

- **If all performer slots are confirmed** (through original confirmations and/or
  volunteers), the **performance starts**.
- **If the 30-second timer runs out** without enough confirmed performers, the
  performance is **skipped**.

### e. Running, evaluating, rating

While the performance runs:

- **Judges** (if the type needs them) prepare their **Evaluations**.
- **Audience** members may submit and freely change their single **Rating**.
- **Comments** flow live and appear on the TV; the audience can post and like them.

### f. Locking in

A performance is considered **over** when the **performers are done and all judges
have submitted their evaluations**. At that moment **all ratings are locked in** —
audience members can no longer change their ratings — and the performance's score is
finalized. Scores and the ranking update.

## 6. The performance queue

- Performances are played from a queue, generally in the order they were added.
- The queue does **not** need to be visible at all times. It's acceptable to show
  **what's next** in a corner of the TV while the current performance hasn't started
  yet, or at other low-pressure moments. The queue is neither the focus nor a
  secret.
- **Physical challenges are auto-added** to the end of the queue by the game on a
  recurring basis, spaced out among the other performances to keep energy up (see
  Engagement Mechanics for the exact cadence and ordering).

## 7. Ending a game

The game **continues while there are pending performances** in the queue.

When the queue empties, the game **prompts whether there will be more performances
or the game is over**:

- **More:** play resumes as new performances are submitted/queued.
- **Over:** the game moves to results.

On game over, the TV displays the **final results and ranking**, presented in a way
that people can **share**.

## Lifecycle at a glance

```
Create ──> Start ──> [ Active ]
                        │
        join anytime ───┤
    submit performances─┤
                        ▼
             ┌─ pull next from queue ─┐
             │                        │
   Announce → Confirm(30s, replace@15s) → Start │ Skip
             │        │                         │
             │   run → judge/rate/comment       │
             │        │                         │
             │   lock in → update scores/ranking│
             └────────────┬───────────────────┘
                          │ queue empty?
                          ▼
                   Prompt: more or over?
                    │              │
                  more           over ──> Results + shareable ranking
```
