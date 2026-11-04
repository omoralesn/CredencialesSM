package mx.org.smsem.credencialessm.dispositivos;

import java.nio.file.Path;

/**
 * Resultado de un intento de impresión (mock / preview / ZXP).
 */
public final class PrintResult {

    private final boolean accepted;
    private final String message;
    private final Path artifactPath;

    public PrintResult(boolean accepted, String message, Path artifactPath) {
        this.accepted = accepted;
        this.message = message;
        this.artifactPath = artifactPath;
    }

    public static PrintResult ok(String message) {
        return new PrintResult(true, message, null);
    }

    public static PrintResult ok(String message, Path artifactPath) {
        return new PrintResult(true, message, artifactPath);
    }

    public static PrintResult rejected(String message) {
        return new PrintResult(false, message, null);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getMessage() {
        return message;
    }

    public Path getArtifactPath() {
        return artifactPath;
    }
}
