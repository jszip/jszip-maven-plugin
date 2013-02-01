package org.jszip.less;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.jszip.css.CssCompilationError;
import org.jszip.css.CssEngine;
import org.jszip.pseudo.io.PseudoFileSystem;
import org.jszip.rhino.GlobalFunctions;
import org.jszip.rhino.JavaScriptTerminationException;
import org.jszip.rhino.MavenLogErrorReporter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
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

/**
 * @author stephenc
 * @since 31/01/2013 23:43
 */
public class LessEngine implements CssEngine {

    private final PseudoFileSystem fs;
    private final ContextFactory contextFactory;
    private final Global global;
    private final Scriptable scope;
    private final Log log;
    private final boolean lessCompress;
    private final boolean showErrorExtracts;
    private final Function function;
    private final String encoding;

    public LessEngine(PseudoFileSystem fs, String encoding, Log log, boolean lessCompress, File customLessScript,
                      boolean showErrorExtracts) throws IOException {
        this.fs = fs;
        this.encoding = encoding;
        this.lessCompress = lessCompress;
        this.showErrorExtracts = showErrorExtracts;
        this.contextFactory = new ShellContextFactory();
        this.global = new Global();
        this.log = log;
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
        global.defineFunctionProperties(new String[]{"print", "debug", "warn", "quit", "readFile"},
                GlobalFunctions.class,
                ScriptableObject.DONTENUM);
        final Context context = contextFactory.enterContext();
        try {
            context.setErrorReporter(new MavenLogErrorReporter(log));
            context.putThreadLocal(Log.class, log);
            global.defineProperty("arguments", new Object[0], ScriptableObject.DONTENUM);
            scope = GlobalFunctions.createPseudoFileSystemScope(global, context);

            compileScript(context, "less-env.js", null, "/org/jszip/less/less-env.js")
                    .exec(context, scope);

            // now load less-rhino.js

            compileScript(context, "less-rhino.js", customLessScript, "/org/jszip/less/less-rhino.js")
                    .exec(context, scope);

            global.defineProperty("showErrorExtracts", showErrorExtracts, ScriptableObject.DONTENUM);

            compileScript(context, "less-engine.js", null, "/org/jszip/less/less-engine.js")
                    .exec(context, scope);

            function = (Function) scope.get("engine", scope);

        } finally {
            fs.removeFromContext();
            Context.exit();
            context.putThreadLocal(Log.class, null);
        }
    }

    public String mapName(String sourceFileName) {
        return sourceFileName.replaceFirst("\\.[lL][eE][sS][sS]$", ".css");
    }

    public String toCSS(String name) throws CssCompilationError {

        final Context context = contextFactory.enterContext();
        try {
            context.setErrorReporter(new MavenLogErrorReporter(log));
            context.putThreadLocal(Log.class, log);
            fs.installInContext();

            GlobalFunctions.setExitCode(0);

            final String result =
                    (String) function.call(context, scope, scope, new Object[]{name, encoding, lessCompress});

            // check for errors

            final Integer exitCode = GlobalFunctions.getExitCode();
            if (exitCode != 0) {
                throw new CssCompilationError(name, -1, -1);
            }
            return result;
        } catch (JavaScriptException e) {
            if (e.getValue() instanceof Scriptable) {
                Scriptable jse = (Scriptable) e.getValue();
                int line = jse.has("line", jse) ? ((Number) jse.get("line", jse)).intValue() : -1;
                int col = jse.has("col", jse) ? ((Number) jse.get("col", jse)).intValue() : -1;
                throw new CssCompilationError(name, line, col, e);
            }
            throw new CssCompilationError(name, -1, -1, e);
        } finally {
            fs.removeFromContext();
            Context.exit();
            context.putThreadLocal(Log.class, null);
        }
    }

    private Script compileScript(Context context, String scriptName, File customScriptFile,
                                 String bundledScriptResource) throws IOException {
        String source;
        int lineNo = 0;
        InputStream inputStream = null;
        InputStreamReader reader = null;
        try {
            if (customScriptFile != null && customScriptFile.isFile()) {
                log.debug("Using custom " + scriptName + " from: " + customScriptFile);
                inputStream = new FileInputStream(customScriptFile);
            } else {
                log.debug("Using bundled " + scriptName);
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
        } finally {
            IOUtil.close(reader);
            IOUtil.close(inputStream);
        }
        return context.compileString(source, scriptName, lineNo, null);
    }

}
