# Original specs

I want to develop an app that uses a Java Spring Backend and two Angular Typescript Frontends (one that is display-only on a big TV for all players to see and another frontend that will be rendered on the phone of each player and allows interacting with the game) to implement the specs below.

As a first step, read all the specs and write a structured documentation from a Business perspective, save the content in the directory ./docs/Business as .md files and submit for my review. Do not start implementing anything yet. We will refine the documentation as needed, and once the Business docs are done, we will start writing the Technical docs under ./docs/Technical and refining them.

Only after all Technical docs are written, then we will spin up several agents to implement it in parallel. 

Specs:

Structure a Party Game of performances where players can participate as a performer, a judge or part of the audience. Each performance will be evaluated based primarily on the Evaluation from judges but should also be evaluated a little bit by the audience. Each player has an individual score, and there should be a Ranking visible at all times.

The performances are defined by the players themselves -- they can submit something that they will perform themselves, something that will be performed by "random" players or a hybrid where they pick some of the performers and the game will assign the rest of the performers "randomly".

I said "random" between quotes because I want the algorithm to be highly biased towards players who haven't performed much (mainly) or who doesn't have many points (less strongly), so that it engages people to participate and have a greater chance of participating. Someone who joined 5 different performances is way less likely to be picked randomly than someone who joined only once. At the same time, I don't want to force players. There should be a feature to skip a particular performance if a player doesn't feel comfortable joining something in particular, and also another feature to "take a break" and disable being picked up for 15 minutes while they eat, go to the washroom, etc. During that time, even if someone assigned something to them, the game should reassign it automatically (show their name with a "Strikethrough" and someone else's name below it so that we know who was assigned to originally and who really performed). Of course, do not make someone a judge if they are taking a break.

It should be interactive and have comments that will show up live. Each comment will be attached to the current performance, and should be visible on the TV at all times, and should be displayed to the audience so that they can add new comments or interact with previous comments (i.e. give "like"). Judges are not shown comments on their phones, but they can see them on the TV. Audience can react to funny comments (comments with Likes grant a very small amount of points to the authors) and submit 1 rating per performance. The game will continue while there are pending performances remaining. Once all performances were done, the game should prompt whether there will be more or if the game is over. If the game is over, it should display results and a ranking that people can share.

There is one Host playing as a regular player. The host will only create a game and start it. Other players can join in while the game has already started. Make sure to display a URL at the bottom of the TV at all times so that new players can join. There is no maximum of players per game.

Details about the game:

1) One host creates an entity -- less call that entity a Game -- that has a unique ID (automatically generated value), and that's the main entity of our app. We should also have a Player entity (for starters, let's have just an ID, a Player displayname and a picture that they can upload), a Performance entity (to be defined below) that can have 3 judges if applicable (some performance types can be rated automatically and won't need judges) and from 1 to 4 performers (some types can have all 4, some types may have a lower maximum of performers), and whatever other entities we need to make everything possible.

There should be a Rating entity as well, and its primary key is a player id + performance id, which should assert that each player submits only one rating (if they aren't judges) or zero (if they are judges, then they don't give Rating, but they give Evaluations) per performance. Each Player belongs to only one game and the Performance also belongs to one game.

2) Players can submit a Performance that will have an ID that's an integer auto-increment. That performance has a Category that has to be one of a few options, to be defined below, and a list of performers (a performer is a player who must exist within the current game).

3) The Ratings that regular players give (i.e. non-Judges for that Performance and also non-Performers) has the primary keys -- player ID and performance ID --, but it also should have some information that helps players rate the performance. It's important that all Ratings have a Total Score that's an automatically generated value based on the individual scores given to a performance, and these will vary depending on the performance type (help me identify them).

Naturally, a player that's in the audience (i.e. non-Judge and non-Performer) should not block the flow. If they don't rate a Performance, their input is not recorded and the game moves on. If a player gives a Rating to a performance, they should be able to change it as many times as they want until the Performance is over (i.e. Performers are done and all Judges have submitted their evaluations), at that moment all Ratings are locked in.

