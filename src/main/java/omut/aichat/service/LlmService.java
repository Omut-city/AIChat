package omut.aichat.service;

public interface LlmService {
    String ask(String prompt);
    boolean isAvailable();
}
