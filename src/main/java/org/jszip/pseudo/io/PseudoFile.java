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

import org.sonatype.aether.util.StringUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class PseudoFile {
    private final PseudoFile parent;

    public PseudoFile(PseudoFile parent) {
        this.parent = parent;
    }

    public final int compareTo(PseudoFile pathname) {
        return getAbsolutePath().compareTo(pathname.getAbsolutePath());
    }

    public final String getAbsolutePath() {
        StringBuilder result = new StringBuilder();
        final String pathSeparator = PseudoFileSystem.current().getPathSeparator();
        if (parent != null) {
            String parentPath = parent.getAbsolutePath();
            if (!StringUtils.isEmpty(parentPath)) {
                result.append(parentPath);
                if (!parentPath.equals(pathSeparator)) {
                    result.append(pathSeparator);
                }
            }
        } else {
            result.append(pathSeparator);
        }
        result.append(getName());
        return result.toString();
    }

    public final PseudoFile getAbsoluteFile() {
        return this;
    }

    public final String getCanonicalPath() throws IOException {
        return getAbsolutePath();
    }

    public final PseudoFile getCanonicalFile() throws IOException {
        return this;
    }

    public final String getPath() {
        return getAbsolutePath();
    }

    public final String getParent() {
        return parent == null ? null : parent.getAbsolutePath();
    }

    public final PseudoFile getParentFile() {
        return parent;
    }

    public final boolean isAbsolute() {
        return true;
    }

    public final String[] list() {
        PseudoFile[] children = PseudoFileSystem.current().listChildren(this, PseudoFileFilter.FILTER_NONE);
        String[] result = new String[children.length];
        for (int i = 0; i < children.length; i++) {
            result[i] = children[i].getName();
        }
        return result;
    }

    public final String[] list(final FilenameFilter filter) {
        PseudoFile[] children = PseudoFileSystem.current().listChildren(this, new PseudoFileFilter() {
            final File fakeDir = new File(getAbsolutePath());

            public boolean accept(String name) {
                return filter.accept(fakeDir, name);
            }
        });
        String[] result = new String[children.length];
        for (int i = 0; i < children.length; i++) {
            result[i] = children[i].getName();
        }
        return result;
    }

    public final PseudoFile[] listFiles() {
        return PseudoFileSystem.current().listChildren(this, PseudoFileFilter.FILTER_NONE);
    }

    public final PseudoFile[] listFiles(final FilenameFilter filter) {
        return PseudoFileSystem.current().listChildren(this, new PseudoFileFilter() {
            final File fakeDir = new File(getAbsolutePath());

            public boolean accept(String name) {
                return filter.accept(fakeDir, name);
            }
        });
    }

    public final PseudoFile[] listFiles(final FileFilter filter) {
        final String fakeDir = getAbsolutePath();
        return PseudoFileSystem.current().listChildren(this, new PseudoFileFilter() {
            public boolean accept(String name) {
                return filter.accept(new File(fakeDir + "/" + name));
            }
        });
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PseudoFile)) {
            return false;
        }

        PseudoFile that = (PseudoFile) o;

        final PseudoFile thisParentFile = getParentFile();
        final PseudoFile thatParentFile = that.getParentFile();
        if (thisParentFile != null ? !thisParentFile.equals(thatParentFile) : thatParentFile != null) {
            return false;
        }

        final String thisName = getName();
        final String thatName = that.getName();
        if (thisName != null ? !thisName.equals(thatName) : thatName != null) {
            return false;
        }

        return true;
    }

    @Override
    public final int hashCode() {
        int result = parent != null ? parent.hashCode() : 0;
        final String name = getName();
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return getAbsolutePath();
    }

    public abstract boolean canRead();

    public abstract boolean canWrite();

    public abstract boolean createNewFile() throws IOException;

    public abstract boolean delete();

    public abstract void deleteOnExit();

    public abstract boolean exists();

    public abstract String getName();

    public abstract boolean isDirectory();

    public abstract boolean isFile();

    public abstract boolean isHidden();

    public abstract long lastModified();

    public abstract long length();

    public abstract boolean mkdir();

    public abstract boolean mkdirs();

    public abstract boolean renameTo(PseudoFile dest);

    public abstract boolean setLastModified(long time);

    abstract InputStream $newInputStream() throws IOException;

    abstract OutputStream $newOutputStream() throws IOException;
}
