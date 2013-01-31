package org.jszip.sass;

import org.codehaus.plexus.util.FileUtils;
import org.jruby.Ruby;
import org.jruby.embed.EmbedEvalUnit;
import org.jruby.embed.ScriptingContainer;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jszip.pseudo.io.PseudoFileSystem;

import java.io.File;

/**
 * @author stephenc
 * @since 31/01/2013 15:30
 */
public class SassEngine {

    private final PseudoFileSystem fs;
    private ScriptingContainer container;
    private final EmbedEvalUnit evalUnit;

    public SassEngine(PseudoFileSystem fs, String encoding) {
        this.fs = fs;
        this.container = new ScriptingContainer();
        this.container.put("filesystem", new PseudoFileSystemImporter(fs, encoding));
        this.container.put("filename", null);
        evalUnit = this.container.parse(getClass().getResourceAsStream("sass-engine.rb"), "sass-engine.rb");
    }

    public String toCSS(String name) {
        container.put("filename", name);
        return (String)JavaEmbedUtils.rubyToJava(evalUnit.run());

    }
}
