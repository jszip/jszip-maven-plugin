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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AliasFilePseudoFile extends PseudoFile {

    private final File delegate;
    private final String name;

    public AliasFilePseudoFile(PseudoFile parent, File delegate, String name) {
        super(parent);
        this.delegate = delegate;
        this.name = name;
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
    public boolean renameTo(PseudoFile dest) {
        return false;
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
    public boolean isDirectory() {
        return delegate.isDirectory();
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
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    public boolean setLastModified(long time) {
        return delegate.setLastModified(time);
    }

    /**
     * {@inheritDoc}
     */
    InputStream $newInputStream() throws IOException {
        return new FileInputStream(delegate);
    }

    /**
     * {@inheritDoc}
     */
    OutputStream $newOutputStream() throws IOException {
        return new FileOutputStream(delegate);
    }

    /**
     * {@inheritDoc}
     */
    OutputStream $newOutputStream(boolean append) throws IOException {
        return new FileOutputStream(delegate, append);
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
    public boolean mkdir() {
        return delegate.mkdir();
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
    public boolean mkdirs() {
        return delegate.mkdirs();
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
    public long length() {
        return delegate.length();
    }

}
