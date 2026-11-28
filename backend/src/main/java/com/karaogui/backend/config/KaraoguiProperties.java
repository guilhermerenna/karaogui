package com.karaogui.backend.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("karaogui")
public record KaraoguiProperties(
        Videos videos,
        Youtube youtube,
        Timers timers,
        Scoreboard scoreboard,
        Assignment assignment,
        Scoring scoring,
        Challenge challenge,
        Features features,
        Security security) {

    public String[] allowedOriginPatterns() {
        return security != null
                ? security.resolvedAllowedOriginPatterns()
                : new Security(null).resolvedAllowedOriginPatterns();
    }

    public record Videos(
            Path rootDir,
            DataSize streamChunkSize) {}

    public record Youtube(
            String apiKey,
            Duration judgingGrace,
            Duration fallbackCeiling) {}

    public record Timers(
            Duration readyConfirmWindow,
            Duration volunteerWindow,
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
            boolean randomAssignment,
            boolean youtube,
            boolean videoDelete) {}

    public record Security(
            List<String> allowedOriginPatterns) {

        private static final List<String> DEFAULT_ORIGINS = List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://192.168.*:*",
                "http://10.*:*",
                "http://172.16.*:*");

        public String[] resolvedAllowedOriginPatterns() {
            if (allowedOriginPatterns == null) {
                return DEFAULT_ORIGINS.toArray(String[]::new);
            }
            List<String> cleaned = allowedOriginPatterns.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .toList();
            return (cleaned.isEmpty() ? DEFAULT_ORIGINS : cleaned).toArray(String[]::new);
        }
    }
}
