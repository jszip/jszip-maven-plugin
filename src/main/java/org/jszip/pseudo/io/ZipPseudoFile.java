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

import org.codehaus.plexus.archiver.zip.ZipEntry;
import org.codehaus.plexus.archiver.zip.ZipFile;
import org.codehaus.plexus.util.IOUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ZipPseudoFile extends PseudoFile {

    private final File zipFile;
    private final ZipEntry entry;

    public ZipPseudoFile(PseudoFile parent, File zipFile, ZipEntry entry) {
        super(parent);
        this.zipFile = zipFile;
        this.entry = entry;
    }

    /**
     * {@inheritDoc}
     */
    public boolean canRead() {
        return true;
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
        throw new IOException(getPath() + " is read-only");
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
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        final String name = entry.getName();
        int index = name.lastIndexOf('/');
        if (index == -1) {
            return name;
        }
        return name.substring(index + 1);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDirectory() {
        return entry.isDirectory();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isFile() {
        return !entry.isDirectory();
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
        return entry.getTime();
    }

    /**
     * {@inheritDoc}
     */
    public long length() {
        return entry.getSize();
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
        getParentFile().mkdirs();
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
        InputStream inputStream = null;
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(this.zipFile);
            inputStream = zipFile.getInputStream(zipFile.getEntry(entry.getName()));
            byte[] contents = IOUtil.toByteArray(inputStream);
            return new ByteArrayInputStream(contents);
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            IOUtil.close(inputStream);
        }
    }

    /**
     * {@inheritDoc}
     */
    OutputStream $newOutputStream() throws IOException {
        throw new IOException(getPath() + " is read-only");
    }
}
