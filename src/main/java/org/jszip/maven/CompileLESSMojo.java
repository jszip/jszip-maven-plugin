package org.jszip.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.IOUtil;
import org.jszip.css.CssCompilationError;
import org.jszip.css.CssEngine;
import org.jszip.less.LessEngine;
import org.jszip.pseudo.io.PseudoDirectoryScanner;
import org.jszip.pseudo.io.PseudoFile;
import org.jszip.pseudo.io.PseudoFileOutputStream;
import org.jszip.pseudo.io.PseudoFileSystem;
import org.jszip.rhino.GlobalFunctions;
import org.jszip.rhino.JavaScriptTerminationException;
import org.jszip.rhino.MavenLogErrorReporter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.tools.shell.Global;
import org.mozilla.javascript.tools.shell.QuitAction;
import org.mozilla.javascript.tools.shell.ShellContextFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mojo(name = "compile-less", defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class CompileLESSMojo extends AbstractPseudoFileSystemProcessorMojo {

    /**
     * Directory containing the less processor.
     */
    @Parameter(defaultValue = "src/build/js/less-rhino.js")
    private File customLessScript;

    /**
     * Skip compilation.
     */
    @Parameter(property = "jszip.less.skip", defaultValue = "false")
    private boolean lessSkip;

    /**
     * Force compilation even if the source LESS file is older than the destination CSS file.
     */
    @Parameter(property = "jszip.less.forceIfOlder", defaultValue = "false")
    private boolean lessForceIfOlder;

    /**
     * Compress CSS.
     */
    @Parameter(property = "jszip.less.compress", defaultValue = "true")
    private boolean lessCompress;

    /**
     * Indicates whether the build will continue even if there are compilation errors.
     */
    @Parameter(property = "jszip.less.failOnError", defaultValue = "true")
    private boolean lessFailOnError;

    /**
     * Indicates whether to show extracts of the code where errors occur.
     */
    @Parameter(property = "jszip.less.showErrorExtracts", defaultValue = "false")
    private boolean showErrorExtracts;

    /**
     * A list of &lt;include&gt; elements specifying the less files (by pattern) that should be included in
     * processing.
     */
    @Parameter
    private List<String> lessIncludes;

    /**
     * A list of &lt;exclude&gt; elements specifying the less files (by pattern) that should be excluded from
     * processing.
     */
    @Parameter
    private List<String> lessExcludes;

    /**
     * The character encoding scheme to be applied when reading SASS files.
     */
    @Parameter( defaultValue = "${project.build.sourceEncoding}" )
    private String encoding;

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (lessSkip) {
            getLog().info("LESS compilation skipped.");
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
        Context.enter();
        try {
            CssEngine engine = new LessEngine(fs, encoding == null ? "utf-8" : encoding, getLog(), lessCompress, customLessScript, showErrorExtracts);
            fs.installInContext();

            // look for files to compile

            PseudoDirectoryScanner scanner = new PseudoDirectoryScanner();

            scanner.setBasedir(PseudoFileSystem.current().getPseudoFile("/virtual"));

            if (lessIncludes != null && !lessIncludes.isEmpty()) {
                scanner.setIncludes(processIncludesExcludes(lessIncludes));
            } else {
                scanner.setIncludes(new String[]{"**/*.less"});
            }

            if (lessExcludes != null && !lessExcludes.isEmpty()) {
                scanner.setExcludes(processIncludesExcludes(lessExcludes));
            } else {
                scanner.setExcludes(new String[0]);
            }

            scanner.scan();

            final List<String> includedFiles = new ArrayList<String>(Arrays.asList(scanner.getIncludedFiles()));
            getLog().debug("Files to compile: " + includedFiles);

            for (String fileName : includedFiles) {
                final PseudoFile dest = fs.getPseudoFile("/target/" + fileName.replaceFirst("\\.less$", ".css"));
                if (!lessForceIfOlder) {
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
        } finally {
            fs.removeFromContext();
            Context.exit();
        }
    }
}
