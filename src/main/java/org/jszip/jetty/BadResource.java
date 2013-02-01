package org.jszip.jetty;

import org.eclipse.jetty.util.resource.Resource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * A resource that does not exist.
 */
class BadResource extends Resource {

    BadResource() {
    }


    /** {@inheritDoc} */
    @Override
    public boolean isContainedIn(Resource r) throws MalformedURLException {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void release() {
    }

    /** {@inheritDoc} */
    @Override
    public boolean exists() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public long lastModified() {
        return -1;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDirectory() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public long length() {
        return -1;
    }

    /** {@inheritDoc} */
    @Override
    public URL getURL() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public File getFile() {
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
        throw new FileNotFoundException();
    }

    /** {@inheritDoc} */
    @Override
    public OutputStream getOutputStream()
            throws IOException, SecurityException {
        throw new FileNotFoundException();
    }

    /** {@inheritDoc} */
    @Override
    public boolean delete() throws SecurityException {
        throw new SecurityException();
    }

    /** {@inheritDoc} */
    @Override
    public boolean renameTo(Resource dest) throws SecurityException {
        throw new SecurityException();
    }

    /** {@inheritDoc} */
    @Override
    public String[] list() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Resource addPath(String path) throws IOException, MalformedURLException {
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public void copyTo(File destination) throws IOException {
        throw new SecurityException();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return super.toString() + "; BadResource";
    }

}
