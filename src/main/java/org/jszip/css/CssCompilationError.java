package org.jszip.css;

/**
 * @author stephenc
 * @since 31/01/2013 23:58
 */
public class CssCompilationError extends Exception {

    private final String fileName;
    private final int line;
    private final int col;

    public CssCompilationError(String fileName, int line, int col) {
        this.fileName = fileName;
        this.line = line;
        this.col = col;
    }

    public CssCompilationError(String fileName, int line, int col, Throwable cause) {
        super(cause);
        this.fileName = fileName;
        this.line = line;
        this.col = col;
    }

    public CssCompilationError(String fileName, int line, int col, String message) {
        super(message);
        this.fileName = fileName;
        this.line = line;
        this.col = col;
    }

    public CssCompilationError(String fileName, int line, int col, String message, Throwable cause) {
        super(message, cause);
        this.fileName = fileName;
        this.line = line;
        this.col = col;
    }
}
