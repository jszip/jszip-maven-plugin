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

package org.jszip.pseudo.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ProxyPseudoFile extends PseudoFile {

    private PseudoFile delegate;

    public ProxyPseudoFile(PseudoFile parent, String child) {
        super(parent);
        final PseudoFileSystem current = PseudoFileSystem.current();
        delegate = current.getPseudoFile(parent.getAbsolutePath() + current.getPathSeparator() + child);
    }

    public ProxyPseudoFile(String pathname) {
        super(PseudoFileSystem.current().getPseudoFile(pathname).getParentFile());
        delegate = PseudoFileSystem.current().getPseudoFile(pathname);
    }

    public ProxyPseudoFile(String parent, String child) {
        super(PseudoFileSystem.current().getPseudoFile(parent));
        final PseudoFileSystem current = PseudoFileSystem.current();
        delegate = current.getPseudoFile(parent + current.getPathSeparator() + child);
    }

    /**
     * {@inheritDoc}
     */
    InputStream $newInputStream() throws IOException {
        return delegate.$newInputStream();
    }

    /**
     * {@inheritDoc}
     */
    OutputStream $newOutputStream() throws IOException {
        return delegate.$newOutputStream();
    }

    /**
     * {@inheritDoc}
     */
    OutputStream $newOutputStream(boolean append) throws IOException {
        return delegate.$newOutputStream(append);
    }

    /**
     * {@inheritDoc}
     */
    public boolean canRead() {
        return delegate.canRead();
    }

    /**
     * {@inheritDoc}
     */
    public boolean canWrite() {
        return delegate.canWrite();
    }

    /**
     * {@inheritDoc}
     */
    public boolean createNewFile() throws IOException {
        return delegate.createNewFile();
    }

    /**
     * {@inheritDoc}
     */
    public boolean delete() {
        return delegate.delete();
    }

    /**
     * {@inheritDoc}
     */
    public void deleteOnExit() {
        delegate.deleteOnExit();
    }

    /**
     * {@inheritDoc}
     */
    public boolean exists() {
        return delegate.exists();
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return delegate.getName();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDirectory() {
        return delegate.isDirectory();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isFile() {
        return delegate.isFile();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isHidden() {
        return delegate.isHidden();
    }

    /**
     * {@inheritDoc}
     */
    public long lastModified() {
        return delegate.lastModified();
    }

    /**
     * {@inheritDoc}
     */
    public long length() {
        return delegate.length();
    }

    /**
     * {@inheritDoc}
     */
    public boolean mkdir() {
        return delegate.mkdir();
    }

    /**
     * {@inheritDoc}
     */
    public boolean mkdirs() {
        return delegate.mkdirs();
    }

    /**
     * {@inheritDoc}
     */
    public boolean renameTo(PseudoFile dest) {
        return delegate.renameTo(dest);
    }

    /**
     * {@inheritDoc}
     */
    public boolean setLastModified(long time) {
        return delegate.setLastModified(time);
    }

    PseudoFile $unwrap() {
        return delegate;
    }

}
