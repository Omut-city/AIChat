package omut.aichat.chat;

public record LlmResponse(
        String text,
        long durationMillis,
        int evalCount,
        long evalDurationNanos
) {

    public double tokensPerSecond() {
        if (evalDurationNanos <= 0 || evalCount <= 0) return 0.0;
        return evalCount / (evalDurationNanos / 1_000_000_000.0);
    }
}