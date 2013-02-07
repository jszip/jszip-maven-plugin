package org.jszip.jetty;

import org.codehaus.plexus.util.FileUtils;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.Resource;
import org.jruby.embed.EvalFailedException;
import org.jszip.css.CssCompilationError;
import org.jszip.css.CssEngine;
import org.jszip.pseudo.io.PseudoFileSystem;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * @author stephenc
 * @since 01/02/2013 09:38
 */
public class CssEngineResource extends Resource {

    private final CssEngine engine;

    private final PseudoFileSystem fs;

    private final String sourceFilename;

    private final String name;

    public CssEngineResource(PseudoFileSystem fs, CssEngine engine, String sourceFilename) {
        this.fs = fs;
        this.engine = engine;
        this.sourceFilename = sourceFilename;
        this.name = FileUtils.filename(engine.mapName(sourceFilename));
    }

    @Override
    public boolean isContainedIn(Resource r) throws MalformedURLException {
        return false;
    }

    @Override
    public void release() {
    }

    @Override
    public boolean exists() {
        return fs.getPseudoFile(sourceFilename).exists();
    }

    @Override
    public boolean isDirectory() {
        return fs.getPseudoFile(sourceFilename).isDirectory();
    }

    @Override
    public long lastModified() {
        return fs.getPseudoFile(sourceFilename).lastModified();
    }

    @Override
    public long length() {
        try {
            return engine.toCSS(sourceFilename).getBytes("utf-8").length;
        } catch (Throwable t) {
            return -1;
        }
    }

    @Override
    public URL getURL() {
        try {
            return new URL("css-engine", null, -1, sourceFilename, new CssEngineURLStreamHandler());
        } catch (MalformedURLException e) {
            throw new IllegalStateException(
                    "MalformedURLException should not be thrown when a URLStreamHandler is provided");
        }
    }

    @Override
    public File getFile() throws IOException {
        return null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        try {
            return new ByteArrayInputStream(engine.toCSS(sourceFilename).getBytes("utf-8"));
        } catch (CssCompilationError e) {
            final IOException ioe = new IOException(e.getMessage());
            ioe.initCause(e);
            throw ioe;
        }
    }

    @Override
    public OutputStream getOutputStream() throws IOException, SecurityException {
        return null;
    }

    @Override
    public boolean delete() throws SecurityException {
        return false;
    }

    @Override
    public boolean renameTo(Resource dest) throws SecurityException {
        return false;
    }

    @Override
    public String[] list() {
        return null;
    }

    @Override
    public Resource addPath(String path) throws IOException, MalformedURLException {
        if (path == null) {
            throw new MalformedURLException();
        }
        if (path.length() == 0 || URIUtil.SLASH.equals(path)) {
            return this;
        }
        if (name.equals(path) || (URIUtil.SLASH + name).equals(path)) {
            return this;
        }
        return new BadResource();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CssEngineResource that = (CssEngineResource) o;

        if (!sourceFilename.equals(that.sourceFilename)) {
            return false;
        }
        if (!fs.getPseudoFile(sourceFilename).equals(that.fs.getPseudoFile(sourceFilename))) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("CssEngineResource");
        sb.append("{sourceFilename='").append(sourceFilename).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return sourceFilename.hashCode();
    }

    /**
     * In order to ensure that we can create URLs with the {@code virtual:} protocol, we need to provide a dummy
     * {@link java.net.URLStreamHandler} otherwise Java will try to look up the protocol and fail thereby throwing the
     * dreaded {@link MalformedURLException}.
     */
    private class CssEngineURLStreamHandler extends URLStreamHandler {

        /**
         * {@inheritDoc}
         */
        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            try {
                final byte[] content = CssEngineResource.this.engine.toCSS(sourceFilename).getBytes("utf-8");
                return new URLConnection(u) {
                    @Override
                    public void connect() throws IOException {
                        // no-op
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                        return new ByteArrayInputStream(content);
                    }

                    @Override
                    public int getContentLength() {
                        return content.length;
                    }

                    @Override
                    public String getContentEncoding() {
                        return "utf-8";
                    }

                    @Override
                    public String getContentType() {
                        return "text/css";
                    }

                    @Override
                    public long getLastModified() {
                        return CssEngineResource.this.lastModified();
                    }
                };
            } catch (CssCompilationError e) {
                IOException ioe = new IOException(e.getMessage());
                ioe.initCause(e);
                throw ioe;
            }
        }
    }


}
