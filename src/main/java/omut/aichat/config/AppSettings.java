package omut.aichat.config;

/**
 * Immutable snapshot of the user-tunable settings edited in the
 * Settings dialog. Carries validated values only — the compact
 * constructor rejects out-of-range input, so callers can rely on
 * every field being usable without further checks.
 */
public record AppSettings(
        String baseUrl,
        double temperature,
        int requestTimeoutMinutes,
        int historyMaxMessages,
        int attachMaxChars
) {

    public AppSettings {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException(
                    "temperature must be in [0.0, 2.0], got " + temperature);
        }
        if (requestTimeoutMinutes < 1 || requestTimeoutMinutes > 120) {
            throw new IllegalArgumentException(
                    "requestTimeoutMinutes must be in [1, 120], got " + requestTimeoutMinutes);
        }
        if (historyMaxMessages < 2 || historyMaxMessages > 1000) {
            throw new IllegalArgumentException(
                    "historyMaxMessages must be in [2, 1000], got " + historyMaxMessages);
        }
        if (attachMaxChars < 1) {
            throw new IllegalArgumentException(
                    "attachMaxChars must be positive, got " + attachMaxChars);
        }
    }
}