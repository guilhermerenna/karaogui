package com.karaogui.backend.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("karaogui")
public record KaraoguiProperties(
        Youtube youtube,
        Timers timers,
        Scoreboard scoreboard,
        Assignment assignment,
        Scoring scoring,
        Challenge challenge,
        Features features) {

    public record Youtube(
            String apiKey,
            Duration judgingGrace,
            Duration fallbackCeiling) {}

    public record Timers(
            Duration readyConfirmWindow,
            Duration replacementOpenAfter,
            Duration breakDuration) {}

    public record Scoreboard(
            int maxDisplayedPlayer,
            Duration pageInterval) {}

    public record Assignment(
            int wPerf,
            int wScore,
            int scoreNorm) {}

    public record Scoring(
            double subjectiveJudgeWeight,
            double subjectiveAudienceWeight,
            int subjectivePointScale,
            int triviaCorrectPoints,
            List<Integer> triviaSpeedBonus,
            int triviaWrongPenalty,
            int physicalChallengePoints,
            int likePoints) {}

    public record Challenge(
            Duration jobInterval,
            int threshold) {}

    public record Features(
            boolean likePoints,
            boolean pictureUpload,
            boolean challengeJob,
            boolean randomAssignment) {}
}
