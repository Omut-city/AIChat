package omut.aichat.ui;

/**
 * One row in the prompt selector ComboBox. {@code id} is the stable
 * key used by {@link omut.aichat.config.AppConfig}; {@code name} is
 * the display label the user sees. {@link #toString()} returns the
 * name so the ComboBox renders it without a cell factory.
 */
public record PromptItem(String id, String name) {
    @Override
    public String toString() {
        return name;
    }
}