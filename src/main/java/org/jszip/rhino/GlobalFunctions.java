package org.jszip.rhino;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.IOUtil;
import org.jszip.pseudo.io.ProxyPseudoFile;
import org.jszip.pseudo.io.PseudoFile;
import org.jszip.pseudo.io.PseudoFileInputStream;
import org.jszip.pseudo.io.PseudoFileOutputStream;
import org.jszip.pseudo.io.PseudoFileReader;
import org.jszip.pseudo.io.PseudoFileSystem;
import org.jszip.pseudo.io.PseudoFileWriter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeJavaPackage;
import org.mozilla.javascript.NativeJavaTopPackage;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.tools.shell.Global;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author stephenc
 * @since 29/01/2013 22:51
 */
public class GlobalFunctions {
    /**
     * Print the string values of its arguments.
     * <p/>
     * This method is defined as a JavaScript function. Note that its arguments
     * are of the "varargs" form, which allows it to handle an arbitrary number
     * of arguments supplied to the JavaScript function.
     */
    public static void print(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                builder.append(" ");
            }

            // Convert the arbitrary JavaScript value into a string form.
            String s = Context.toString(args[i]);

            builder.append(s);
        }
        Log log = (Log) cx.getThreadLocal(Log.class);
        if (log != null) {
            for (String line : builder.toString().split("(\\r\\n?)|(\\n\\r?)")) {
                log.info(line);
            }
        }
    }

    /**
     * Print the string values of its arguments.
     * <p/>
     * This method is defined as a JavaScript function. Note that its arguments
     * are of the "varargs" form, which allows it to handle an arbitrary number
     * of arguments supplied to the JavaScript function.
     */
    public static void debug(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                builder.append(" ");
            }

            // Convert the arbitrary JavaScript value into a string form.
            String s = Context.toString(args[i]);

            builder.append(s);
        }
        Log log = (Log) cx.getThreadLocal(Log.class);
        if (log != null) {
            for (String line : builder.toString().split("(\\r\\n?)|(\\n\\r?)")) {
                log.debug(line);
            }
        }
    }

    /**
     * Print the string values of its arguments.
     * <p/>
     * This method is defined as a JavaScript function. Note that its arguments
     * are of the "varargs" form, which allows it to handle an arbitrary number
     * of arguments supplied to the JavaScript function.
     */
    public static void warn(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                builder.append(" ");
            }

            // Convert the arbitrary JavaScript value into a string form.
            String s = Context.toString(args[i]);

            builder.append(s);
        }
        Log log = (Log) cx.getThreadLocal(Log.class);
        if (log != null) {
            for (String line : builder.toString().split("(\\r\\n?)|(\\n\\r?)")) {
                log.warn(line);
            }
        }
    }

    /**
     * Print the string values of its arguments.
     * <p/>
     * This method is defined as a JavaScript function. Note that its arguments
     * are of the "varargs" form, which allows it to handle an arbitrary number
     * of arguments supplied to the JavaScript function.
     */
    public static void quit(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        final int exitCode = args.length == 0 ? 0 : ScriptRuntime.toInt32(args[0]);
        final Log log = (Log) cx.getThreadLocal(Log.class);
        cx.putThreadLocal(ExitCodeHolder.class, new ExitCodeHolder(exitCode));
        if (exitCode > 0) {
            if (log != null) {
                log.debug("Script exit code = " + exitCode);
            }
        } else {
            if (log != null) {
                log.debug("Script exit code = " + exitCode);
            }
        }

    }

    /**
     * The readFile reads the given file content and convert it to a string
     * using the specified character coding or default character coding if
     * explicit coding argument is not given.
     * <p>
     * Usage:
     * <pre>
     * readFile(filePath)
     * readFile(filePath, charCoding)
     * </pre>
     * The first form converts file's context to string using the default
     * character coding.
     */
    public static Object readFile(Context cx, Scriptable thisObj, Object[] args,
                                  Function funObj)
        throws IOException
    {
        if (args.length == 0) {
            throw Context.reportRuntimeError("Bad arguments supplied to readFile()");
        }
        String path = ScriptRuntime.toString(args[0]);
        String charCoding = null;
        if (args.length >= 2) {
            charCoding = ScriptRuntime.toString(args[1]);
        }

        final PseudoFile file = PseudoFileSystem.current().getPseudoFile(path);
        InputStream inputStream = null;
        try {
            inputStream = new PseudoFileInputStream(file);
            return charCoding == null ? IOUtil.toString(inputStream) : IOUtil.toString(inputStream, charCoding);
        } finally {
            IOUtil.close(inputStream);
        }
    }


    public static void setExitCode(int exitCode) {
        Context.getCurrentContext().putThreadLocal(ExitCodeHolder.class, new ExitCodeHolder(exitCode));
    }

    public static Integer getExitCode() {
        final Context context = Context.getCurrentContext();
        if (context != null) {
            ExitCodeHolder result = (ExitCodeHolder) context.getThreadLocal(ExitCodeHolder.class);
            context.putThreadLocal(GlobalFunctions.ExitCodeHolder.class, null);
            return result == null ? null : result.getExitCode();
        }
        return null;
    }

    public static Scriptable createPseudoFileSystemScope(Global global, Context context) {
        Scriptable scope = context.newObject(global);
        scope.setPrototype(global);
        scope.setParentScope(null);

        NativeJavaTopPackage $packages = (NativeJavaTopPackage) global.get("Packages");
        NativeJavaPackage $java = (NativeJavaPackage) $packages.get("java");
        NativeJavaPackage $java_io = (NativeJavaPackage) $java.get("io");

        ProxyNativeJavaPackage proxy$java = new ProxyNativeJavaPackage($java);
        ProxyNativeJavaPackage proxy$java_io = new ProxyNativeJavaPackage($java_io);
        proxy$java_io.put("File", global, get(global, "Packages." + ProxyPseudoFile.class.getName()));
        proxy$java_io.put("FileInputStream", global,
                get(global, "Packages." + PseudoFileInputStream.class.getName()));
        proxy$java_io.put("FileOutputStream", global,
                get(global, "Packages." + PseudoFileOutputStream.class.getName()));
        proxy$java_io.put("FileReader", global,
                get(global, "Packages." + PseudoFileReader.class.getName()));
        proxy$java_io.put("FileWriter", global,
                get(global, "Packages." + PseudoFileWriter.class.getName()));
        proxy$java.put("io", global, proxy$java_io);
        global.defineProperty("java", proxy$java, ScriptableObject.DONTENUM);
        return scope;
    }

    public static Object get(Scriptable scope, String name) {
        Scriptable cur = scope;
        for (String part : StringUtils.split(name, ".")) {
            Object next = cur.get(part, scope);
            if (next instanceof Scriptable) {
                cur = (Scriptable) next;
            } else {
                return null;
            }
        }
        return cur;
    }

    private static class ExitCodeHolder {
        private final int exitCode;

        private ExitCodeHolder(int exitCode) {
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return exitCode;
        }

    }


}
