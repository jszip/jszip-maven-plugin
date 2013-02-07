package org.jszip.sass;

import org.codehaus.plexus.util.FileUtils;
import org.jruby.embed.EmbedEvalUnit;
import org.jruby.embed.ParseFailedException;
import org.jruby.embed.ScriptingContainer;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jszip.css.CssCompilationError;
import org.jszip.css.CssEngine;
import org.jszip.pseudo.io.PseudoFileSystem;

import java.io.IOException;

/**
 * @author stephenc
 * @since 31/01/2013 15:30
 */
public class SassEngine implements CssEngine {

    private final PseudoFileSystem fs;
    private final RubyProxy proxy;
    private final PseudoFileSystemImporter fileSystemImporter;
    private final ScriptingContainer container;

    public SassEngine(PseudoFileSystem fs, String encoding) throws IOException {
        this.fs = fs;
        this.container = new ScriptingContainer();
        fileSystemImporter = new PseudoFileSystemImporter(fs, encoding);
        try {
            Object reciever = this.container.runScriptlet(getClass().getResourceAsStream("sass-engine.rb"), "sass-engine.rb");
            proxy = this.container.getInstance(reciever, RubyProxy.class);
        } catch (ParseFailedException e) {
            final IOException ioe = new IOException(e.getMessage());
            ioe.initCause(e);
            throw ioe;
        }
    }

    public String mapName(String sourceFileName) {
        return sourceFileName.replaceFirst("\\.[sS][aAcC][sS][sS]$", ".css");
    }

    public String toCSS(String name) {
        return proxy.toCSS(fileSystemImporter, name);
    }

    public static interface RubyProxy {
        String toCSS(PseudoFileSystemImporter importer, String name);
    }
}
