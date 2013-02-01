package org.jszip.sass;

import org.codehaus.plexus.util.FileUtils;
import org.jruby.embed.EmbedEvalUnit;
import org.jruby.embed.ParseFailedException;
import org.jruby.embed.ScriptingContainer;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jszip.css.CssEngine;
import org.jszip.pseudo.io.PseudoFileSystem;

import java.io.IOException;

/**
 * @author stephenc
 * @since 31/01/2013 15:30
 */
public class SassEngine implements CssEngine {

    private final PseudoFileSystem fs;
    private ScriptingContainer container;
    private final EmbedEvalUnit evalUnit;

    public SassEngine(PseudoFileSystem fs, String encoding) throws IOException {
        this.fs = fs;
        this.container = new ScriptingContainer();
        this.container.put("filesystem", new PseudoFileSystemImporter(fs, encoding));
        this.container.put("filename", null);
        try {
            evalUnit = this.container.parse(getClass().getResourceAsStream("sass-engine.rb"), "sass-engine.rb");
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
        container.put("filename", name);
        return (String) JavaEmbedUtils.rubyToJava(evalUnit.run());

    }
}
