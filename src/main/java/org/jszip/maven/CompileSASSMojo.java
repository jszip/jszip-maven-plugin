package org.jszip.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.IOUtil;
import org.jruby.embed.EmbedEvalUnit;
import org.jruby.embed.ScriptingContainer;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jszip.css.CssCompilationError;
import org.jszip.css.CssEngine;
import org.jszip.pseudo.io.PseudoDirectoryScanner;
import org.jszip.pseudo.io.PseudoFile;
import org.jszip.pseudo.io.PseudoFileOutputStream;
import org.jszip.pseudo.io.PseudoFileSystem;
import org.jszip.rhino.GlobalFunctions;
import org.jszip.rhino.MavenLogErrorReporter;
import org.jszip.sass.SassEngine;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mojo(name = "compile-sass", defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class CompileSASSMojo extends AbstractPseudoFileSystemProcessorMojo {

    /**
     * Skip compilation.
     */
    @Parameter(property = "jszip.sass.skip", defaultValue = "false")
    private boolean sassSkip;

    /**
     * Force compilation even if the source Sass file is older than the destination CSS file.
     */
    @Parameter(property = "jszip.sass.forceIfOlder", defaultValue = "false")
    private boolean sassForceIfOlder;

    /**
     * Indicates whether the build will continue even if there are compilation errors.
     */
    @Parameter(property = "jszip.sass.failOnError", defaultValue = "true")
    private boolean sassFailOnError;

    /**
     * A list of &lt;include&gt; elements specifying the sass files (by pattern) that should be included in
     * processing.
     */
    @Parameter
    private List<String> sassIncludes;

    /**
     * A list of &lt;exclude&gt; elements specifying the sass files (by pattern) that should be excluded from
     * processing.
     */
    @Parameter
    private List<String> sassExcludes;

    /**
     * The character encoding scheme to be applied when reading SASS files.
     */
    @Parameter( defaultValue = "${project.build.sourceEncoding}" )
    private String encoding;

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (sassSkip) {
            getLog().info("SASS compilation skipped.");
            return;
        }
        if (webappDirectory.isFile()) {
            throw new MojoExecutionException("Webapp directory '" + webappDirectory + "' is not a directory");
        }
        if (!webappDirectory.isDirectory()) {
            getLog().info("Webapp directory '" + webappDirectory + " does not exist. Nothing to do.");
            return;
        }
        final List<PseudoFileSystem.Layer> layers = buildVirtualFileSystemLayers();
        final PseudoFileSystem fs = new PseudoFileSystem(layers);
        try {
            CssEngine engine = new SassEngine(fs, encoding == null ? "utf-8" : encoding);

            // look for files to compile

            PseudoDirectoryScanner scanner = new PseudoDirectoryScanner();

            scanner.setFileSystem(fs);

            scanner.setBasedir(fs.getPseudoFile("/virtual"));

            if (sassIncludes != null && !sassIncludes.isEmpty()) {
                scanner.setIncludes(processIncludesExcludes(sassIncludes));
            } else {
                scanner.setIncludes(new String[]{"**/*.sass","**/*.scss"});
            }

            if (sassExcludes != null && !sassExcludes.isEmpty()) {
                scanner.setExcludes(processIncludesExcludes(sassExcludes));
            } else {
                scanner.setExcludes(new String[]{"**/_*.sass","**/_*.scss"});
            }

            scanner.scan();

            final List<String> includedFiles = new ArrayList<String>(Arrays.asList(scanner.getIncludedFiles()));
            getLog().debug("Files to compile: " + includedFiles);

            for (String fileName : includedFiles) {
                final PseudoFile dest = fs.getPseudoFile("/target/" + engine.mapName(fileName));
                if (!sassForceIfOlder) {
                    if (dest.isFile()) {
                        final PseudoFile src = fs.getPseudoFile("/virtual/" + fileName);
                        if (src.lastModified() < dest.lastModified()) {
                            continue;
                        }
                    }
                }
                if (!dest.getParentFile().isDirectory()) {
                    dest.getParentFile().mkdirs();
                }

                final String css = engine.toCSS("/virtual/" + fileName);
                PseudoFileOutputStream fos = null;
                try {
                    fos = new PseudoFileOutputStream(dest);
                    IOUtil.copy(css, fos);
                } catch (IOException e) {
                    throw new MojoFailureException("Could not write CSS file produced from " + fileName, e);
                } finally {
                    IOUtil.close(fos);
                }
            }
        } catch (CssCompilationError e) {
            throw new MojoFailureException("Compilation failure: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not instantiate compiler: " + e.getMessage(), e);
        }
    }
}
