package org.jszip.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.IOUtil;
import org.jszip.pseudo.io.PseudoDirectoryScanner;
import org.jszip.pseudo.io.PseudoFile;
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
        final ContextFactory contextFactory = new ShellContextFactory();
        final Global global = new Global();
        global.initQuitAction(new QuitAction() {
            public void quit(Context context, int exitCode) {
                if (exitCode != 0) {
                    throw new JavaScriptTerminationException("Script exited with exit code of " + exitCode, exitCode);
                }
            }
        });
        if (!global.isInitialized()) {
            global.init(contextFactory);
        }

        final List<PseudoFileSystem.Layer> layers = buildVirtualFileSystemLayers();
        final Context context = contextFactory.enterContext();
        final PseudoFileSystem fs = new PseudoFileSystem(layers);
        try {
            context.setErrorReporter(new MavenLogErrorReporter(getLog()));
            context.putThreadLocal(Log.class, getLog());
            fs.installInContext();

            // look for files to compile

            PseudoDirectoryScanner scanner = new PseudoDirectoryScanner();

            scanner.setBasedir(fs.getPseudoFile("/virtual"));

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

            global.defineFunctionProperties(new String[]{"print", "debug", "warn", "quit", "readFile"},
                    GlobalFunctions.class,
                    ScriptableObject.DONTENUM);

            final Scriptable scope = GlobalFunctions.createPseudoFileSystemScope(global, context);

            List<String> modifiedFiles = new ArrayList<String>();
            for (String fileName : includedFiles) {
                if (!lessForceIfOlder) {
                    final PseudoFile dest = fs.getPseudoFile("/target/" + fileName.replaceFirst("\\.less$", ".css"));
                    if (dest.isFile()) {
                        final PseudoFile src = fs.getPseudoFile("/virtual/" + fileName);
                        if (src.lastModified() < dest.lastModified()) {
                            continue;
                        }
                    }
                }
                modifiedFiles.add(fileName);
            }

            if (lessCompress) {
                modifiedFiles.add(0, "-x");
            }
            Object[] args = modifiedFiles.toArray(new Object[modifiedFiles.size()]);
            Scriptable argsObj = context.newArray(global, args);
            global.defineProperty("arguments", argsObj, ScriptableObject.DONTENUM);

            // stub out some code to allow using less-rhino.js

            compileScript(context, "less-env.js", null, "/org/jszip/maven/less-env.js")
                    .exec(context, scope);

            // now load less-rhino.js

            compileScript(context, "less-rhino.js", customLessScript, "/org/jszip/maven/less-rhino.js")
                    .exec(context, scope);

            // now use our engine to process the LESS files

            global.defineProperty("showErrorExtracts", showErrorExtracts, ScriptableObject.DONTENUM);

            GlobalFunctions.setExitCode(0);

            compileScript(context, "less-engine.js", null, "/org/jszip/maven/less-engine.js")
                    .exec(context, scope);

            // check for errors

            final Integer exitCode = GlobalFunctions.getExitCode();
            if (lessFailOnError && exitCode != null && exitCode != 0) {
                throw new MojoFailureException("Compilation failure");
            }

        } finally {
            fs.removeFromContext();
            Context.exit();
            context.putThreadLocal(Log.class, null);
        }
    }

    private Script compileScript(Context context, String scriptName, File customScriptFile,
                                 String bundledScriptResource)
            throws MojoExecutionException {
        String source;
        int lineNo = 0;
        InputStream inputStream = null;
        InputStreamReader reader = null;
        try {
            if (customScriptFile != null && customScriptFile.isFile()) {
                getLog().debug("Using custom " + scriptName + " from: " + customScriptFile);
                inputStream = new FileInputStream(customScriptFile);
            } else {
                getLog().debug("Using bundled " + scriptName);
                inputStream = getClass().getResourceAsStream(bundledScriptResource);
            }
            source = IOUtil.toString(inputStream, "UTF-8");
            if (source.startsWith("#!")) {
                int i1 = source.indexOf('\n');
                int i2 = source.indexOf('\r');
                int index = (i1 == -1 || i2 == -1) ? Math.max(i1, i2) : Math.min(i1, i2);
                if (index > 0) {
                    source = source.substring(index);
                    lineNo++;
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            IOUtil.close(reader);
            IOUtil.close(inputStream);
        }
        return context.compileString(source, scriptName, lineNo, null);
    }
}
