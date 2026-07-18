# Performance Types

Every performance belongs to a **type** (and some types have **categories**). The
type determines how many performers there are, how those performers are chosen,
whether judges are needed, whether the submitter performs or judges, and how the
result is scored.

Two broad families exist:

- **Subjective performances** — scored by judges and audience (see Scoring &
  Ranking for the 85% / 15% split). These are Karaoke, the Talent Show categories,
  Trivia, and Reverse Mimic.
- **Deterministic performances** — Physical Challenges, which have a clear winner
  and award points by outcome, not by opinion.

---

## 1. Karaoke *(main type)*

The signature performance of the game.

- **Submission:** a **YouTube link** plus a list of performers.
- **Performers:** existing players and/or **"random"** slots (filled by the biased
  algorithm).
- **Judges:** **required.**
- **Scoring:** subjective (judges + audience).

---

## 2. Talent Show

An umbrella type with several categories. Pick one category per submission.

### 2.1 Magic trick
- **Performers:** at least **1 pre-determined** performer, plus **1 to 3 additional**
  performers that may be pre-determined or picked randomly.
- **Judges:** required.
- **Scoring:** subjective.

### 2.2 Dance
- Very similar to Karaoke, but performers **dance** instead of singing.
- **Judges:** required.
- **Scoring:** subjective.

### 2.3 Acting / Mimic
- A **video link** is submitted and played **after** the challenge is done, so the
  audience can confirm what the reference was.
- **The author is automatically one of the judges.**
- **Scoring:** subjective.

### 2.4 Standup comedy
- **Performers:** maximum of **1 or 2 pre-determined** performers.
- **Random spots:** **not allowed.** (Replacements are still allowed if a performer
  fails to confirm in time or is on a break.)
- **Judges:** required.
- **Scoring:** subjective.

---

## 3. Trivia

- **Author:** **cannot** perform; instead the author enters as **one of the judges.**
- **Performers:** exactly **4 players, all picked randomly.**
- **Content:** the author must post **10 different questions**, and for each question
  they submit **both the question and its answer** (the correct answer is captured up
  front at submission time).
- **Answer visibility:** during play, **all judges see the author-provided answer**
  for the current question at each part of the round, so they can confirm whether a
  performer's response is correct. (Performers and audience do not see the answer.)
- **Scoring:** subjective in structure but driven by answer correctness and speed —
  each correct answer earns fixed points, faster correct answers earn a **speed
  bonus**, and wrong answers incur a **penalty deduction** (see Scoring & Ranking).

---

## 4. Reverse Mimic

- **Author:** **cannot** perform; joins as a **judge.**
- **Performers:** the author specifies **how many — from 1 to 4 — all picked
  randomly.**
- **Judges:** required (includes the author).
- **Scoring:** subjective.

---

## 5. Physical Challenges *(auto-queued, deterministic)*

These are **not submitted by anyone.** The game **automatically adds** them to the
queue on a recurring cadence to keep everyone engaged (see Engagement Mechanics for
the exact timing and cyclic ordering).

Common rules for all physical challenges:

- **Performers:** **4 random players.**
- **Judges:** required **only to confirm the rules were followed.** Judges do **not**
  score — they simply record **who the winner was** according to them. It's
  **all-or-nothing** (no gradations).
- **Scoring:** deterministic — points go to the winner per the challenge's rule.

The four challenges, added **cyclically** in this order (after all four have been
used, the cycle restarts):

### 5.1 Balloon
Performers must inflate a balloon until it bursts. **First to burst the balloon
wins.**

### 5.2 Water drinking
Performers each enter **how much water they can drink**. The **lowest value** is
taken as the challenge amount (so everyone can reasonably complete it). **First to
finish drinking that amount wins.** If none can finish, **whoever drinks the most
wins.**

### 5.3 Marshmallow challenge
Performers stuff as many marshmallows into their mouths as they can. **Whoever fits
the most wins.**

### 5.4 Cookie
Each performer gets a cookie on their forehead and must move it to their mouth using
only their face (the cookie may not touch anything but the performer's face), then
eat it. Each player has **3 cookies**. Winner determination, in order:

1. **First to eat all 3** cookies without dropping wins.
2. If nobody kept all 3 from falling, **first to eat 2** wins.
3. If nobody kept at least 2, **first to eat 1** wins.
4. If **all cookies fall**, **everybody scores 0.**

---

## Quick reference

| Type / Category      | Performers                         | Random spots?            | Judges | Author role         | Scoring        |
|----------------------|------------------------------------|--------------------------|:------:|---------------------|----------------|
| Karaoke              | 1–4 (players and/or random)        | Yes                      |  Yes   | May perform         | Subjective     |
| Magic trick          | ≥1 pre-set + 1–3 more              | Yes (for the extra slots)|  Yes   | May perform         | Subjective     |
| Dance                | 1–4 (players and/or random)        | Yes                      |  Yes   | May perform         | Subjective     |
| Acting / Mimic       | per submission                     | Yes                      |  Yes   | **Auto-judge**      | Subjective     |
| Standup comedy       | 1–2 pre-set                        | **No** (replacements ok) |  Yes   | May perform         | Subjective     |
| Trivia               | exactly 4, **all random**          | Yes (all)                |  Yes   | **Judge, no perform** | Correct+speed |
| Reverse Mimic        | 1–4, **all random**                | Yes (all)                |  Yes   | **Judge, no perform** | Subjective    |
| Physical Challenge   | 4 random                           | Yes (all)                | Confirm-only | n/a (auto)     | Deterministic  |

> Notes: "Random spots" are filled by the biased assignment algorithm. Even types
> that forbid random spots still permit **volunteer replacements** when a performer
> fails to confirm or is on a break. Exact performer maximums per type are honored
> during submission and assignment.
