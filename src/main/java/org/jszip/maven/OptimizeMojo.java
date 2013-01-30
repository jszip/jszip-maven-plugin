/*
 * Copyright 2011-2012 Stephen Connolly.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jszip.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.IOUtil;
import org.jszip.pseudo.io.PseudoFileSystem;
import org.jszip.rhino.JavaScriptTerminationException;
import org.jszip.rhino.OptimizeContextAction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.tools.shell.Global;
import org.mozilla.javascript.tools.shell.QuitAction;
import org.mozilla.javascript.tools.shell.ShellContextFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the r.js optimizer over the source
 */
@Mojo(name = "optimize", defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class OptimizeMojo extends AbstractPseudoFileSystemProcessorMojo {

    /**
     * Regex for a quoted string which may include escapes.
     */
    public static final String QUOTED_STRING_WITH_ESCAPES = "'([^\\\\']+|\\\\([btnfr\"'\\\\]|[0-3]?[0-7]{1,"
            + "2}|u[0-9a-fA-F]{4}))*'|\"([^\\\\\"]+|\\\\([btnfr\"'\\\\]|[0-3]?[0-7]{1,2}|u[0-9a-fA-F]{4}))*\"";

    /**
     * Regex for sniffing the version of r.js being used.
     */
    public static final String R_JS_VERSION_REGEX = "\\s+version\\s*=\\s*(" + QUOTED_STRING_WITH_ESCAPES + ")";

    /**
     * Directory containing the build profiles.
     */
    @Parameter(defaultValue = "src/build/js", required = true)
    private File contentDirectory;

    /**
     * Directory containing the build profiles.
     */
    @Parameter(defaultValue = "src/build/js/r.js")
    private File customRScript;

    /**
     * Skip optimization.
     */
    @Parameter(property = "jszip.optimize.skip", defaultValue = "false")
    private boolean skip;

    /**
     * A list of &lt;include&gt; elements specifying the build profiles (by pattern) that should be included in
     * optimization.
     */
    @Parameter(property = "includes")
    private List<String> includes;

    /**
     * A list of &lt;exclude&gt; elements specifying the build profiles (by pattern) that should be excluded from
     * optimization.
     */
    @Parameter(property = "excludes")
    private List<String> excludes;

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Optimization skipped.");
            return;
        }
        if (!contentDirectory.exists()) {
            getLog().info("Nothing to do, no r.js build profiles in " + contentDirectory);
            return;
        }
        if (!contentDirectory.isDirectory()) {
            throw new MojoExecutionException("Build profile directory '" + contentDirectory + "' is not a directory");
        }
        if (webappDirectory.isFile()) {
            throw new MojoExecutionException("Webapp directory '" + webappDirectory + "' is not a directory");
        }
        if (!webappDirectory.isDirectory() && !webappDirectory.mkdirs()) {
            throw new MojoExecutionException("Could not create Webapp directory '" + webappDirectory + "'");
        }
        String source;
        int lineNo = 0;
        InputStream inputStream = null;
        InputStreamReader reader = null;
        try {
            if (customRScript.isFile()) {
                getLog().debug("Using custom r.js from: " + customRScript);
                inputStream = new FileInputStream(customRScript);
            } else {
                getLog().debug("Using bundled r.js");
                inputStream = getClass().getResourceAsStream("/org/jszip/maven/r.js");
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

        String sourceVersion = "unknown";
        Pattern rJsVersionPattern = Pattern.compile(R_JS_VERSION_REGEX);
        Matcher rJsVersionMatcher = rJsVersionPattern.matcher(source);
        if (rJsVersionMatcher.find()) {
            sourceVersion = rJsVersionMatcher.group(1);
        }

        getLog().info("Using r.js version " + sourceVersion);

        List<PseudoFileSystem.Layer> layers = buildVirtualFileSystemLayers();

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
        DirectoryScanner scanner = new DirectoryScanner();

        scanner.setBasedir(contentDirectory);

        if (includes != null && !includes.isEmpty()) {
            scanner.setIncludes(processIncludesExcludes(includes));
        } else {
            scanner.setIncludes(new String[]{"**/*.js"});
        }

        if (excludes != null && !excludes.isEmpty()) {
            scanner.setExcludes(processIncludesExcludes(excludes));
        } else {
            scanner.setExcludes(new String[]{"r.js"});
        }

        scanner.scan();

        for (String path : scanner.getIncludedFiles()) {
            File profileJs = new File(contentDirectory, path);
            PseudoFileSystem.Layer[] layersArray = layers.toArray(new PseudoFileSystem.Layer[layers.size() + 1]);
            layersArray[layers.size()] = new PseudoFileSystem.FileLayer("build", profileJs.getParentFile());
            try {
                Object rv = contextFactory
                        .call(new OptimizeContextAction(getLog(), global, profileJs, source, lineNo, layersArray));
                if (rv instanceof Number) {
                    if (((Number) rv).intValue() != 0) {
                        throw new MojoExecutionException(
                                "Non-zero exit code of " + ((Number) rv).intValue()
                                        + " when trying to optimize profile " + profileJs);
                    }
                }
            } catch (JavaScriptException e) {
                throw new MojoExecutionException(
                        "Uncaught exception when trying to optimize profile " + profileJs, e);
            } catch (JavaScriptTerminationException e) {
                throw new MojoExecutionException(
                        "Non-zero exit code of " + e.getExitCode() + " when trying to optimize profile " + profileJs);
            }
        }
    }

}
