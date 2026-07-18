# Engagement Mechanics

These are the systems that make KaraoGUI feel inclusive and lively. Their shared
purpose is to **maximize participation without ever forcing anyone**, and to keep the
game moving.

## 1. Biased "random" assignment

When a performance has **"random" performer slots**, the game fills them — but not
uniformly at random. The selection is **deliberately biased** to favor players who
are under-participating, so more people get pulled into the action.

The bias favors, in order of strength:

1. **Players who haven't performed much** — this is the **primary** factor. Someone
   who has joined **5** performances is **far less likely** to be picked than someone
   who has joined only **once**.
2. **Players with fewer points** — a **secondary, weaker** factor.

Consequences and rules:

- **Late joiners** (0 performances, 0 points) are among the **most eligible** — a
  natural way to welcome them in.
- Players who are **on a break** or who have **skipped** the specific performance are
  **not eligible** for that assignment.
- A player is **never forced** — the mechanisms below let them opt out gracefully,
  and the flow (confirm/replace/skip timers) ensures no one stalls the game.

> The exact weighting formula is deferred to the Technical docs; the business
> intent is: strongly boost the seldom-performers, gently boost the low-scorers.

## 2. Skip a specific performance

A player can **skip** a particular performance they don't feel comfortable joining.

- Skipping applies to **that one performance only.**
- A player who has skipped is **not eligible** to be randomly assigned to it.
- Skipping is friction-free and carries **no penalty** — it's an explicit design goal
  that people never feel pressured into a specific act.

## 3. Take a break

A player can **take a break** to step away (eat, restroom, etc.) without leaving the
game.

- Activating a break **disables the player from being picked** for **15 minutes.**
- **Re-activating resets the window:** each activation starts a **fresh 15-minute**
  timer from that moment.
- During a break, the player **must not be made a judge.**
- If a player was **already assigned** to something and then goes on a break, the
  game **automatically reassigns** their slot. On the display, the original name is
  shown **struck through** with the replacement's name **beneath it**, so it's clear
  who was originally assigned and who actually performed.

## 4. Ready-confirmation, replacement, and skip timers

These timers (detailed in Game Lifecycle) are themselves engagement mechanics — they
keep performances flowing regardless of who is paying attention:

- **30-second** ready-confirmation window for performers.
- **After 15 seconds**, unconfirmed performers can be **replaced by volunteers**
  (no new random picks at this stage — only active volunteers).
- If not enough performers confirm before the window ends, the performance is
  **skipped** rather than stalling the game.

This is also the path by which a **break-triggered reassignment** finds a
replacement performer.

## 5. Auto-queued Physical Challenges

To guarantee bursts of high-energy, everyone-can-play moments, the game **injects
Physical Challenges into the queue automatically** — nobody submits them.

**Cadence (business intent):**

- The backend periodically checks the recent queue history (on the order of **once a
  minute**).
- When there have been **at least 4** other-type performances **since the last
  Physical Challenge**, the **next** Physical Challenge is appended to the **end of
  the queue.**
- Example: with 3 karaoke performances and 1 dance performance since the last
  challenge, the recurring check detects the 4-performance threshold and queues the
  next challenge. The counter effectively resets, and the next challenge is queued
  again once **4 or more** further performances have occurred.

**Ordering (cyclic):**

Physical Challenges are added in this fixed order, cycling back to the start after
all four have been used:

1. **Balloon**
2. **Water drinking**
3. **Marshmallow challenge**
4. **Cookie**

→ then Balloon again, and so on.

> Exact interval and threshold checking are implementation details for the Technical
> docs. The business rule is: **space challenges out so roughly one appears for every
> ~4 other performances, always cycling through the four in order.**

## How the mechanics reinforce the goal

| Mechanic                     | Serves participation by…                                         |
|------------------------------|------------------------------------------------------------------|
| Biased random assignment     | Actively pulling in people who've performed little               |
| Skip                         | Letting people decline one act without feeling pressured         |
| Take a break                 | Letting people step away without penalty or awkward assignments  |
| Confirm / replace / skip     | Keeping the game flowing; turning absences into volunteer chances |
| Auto-queued challenges       | Injecting inclusive, everyone-can-join bursts of fun             |
