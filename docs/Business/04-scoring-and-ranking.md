# Scoring & Ranking

Every player has an **individual score**, and a **ranking** is visible at all times.
This document explains where points come from and how a performance's score is
determined. Exact point values and formulas are intentionally left to the Technical
documentation; here we describe the business rules.

## Where points come from

1. **Performances** — the **main** source of points, by far. Everything else is
   minor by design.
2. **Comment likes** — a very small bonus for authoring comments that others like.

## Judge Evaluations vs. Audience Ratings

Two different mechanisms feed a subjective performance's score:

- **Evaluation** — given by **judges**. Judges give a structured evaluation, not a
  single number. Judges do **not** give a Rating.
- **Rating** — given by **audience** members (non-judges, non-performers). Each
  audience member may give **at most one** rating per performance and can change it
  freely until the performance locks in.

### How judges evaluate (whole-then-individual)

Judges evaluate in two steps and submit once:

1. **Whole-group first.** The judge evaluates the performance **as a whole**. The
   app applies that evaluation to **all performers equally** as a starting point.
2. **Individual drill-down.** The judge may then **adjust individual performers'**
   scores up or down from that baseline.
3. **Submit once.** When satisfied, the judge submits the **entire evaluation in one
   shot.**

### Rating detail helpers

Ratings carry supporting detail to help the audience rate meaningfully. The specific
criteria depend on the performance type. Regardless of the detail, **every Rating has
an automatically computed Total Score** derived from its individual sub-scores; how
that total is composed varies by performance type (to be finalized in the Technical
docs).

## Combining scores: the 85% / 15% split

For **subjective** performances, the final performance score combines:

- **85% — judge evaluations** (the primary driver), and
- **15% — audience ratings** (a meaningful but secondary voice).

This split applies **only to subjective performances**.

## Deterministic performances (Physical Challenges)

Physical challenges are **not** subject to the 85/15 split. They have a clear,
rules-based outcome:

- Judges only **confirm the rules were followed** and record **the winner** — they
  don't score.
- Points are awarded to the **winner** according to the challenge's rule
  (all-or-nothing; no partial gradations).
- In the specific case where a challenge's rules result in no winner (e.g. the
  Cookie challenge where **all cookies fall**), **everyone scores 0** for it.

## Trivia scoring

Trivia is scored from actual answers rather than opinion. The **author supplies the
correct answer** for each of the 10 questions at submission time, and **judges see
that answer** during play, so correctness is judged against a known reference:

- **Correct answer:** a **fixed** number of points.
- **Speed bonus:** **faster** correct answers earn **more** points.
- **Wrong answer:** a **penalty deduction.**

(The exact point/bonus/penalty values are to be set in the Technical docs.)

## Comment likes

- A liked comment grants its author a **very small** amount of points — on the order
  of **1 point per like** (to be confirmed in the Technical docs).
- This exists purely to reward fun engagement and must never rival performing as a
  way to climb the ranking.

## When scores lock in

A performance's contribution to scores is finalized when the performance is **over**
— i.e. **performers are done and all judges have submitted their evaluations.** At
that moment:

- **All audience ratings are locked** (no more changes).
- The performance's total is computed and **added to each relevant player's score.**
- The **ranking updates.**

## The ranking

- The ranking is **visible at all times** (prominently on the TV).
- It reflects each player's **cumulative individual score**, and **nothing else** —
  score is the **only** metric players see. Internal figures such as how many
  performances a player has joined are **never** shown here (or anywhere player-facing).
- Late joiners enter at **0** and climb as they participate.
- At **game over**, the final ranking is presented in a **shareable** form.

### Paginated scoreboard

The ranking must stay visible without dominating the screen. To achieve this when
there are many players:

- The scoreboard shows at most **`MAX_DISPLAYED_PLAYER`** players at a time (**5** for
  now).
- If there are **more** than that, the scoreboard splits into **multiple pages**,
  each holding up to `MAX_DISPLAYED_PLAYER` players in rank order (page 1 = top
  players, page 2 = next, and so on).
- Pages **cycle automatically**: a page stays on screen for a short interval (about
  **5 seconds**), then the next page replaces it in the same space. After the last
  page, it **loops back to the first.**
- This keeps the full ranking continuously viewable while occupying a small,
  constant footprint.

> `MAX_DISPLAYED_PLAYER` (initially **5**) and the per-page display interval
> (~5 seconds) are configurable; final values live in the Technical docs.

## Summary

| Source                 | Applies to               | Weight / Rule                          |
|------------------------|--------------------------|----------------------------------------|
| Judge Evaluation       | Subjective performances  | **85%** of the performance score       |
| Audience Rating        | Subjective performances  | **15%** of the performance score       |
| Deterministic outcome  | Physical Challenges      | Winner takes points; all-or-nothing    |
| Trivia answers         | Trivia                   | Fixed per correct + speed bonus − penalties |
| Comment likes          | Any player               | ~1 pt per like (minor by design)       |
