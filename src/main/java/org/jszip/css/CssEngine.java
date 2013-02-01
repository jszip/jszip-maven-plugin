package org.jszip.css;

/**
 * @author stephenc
 * @since 31/01/2013 23:42
 */
public interface CssEngine {
    String toCSS(String name) throws CssCompilationError;
}