The Performance should follow this flow: Performance is displayed on pause on the background of the screen, and a box at the front saying who will perform (doesn't matter if they were pre-determined by the Submitter or "randomly" picked by the algorithm). 

Then there should be a timer of 30 seconds asking performers to confirm that they are ready. The list of performers is composed by the original performer(s) manually picked + the random spots. How this composition is selected depends on the performance type.
As soon as all performers are confirmed, the Performance starts. After 15 seconds of waiting, the absent Performers (i.e. who haven't confirmed within 15 seconds) can be replaced by the first other player who volunteered to replace them. At this point, there's no random players selected, only volunteers who actively volunteer can replace an absent performer by clicking on a "Volunteer" button. This will replace the absent Performer immediately and confirm the volunteer. If not enough performers are "Confirmed" and the timer runs out, the Performance is skipped.

Here is a list of all types of Performances:
1) Karaoke -- main type. Players submit a YouTube link and a list of players. That list can be composed by existing players within the game or "Random" entries, so that players are picked up by the algorithm. Needs judges.

2) Talent show, could be one of the following categories:
2.1) Magic trick (requires at least 1 pre-determined Performer, can have 1 to 3 other performers either pre-determined or picked randomly)
2.2) Dance -- very similar to Karaoke. But instead of people singing, there will be people dancing. Needs judges too.
2.3) Acting/Mimic -- A video link is submitted and will be played after the challenge is done, just so that the audience can confirm what was the reference. Author is automatically one of the judges.
2.4) Standup comedy -- maximum 1 or 2 pre-determined performers. Does not accept random spots (but allows replacements if player does not confirm on time or is "taking a break").

3) Trivia -- Author of the trivia cannot join as a performer, but enters as one of the judges. Must have 4 players, all picked randomly. They must post 10 different questions.

4) Reverse mimic -- Author cannot join as a performer, but joins as a Judge. Author specifies how many players -- from 1 to 4 -- all randomly picked.

5) Physical challenges -- these pick 4 random players and are not submitted by anyone. Judges required only to assert the rules were followed, but they just enter who was the winner according to them (no scoring, it's all or nothing). Each of these will be automatically added to the Performance Queue after 4 at least occurrences of the other types. For example, if there were 3 karaoke performances and one Dance performance, there should be a job ran by the backend at every 60 seconds that detects this and adds the next challenge to the end of the queue. Next time it runs, if there has been 4 or more entries since the last Physical Challenge, it adds another.

It should always add them following the order below cyclically (i.e. after adding all 4, starts from the beginning adding them again)
5.1) Balloon: performers must fill a balloon until it bursts. First one to burst the balloon wins.

5.2) Water drinking: performers will enter how much water they are able to drink. Lowest value remains as the challenge so that they are all able to complete the challenge. First one to finish drinking the water wins. If all three are unable to drink everything, the one who drinks most of the water wins.

5.3) Marshmallow challenge: performers will put as many marshmallows in their mouths as they can. Whoever puts most of the marshmallows wins.

5.4) Cookie: performers receive a cookie on their forehead and they have to transfer the cookie to their own mouths, then eat the cookie. The cookie cannot touch anything other than the face of the performer. Players have 3 cookies each. Whoever eats all three cookies first without dropping, wins. If nobody could keep all 3 cookies from falling, then whoever eats two cookies first wins. If nobody could keep at least 2 cookies from falling, then whoever eats one cookie first wins. If all cookies fall, then everybody scores 0.




# Ordinary Questions & Assumptions

This section details some aspects that were not explicitly specified in the original requirements, but are not exhaustive. See them for some more details.

---

## Open Questions

### OQ-1: Judge Evaluation — Individual vs. Group

**Question:** Do judges evaluate the group performance as a whole (one evaluation per judge covering all performers), or do they evaluate each performer individually?

Answer:
Both. The judge must first evaluate the performance as a whole (the app then applies the evaluation to all performers equally as a starting point), then the judge drills down and changes the scores individually if they want. Once they are done, they submit the entire evaluation in one shot.

---

### OQ-2: Trivia Scoring Detail

**Question:** In Trivia, how exactly are individual contestant scores calculated? 

Answer:
- Each correct answer = fixed points
- Faster correct answers = more points (speed bonus)
- Wrong answers = penalty deduction



---

### OQ-3: Performance Queue Visibility

**Question:** Can all players see the full upcoming queue, or only the next performance?

Answer:
Queue does not have to be visible at all times, but we can display what's next in a corner while the current performance haven't started. Or in any other moments where there's a chance to show it without occupying the space of more relevant content. The focus is not the queue, nor is it a mystery.

---

### OQ-4: Can a Player Join Mid-Game?

Answer: Yes, players can join at any time while the game is ACTIVE. Late joiners start with 0 score and 0 performances, making them highly eligible for random assignment.

---

### OQ-5: Break Timer Reset

**Question:** If a player activates "On a Break" twice in a row (re-activates before the 15 minutes expires), does the timer reset?

Answer: Each activation of "On a Break" sets a fresh 15-minute window from that moment.

---

### OQ-6: Like Points — Exact Value

**Question:** How many points does a comment like award?

Answer: 1 point per like received. To be confirmed and documented in the Technical specification. But performances should be the main way of getting points.

---

### OQ-7: Scoring Weight Split

**Question:** How is it?

Answer: The 85%/15% split between judge evaluation and audience rating is used when the performance is subjective. When it's a performance with a deterministic score, such as the Physical Challenges, then the points are given to the winner accordingly, no variation.