package org.jszip.jetty;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * In order to support the {@link org.jszip.maven.Mapping} we need virtual resources to handle the path offset.
 */
public class VirtualDirectoryResource extends Resource {

    /**
     * The resource that we are inserting a virtual path in front of.
     */
    private Resource child;

    /**
     * The name of the child resource
     */
    private final String name;

    /**
     * The url of the child resource.
     */
    private final String pathName;

    /**
     * Creates a tree of virtual resources in order to present the child resource at the provided path.
     *
     * @param child     the child resource.
     * @param childPath the path at which the child resource will appear.
     */
    public VirtualDirectoryResource(Resource child, String childPath) {
        this(child, "/", childPath);
    }

    private VirtualDirectoryResource(Resource child, String path, String name) {
        int index = name.indexOf(URIUtil.SLASH);
        if (index == 0) {
            name = name.substring(1);
            index = name.indexOf(URIUtil.SLASH);
        }
        path = StringUtils.strip(path, "/");
        if (StringUtils.isBlank(path)) {
            this.pathName = "/";
        } else {
            this.pathName  = "/" + path + "/";
        }
        if ((index == -1) || (index == name.length() - 1)) {
            this.child = child;
            this.name = name;
        } else {
            this.name = name.substring(0, index);
            this.child = new VirtualDirectoryResource(child, pathName + this.name + "/", name.substring(index + 1));
        }
    }

    /**
     * Returns our child resource.
     * @return our child resource.
     */
    public Resource getChild() {
        return child;
    }

    /**
     * Sets our child resource.
     * @param child our child resource.
     */
    public void setChild(Resource child) {
        this.child = child;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isContainedIn(Resource resource) throws MalformedURLException {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void release() {
        if (child != null) {
            child.release();
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean exists() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDirectory() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public long lastModified() {
        return child != null ? child.lastModified() : -1;
    }

    /** {@inheritDoc} */
    @Override
    public long length() {
        return -1;
    }

    /** {@inheritDoc} */
    @Override
    public URL getURL() {
        try {
            return new URL("virtual", null, -1, pathName, VirtualURLStreamHandler.INSTANCE);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(
                    "MalformedURLException should not be thrown when a URLStreamHandler is provided");
        }
    }

    /** {@inheritDoc} */
    @Override
    public File getFile() throws IOException {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public InputStream getInputStream() throws IOException {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public OutputStream getOutputStream() throws IOException, SecurityException {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean delete() throws SecurityException {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    @Override
    public boolean renameTo(Resource resource) throws SecurityException {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    @Override
    public String[] list() {
        return new String[]{name + "/"};
    }

    /** {@inheritDoc} */
    @Override
    public Resource addPath(String path) throws IOException, MalformedURLException {
        if (path == null) {
            throw new MalformedURLException();
        }
        if (path.length() == 0 || URIUtil.SLASH.equals(path)) {
            return this;
        }
        if (path.startsWith(name) || path.startsWith(URIUtil.SLASH + name)) {
            return child.addPath(path.substring(path.indexOf(name) + name.length()));
        }
        return new BadResource();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("VirtualDirectoryResource");
        sb.append("{url='virtual:").append(pathName).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", child=").append(child);
        sb.append('}');
        return sb.toString();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VirtualDirectoryResource)) {
            return false;
        }

        VirtualDirectoryResource that = (VirtualDirectoryResource) o;

        if (child != null ? !child.equals(that.child) : that.child != null) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }

        return true;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int result = child != null ? child.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }

    /**
     * In order to ensure that we can create URLs with the {@code virtual:} protocol, we need to provide a dummy
     * {@link URLStreamHandler} otherwise Java will try to look up the protocol and fail thereby throwing the
     * dreaded {@link MalformedURLException}.
     */
    private static class VirtualURLStreamHandler extends URLStreamHandler {
        /**
         * The singleton instance.
         */
        private static final VirtualURLStreamHandler INSTANCE = new VirtualURLStreamHandler();

        /**
         * {@inheritDoc}
         */
        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            throw new IOException("virtual:" + u.getPath() + " is a virtual URL");
        }
    }

}
