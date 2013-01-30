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

public class NotExistingPseudoFile extends PseudoFile {
    private final String name;

    public NotExistingPseudoFile(PseudoFile parent, String name) {
        super(parent);
        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    public boolean canRead() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean canWrite() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean createNewFile() throws IOException {
        throw new IOException(getPath() + " is a non-existing file");
    }

    /**
     * {@inheritDoc}
     */
    public boolean delete() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public void deleteOnExit() {
    }

    /**
     * {@inheritDoc}
     */
    public boolean exists() {
        return false;
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
    public boolean isDirectory() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isFile() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isHidden() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public long lastModified() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    public long length() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    public boolean mkdir() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean mkdirs() {
        return false;
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
    public boolean setLastModified(long time) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    InputStream $newInputStream() throws IOException {
        throw new IOException(getPath() + " is a non-existing file");
    }

    /**
     * {@inheritDoc}
     */
    OutputStream $newOutputStream() throws IOException {
        throw new IOException(getPath() + " is a non-existing file");
    }

    /**
     * {@inheritDoc}
     */
    OutputStream $newOutputStream(boolean append) throws IOException {
        throw new IOException(getPath() + " is a non-existing file");
    }
}
