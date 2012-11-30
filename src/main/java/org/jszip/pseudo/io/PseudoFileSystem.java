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

import org.apache.commons.lang3.StringUtils;
import org.codehaus.plexus.archiver.zip.ZipEntry;
import org.codehaus.plexus.archiver.zip.ZipFile;
import org.mozilla.javascript.Context;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class PseudoFileSystem {
    /**
     * Secret key used to hold the reference to the pseudo filesystem.
     */
    private static final Object KEY = new Object();

    private final PseudoFile root = new VirtualDirectoryPseudoFile(null, "");

    private final Layer[] layers;

    public PseudoFileSystem(Layer... layers) {
        this.layers = layers;
    }

    public String getPathSeparator() {
        return "/";
    }

    public PseudoFile[] listChildren(PseudoFile dir, PseudoFileFilter filter) {
        TreeMap<String, Layer> names = new TreeMap<String, Layer>();
        final String path = dir.getAbsolutePath();
        for (int i = layers.length - 1; i >= 0; i--) {
            for (String name : layers[i].listChildren(path)) {
                names.put(name, layers[i]);
            }
        }
        List<PseudoFile> result = new ArrayList<PseudoFile>(names.size());
        for (Map.Entry<String, Layer> entry : names.entrySet()) {
            if (filter.accept(entry.getKey())) {
                result.add(entry.getValue().makeChild(dir, entry.getKey()));
            }
        }
        return result.toArray(new PseudoFile[result.size()]);
    }

    public PseudoFile root() {
        return root;
    }

    public PseudoFile getPseudoFile(String filename) {
        filename = StringUtils.removeEnd(filename, getPathSeparator());
        if (filename.isEmpty()) {
            return root();
        }
        int index = filename.lastIndexOf(getPathSeparator());
        if (index != -1) {
            return getPseudoFile(getPseudoFile(filename.substring(0, index)), filename.substring(index + 1));
        }
        return getPseudoFile(root(), filename);
    }

    public PseudoFile getPseudoFile(PseudoFile parent, String name) {
        if (name.equals(".")) {
            return parent;
        }
        if (name.equals("..")) {
            return parent.getParentFile();
        }
        String parentPath = parent.getAbsolutePath();
        for (Layer layer : layers) {
            if (layer.listChildren(parentPath).contains(name)) {
                return layer.makeChild(parent, name);
            }
        }
        if (layers.length == 0) {
            return new VirtualDirectoryPseudoFile(parent, name);
        }
        return layers[0].makeChild(parent, name);
    }

    public synchronized void installInContext() {
        Context.getCurrentContext().putThreadLocal(KEY, this);
    }

    public synchronized void removeFromContext() {
        Context.getCurrentContext().putThreadLocal(KEY, null);
    }

    public static PseudoFileSystem current() {
        return (PseudoFileSystem) Context.getCurrentContext().getThreadLocal(KEY);
    }

    public abstract static class Layer {

        public abstract List<String> listChildren(String relativePath);

        public abstract PseudoFile makeChild(PseudoFile parent, String name);

    }

    public static class FileLayer extends Layer {
        private final String prefix;
        private final File root;

        public FileLayer(File root) {
            this("", root);
        }

        public FileLayer(String prefix, File root) {
            this.prefix = StringUtils.isEmpty(prefix) ? "/" : "/" + StringUtils.removeEnd(
                    StringUtils.removeStart(prefix, "/"), "/") + "/";
            this.root = root;
        }

        @Override
        public List<String> listChildren(String relativePath) {
            relativePath = StringUtils.removeEnd(relativePath, "/") + "/";
            if (relativePath.startsWith(prefix)) {
                final String pathFragment = relativePath.substring(prefix.length());
                final String[] list = new File(root, pathFragment).list();
                return list == null ? Collections.<String>emptyList() : Arrays.asList(list);
            }
            if (prefix.startsWith(relativePath)) {
                int index = prefix.indexOf('/', relativePath.length() + 1);
                if (index != -1) {
                    return Collections.singletonList(prefix.substring(relativePath.length(), index));
                }
            }
            return Collections.emptyList();
        }

        @Override
        public PseudoFile makeChild(PseudoFile parent, String name) {
            String relativePath = StringUtils.removeEnd(parent.getAbsolutePath(), "/") + "/" + name;
            if (relativePath.startsWith(prefix)) {
                return new FilePseudoFile(parent, new File(root, relativePath.substring(prefix.length())));
            }
            if (prefix.equals(relativePath + "/")) {
                int lastIndex = prefix.lastIndexOf('/');
                int index = prefix.lastIndexOf('/', lastIndex - 1);
                return new AliasFilePseudoFile(parent, root, prefix.substring(index + 1, lastIndex));
            }
            if (!StringUtils.isEmpty(prefix) && prefix.startsWith(relativePath)) {
                return new VirtualDirectoryPseudoFile(parent, name);
            }
            return new NotExistingPseudoFile(parent, name);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("FileLayer");
            sb.append("{prefix='").append(prefix).append('\'');
            sb.append(", root=").append(root);
            sb.append('}');
            return sb.toString();
        }
    }

    public static class ZipLayer extends PseudoFileSystem.Layer {
        private final String prefix;
        private final File zipFile;
        private final Map<String, ZipEntry> contents;

        public ZipLayer(String prefix, File zipFile) throws IOException {
            this.prefix = StringUtils.isEmpty(prefix) ? "/" : "/" + StringUtils.removeEnd(
                    StringUtils.removeStart(prefix, "/"), "/") + "/";
            this.zipFile = zipFile;
            ZipFile file = new ZipFile(zipFile);
            Map<String, ZipEntry> contents = new TreeMap<String, ZipEntry>();
            Enumeration<ZipEntry> entries = file.getEntries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                contents.put(this.prefix + StringUtils.removeStart(entry.getName(), "/"), entry);
            }

            this.contents = contents;
        }

        @Override
        public List<String> listChildren(String relativePath) {
            relativePath = StringUtils.removeEnd(relativePath, "/") + "/";
            if (relativePath.startsWith(prefix) || prefix.equals(relativePath + "/")) {
                final String pathFragment = StringUtils.removeEnd(relativePath,"/") + "/";
                Set<String> result = new LinkedHashSet<String>();
                for (String path : contents.keySet()) {
                    if (path.startsWith(pathFragment)) {
                        int index = path.indexOf('/', pathFragment.length());
                        if (index == -1) {
                            result.add(path.substring(pathFragment.length()));
                        } else {
                            result.add(path.substring(pathFragment.length(), index));
                        }
                    }
                }
                return new ArrayList<String>(result);
            }
            if (prefix.startsWith(relativePath)) {
                int index = prefix.indexOf('/', relativePath.length());
                if (index != -1) {
                    return Collections.singletonList(prefix.substring(relativePath.length(), index));
                }
            }
            return Collections.emptyList();
        }

        @Override
        public PseudoFile makeChild(PseudoFile parent, String name) {
            String relativePath = StringUtils.removeEnd(parent.getAbsolutePath(), "/") + "/" + name;
            final ZipEntry entry = contents.get(relativePath);
            if (entry != null) {
                return new ZipPseudoFile(parent, zipFile, entry);
            }
            if (prefix.equals(relativePath + "/")) {
                return new VirtualDirectoryPseudoFile(parent, name);
            }
            if (!StringUtils.isEmpty(prefix) && prefix.startsWith(relativePath)) {
                return new VirtualDirectoryPseudoFile(parent, name);
            }
            for (String childPath: contents.keySet()) {
                if (!StringUtils.isEmpty(childPath) && childPath.startsWith(relativePath)) {
                    return new VirtualDirectoryPseudoFile(parent, name);
                }
            }
            return new NotExistingPseudoFile(parent, name);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("ZipLayer");
            sb.append("{prefix='").append(prefix).append('\'');
            sb.append(", zipFile=").append(zipFile);
            sb.append('}');
            return sb.toString();
        }
    }

}
