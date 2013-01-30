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

package org.jszip.rhino;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.FileUtils;
import org.jszip.pseudo.io.PseudoFile;
import org.jszip.pseudo.io.PseudoFileSystem;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.UniqueTag;
import org.mozilla.javascript.tools.shell.Global;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

/**
 * An action for running r.js against a virtual filesystem.
 */
public class OptimizeContextAction extends ScriptableObject implements ContextAction {
    private final Global global;
    private final File profileJs;
    private final String source;
    private final int lineNo;
    private final PseudoFileSystem.Layer[] layers;
    private final Log log;

    public OptimizeContextAction(Log log, Global global, File profileJs, String source, int lineNo,
                                 PseudoFileSystem.Layer... layers) {
        this.log = log;
        this.global = global;
        this.profileJs = profileJs;
        this.source = source;
        this.lineNo = lineNo;
        this.layers = layers;
    }

    public Object run(Context context) {
        context.setErrorReporter(new MavenLogErrorReporter(log));
        PseudoFileSystem fileSystem = new PseudoFileSystem(layers);
        context.putThreadLocal(Log.class, log);
        fileSystem.installInContext();
        try {

            if (log.isDebugEnabled()) {
                log.debug("Virtual filesystem exposed to r.js:");
                Stack<Iterator<PseudoFile>> stack = new Stack<Iterator<PseudoFile>>();
                stack.push(Arrays.asList(fileSystem.root().listFiles()).iterator());
                while (!stack.isEmpty()) {
                    Iterator<PseudoFile> iterator = stack.pop();
                    while (iterator.hasNext()) {
                        PseudoFile f = iterator.next();
                        if (f.isFile()) {
                            log.debug("  " + f.getAbsolutePath() + " [file]");
                        } else {
                            log.debug("  " + f.getAbsolutePath() + " [dir]");
                            stack.push(iterator);
                            iterator = Arrays.asList(f.listFiles()).iterator();
                        }
                    }
                }
            }

            List<String> argsList = new ArrayList<String>();
            argsList.add("-o");
            argsList.add("/build/" + profileJs.getName());
            String appDir = null;
            String baseUrl = "./";
            String dir = null;
            try {
                String profile = FileUtils.fileRead(profileJs, "UTF-8");
                Scriptable scope = context.newObject(global);
                scope.setPrototype(global);
                scope.setParentScope(null);
                Object parsedProfile = context.evaluateString(scope, profile, profileJs.getName(), 0, null);
                if (parsedProfile instanceof Scriptable) {
                    final Scriptable scriptable = (Scriptable) parsedProfile;
                    appDir = getStringWithDefault(scriptable, "appDir", null);
                    baseUrl = getStringWithDefault(scriptable, "baseUrl", "./");
                    dir = getStringWithDefault(scriptable, "dir", null);
                }
            } catch (IOException e) {
                log.debug("Cannot infer profile fixups", e);
            } catch (JavaScriptException e) {
                log.warn("JavaScript exception while parsing " + profileJs.getAbsolutePath() + ": " + e.details());
            } catch (Throwable e) {
                log.warn("Cannot infer if profile needs appDir and dir remapping to virtual directory structure", e);
            }
            if (appDir == null) {
                argsList.add("appDir=/virtual/");
                argsList.add("baseUrl=" + baseUrl);
            } else if (!appDir.startsWith("/virtual/") && !appDir.equals("/virtual")) {
                argsList.add("appDir=/virtual/" + StringUtils.removeEnd(StringUtils.removeStart(appDir, "/"),"/")+"/");
                argsList.add("baseUrl=" + baseUrl);
            }
            if (dir == null) {
                argsList.add("dir=/target/");
            } else if (!dir.startsWith("/target/") && !dir.equals("/target")) {
                argsList.add("dir=/target/" + StringUtils.removeEnd(StringUtils.removeStart(dir, "/"),"/")+"/");
            }

            global.defineFunctionProperties(new String[]{"print", "quit"}, GlobalFunctions.class,
                    ScriptableObject.DONTENUM);

            Script script = context.compileString(source, "r.js", lineNo, null);
            script.getClass();

            Scriptable argsObj = context.newArray(global, argsList.toArray());
            global.defineProperty("arguments", argsObj, ScriptableObject.DONTENUM);

            Scriptable scope = GlobalFunctions.createPseudoFileSystemScope(global, context);

            log.info("Applying r.js profile " + profileJs.getPath());
            log.debug("Executing r.js with arguments: " + StringUtils.join(argsList, " "));
            GlobalFunctions.setExitCode(0);
            script.exec(context, scope);
            return GlobalFunctions.getExitCode();
        } finally {
            fileSystem.removeFromContext();
            context.putThreadLocal(OptimizeContextAction.class, null);
        }
    }

    private String getStringWithDefault(Scriptable scriptable, String name, String defaultValue) {
        final Object object = scriptable.get(name, scriptable);
        if (object instanceof String) {
            return (String) object;
        }
        if (object instanceof UniqueTag) {
            if (object == UniqueTag.NULL_VALUE) {
                return null;
            }
            return defaultValue;
        } else {
            return object.toString();
        }
    }

    @Override
    public String getClassName() {
        return "global";
    }

}
