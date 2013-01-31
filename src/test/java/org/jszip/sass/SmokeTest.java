package org.jszip.sass;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.jruby.Ruby;
import org.jruby.embed.EmbedEvalUnit;
import org.jruby.embed.ScriptingContainer;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jszip.pseudo.io.PseudoFileSystem;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

/**
 * @author stephenc
 * @since 31/01/2013 09:34
 */
public class SmokeTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    public String loadResource(String name) throws IOException {
        InputStream stream = null;
        try {
            stream = getClass().getResourceAsStream(name);
            return IOUtil.toString(stream);
        } finally {
            IOUtil.close(stream);
        }
    }

    @Test
    public void smokes() throws IOException {
        ScriptingContainer container = new ScriptingContainer();
        final Ruby runtime = container.getProvider().getRuntime();
        final PseudoFileSystem fs = new PseudoFileSystem(new PseudoFileSystem.FileLayer(folder.getRoot()));
        FileUtils.fileWrite(new File(folder.getRoot(), "foo.scss"), "utf-8", loadResource("foo.scss"));
        FileUtils.fileWrite(new File(folder.getRoot(), "bar.sass"), "utf-8", loadResource("bar.sass"));
        container.put("filesystem", new PseudoFileSystemImporter(fs, "utf-8"));
        container.put("filename", "/foo.scss");
        final EmbedEvalUnit evalUnit = container.parse(getClass().getResourceAsStream("sass-engine.rb"), "sass-engine.rb");
        assertThat(JavaEmbedUtils.rubyToJava(evalUnit.run()).toString(), containsString("8px"));
    }

    @Test
    public void engine() throws IOException {
        final PseudoFileSystem fs = new PseudoFileSystem(new PseudoFileSystem.FileLayer(folder.getRoot()));
        FileUtils.fileWrite(new File(folder.getRoot(), "foo.scss"), "utf-8", loadResource("foo.scss"));
        FileUtils.fileWrite(new File(folder.getRoot(), "bar.sass"), "utf-8", loadResource("bar.sass"));
        SassEngine engine = new SassEngine(fs, "utf-8");
        assertThat(engine.toCSS("/foo.scss"), containsString("8px"));
    }
}
