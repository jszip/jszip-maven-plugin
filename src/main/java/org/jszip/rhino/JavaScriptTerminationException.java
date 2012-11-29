package org.jszip.rhino;

/**
 * @author stephenc
 * @since 29/11/2012 14:58
 */
public class JavaScriptTerminationException extends RuntimeException {

    private final int exitCode;

    public JavaScriptTerminationException() {
        this(0);
    }

    public JavaScriptTerminationException(Throwable cause) {
        this(0, cause);
    }

    public JavaScriptTerminationException(String message) {
        this(message, 0);
    }

    public JavaScriptTerminationException(String message, Throwable cause) {
        this(message, 0, cause);
    }

    public JavaScriptTerminationException(int exitCode) {
        this.exitCode = exitCode;
    }

    public JavaScriptTerminationException(int exitCode, Throwable cause) {
        super(cause);
        this.exitCode = exitCode;
    }

    public JavaScriptTerminationException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }

    public JavaScriptTerminationException(String message, int exitCode, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    public int getExitCode() {
        return exitCode;
    }
}
