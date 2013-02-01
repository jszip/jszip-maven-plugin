package org.jszip.less;

import org.apache.maven.monitor.logging.DefaultLog;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.jruby.Ruby;
import org.jruby.embed.EmbedEvalUnit;
import org.jruby.embed.ScriptingContainer;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jszip.css.CssCompilationError;
import org.jszip.pseudo.io.PseudoFileSystem;
import org.jszip.sass.PseudoFileSystemImporter;
import org.jszip.sass.SassEngine;
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
 * @since 01/02/2013 00:05
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
    public void engine() throws IOException, CssCompilationError {
        final PseudoFileSystem fs = new PseudoFileSystem(new PseudoFileSystem.FileLayer(folder.getRoot()));
        FileUtils.fileWrite(new File(folder.getRoot(), "foo.less"), "utf-8", loadResource("foo.less"));
        FileUtils.fileWrite(new File(folder.getRoot(), "bar.less"), "utf-8", loadResource("bar.less"));
        LessEngine engine = new LessEngine(fs, "utf-8", new DefaultLog(new ConsoleLogger()), false, null, false);
        assertThat(engine.toCSS("/foo.less"), containsString("8px"));
    }

}
