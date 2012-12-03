package org.jszip.jetty;

import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.Resource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author stephenc
 * @since 03/12/2012 09:18
 */
public class VirtualDirectoryResource extends Resource {

    private Resource child;
    private final String name;

    public VirtualDirectoryResource(Resource child, String name) {
        int index = name.indexOf(URIUtil.SLASH);
        if (index == 0) {
            name = name.substring(1);
            index = name.indexOf(URIUtil.SLASH);
        }
        if ((index == -1) || (index == name.length() - 1)) {
            this.child = child;
            this.name = name;
        } else {
            this.child = new VirtualDirectoryResource(child, name.substring(index + 1));
            this.name = name.substring(0, index);
        }
    }

    public Resource getChild() {
        return child;
    }

    public void setChild(Resource child) {
        this.child = child;
    }

    @Override
    public boolean isContainedIn(Resource resource) throws MalformedURLException {
        return false;
    }

    @Override
    public void release() {
        if (child != null) {
            child.release();
        }
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public long lastModified() {
        return child != null ? child.lastModified() : -1;
    }

    @Override
    public long length() {
        return -1;
    }

    @Override
    public URL getURL() {
        // TODO return a non-null URL to allow directory browsing support
        return null;
    }

    @Override
    public File getFile() throws IOException {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return null;
    }

    @Override
    public OutputStream getOutputStream() throws IOException, SecurityException {
        return null;
    }

    @Override
    public boolean delete() throws SecurityException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean renameTo(Resource resource) throws SecurityException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] list() {
        return new String[]{name + "/"};
    }

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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("VirtualDirectoryResource");
        sb.append("{name='").append(name).append('\'');
        sb.append(", child=").append(child);
        sb.append('}');
        return sb.toString();
    }

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

    @Override
    public int hashCode() {
        int result = child != null ? child.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }

    class BadResource extends Resource {

        BadResource() {
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
            return false;
        }

        @Override
        public long lastModified() {
            return -1;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public long length() {
            return -1;
        }

        @Override
        public URL getURL() {
            return null;
        }

        @Override
        public File getFile() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            throw new FileNotFoundException();
        }

        @Override
        public OutputStream getOutputStream()
                throws java.io.IOException, SecurityException {
            throw new FileNotFoundException();
        }

        @Override
        public boolean delete() throws SecurityException {
            throw new SecurityException();
        }

        @Override
        public boolean renameTo(Resource dest) throws SecurityException {
            throw new SecurityException();
        }

        @Override
        public String[] list() {
            return null;
        }

        @Override
        public Resource addPath(String path) throws IOException, MalformedURLException {
            return this;
        }

        @Override
        public void copyTo(File destination) throws IOException {
            throw new SecurityException();
        }

        @Override
        public String toString() {
            return super.toString() + "; BadResource";
        }

    }

}
