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

package org.jszip.pseudo;

import org.codehaus.plexus.util.IOUtil;
import org.jszip.pseudo.io.PseudoFile;
import org.jszip.pseudo.io.ProxyPseudoFile;
import org.jszip.pseudo.io.PseudoFileInputStream;
import org.jszip.pseudo.io.PseudoFileSystem;
import org.junit.*;
import org.mozilla.javascript.Context;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * @author stephenc
 * @since 26/11/2012 23:30
 */
public class SmokeTest {
    @Test
    public void smokes() throws Exception {
        assertThat(new File("/").getParentFile(), nullValue());
        assertThat(new File("/").getName(), is(""));
        assertThat(new File("/").getParent(), nullValue());
    }

    @Test
    public void access() throws Exception {
        final String pathname = "/" + SmokeTest.class.getName().replace('.', '/') + ".class";
        final Context context = Context.enter();
        try {
            final File root = new File(SmokeTest.class.getResource("/").toURI());
            PseudoFileSystem fs = new PseudoFileSystem(new PseudoFileSystem.FileLayer(root));
            fs.installInContext();
            try {
                final PseudoFile f = new ProxyPseudoFile(pathname);
                assertThat(f.getParentFile().getParentFile().getParentFile().getParentFile(), is(fs.root()));
                InputStream is = null;
                byte[] direct;
                try {
                    is = SmokeTest.class.getResourceAsStream(pathname);
                    direct = IOUtil.toByteArray(is);
                } finally {
                    IOUtil.close(is);
                }
                is= null;
                byte[] indirect;
                try {
                    is = new PseudoFileInputStream(f);
                    indirect = IOUtil.toByteArray(is);
                } finally {
                    IOUtil.close(is);
                }
                assertArrayEquals(direct, indirect);
                assertThat(listAll(fs), hasItems("file:"+pathname, "dir:/org/jszip/pseudo"));
            } finally {
                fs.removeFromContext();
            }
        } finally {
            Context.exit();
        }
    }

    @Test
    public void layerAccess() throws Exception {
        final String pathname = "/" + SmokeTest.class.getName().replace('.', '/') + ".class";
        final Context context = Context.enter();
        try {
            final File root = new File(SmokeTest.class.getResource("/").toURI());
            PseudoFileSystem fs = new PseudoFileSystem(new PseudoFileSystem.FileLayer("sub",root));
            fs.installInContext();
            try {
                final PseudoFile f = new ProxyPseudoFile("/sub"+pathname);
                assertThat(f.getParentFile().getParentFile().getParentFile().getParentFile().getParentFile(), is(fs.root()));
                InputStream is = null;
                byte[] direct;
                try {
                    is = SmokeTest.class.getResourceAsStream(pathname);
                    direct = IOUtil.toByteArray(is);
                } finally {
                    IOUtil.close(is);
                }
                is= null;
                byte[] indirect;
                try {
                    is = new PseudoFileInputStream(f);
                    indirect = IOUtil.toByteArray(is);
                } finally {
                    IOUtil.close(is);
                }
                assertArrayEquals(direct, indirect);
                assertThat(listAll(fs), hasItems("file:/sub"+pathname, "dir:/sub/org/jszip/pseudo"));
            } finally {
                fs.removeFromContext();
            }
        } finally {
            Context.exit();
        }
    }

    private List<String> listAll(PseudoFileSystem fs) {
        List<String> result = new ArrayList<String>();
        Stack<Iterator<PseudoFile>> stack = new Stack<Iterator<PseudoFile>>();
        stack.push(Arrays.asList(fs.root().listFiles()).iterator());
        while (!stack.isEmpty()) {
            Iterator<PseudoFile> iterator = stack.pop();
            while (iterator.hasNext()) {
                PseudoFile f= iterator.next();
                if (f.isFile()) {
                    result.add("file:"+f.getAbsolutePath());
                } else {
                    result.add("dir:"+f.getAbsolutePath());
                    stack.push(iterator);
                    iterator = Arrays.asList(f.listFiles()).iterator();
                }
            }
        }
        return result;
    }
}
