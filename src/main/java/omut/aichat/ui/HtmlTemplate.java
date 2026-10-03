package omut.aichat.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class HtmlTemplate {

    private final String template;

    public HtmlTemplate(String resourcePath) {
        this.template = readResource(resourcePath);
        if (this.template.isEmpty()) {
            throw new IllegalStateException("Template not found or empty: " + resourcePath);
        }
    }

    public String render(String cssContent, String jsContent, String body) {
        return template
                .replace("{{CSS}}", cssContent)
                .replace("{{JS}}", jsContent)
                .replace("{{BODY}}", body);
    }

    /**
     * Reads a classpath resource as UTF-8 text.
     * Returns an empty string if the resource is missing or unreadable.
     */
    public static String readResource(String path) {
        try (InputStream in = HtmlTemplate.class.getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("Resource not found: " + path);
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to read resource: " + path + " — " + e.getMessage());
            return "";
        }
    }
}