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
import java.nio.ByteBuffer;

public class PseudoFileInputStream extends InputStream {
    private final InputStream delegate;
    private final PseudoFile file;

    public PseudoFileInputStream(PseudoFile file) throws IOException {
        this.file = file;
        this.delegate = file.$newInputStream();
    }

    public PseudoFileInputStream(String filename) throws IOException {
        this.file = PseudoFileSystem.current().getPseudoFile(filename);
        this.delegate = file.$newInputStream();
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public void mark(int readlimit) {
        delegate.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        return delegate.markSupported();
    }

    @Override
    public int read() throws IOException {
        return delegate.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return delegate.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return delegate.read(b, off, len);
    }

    @Override
    public void reset() throws IOException {
        delegate.reset();
    }

    @Override
    public long skip(long n) throws IOException {
        return delegate.skip(n);
    }

    public PseudoFileInputChannel getChannel() {
        return new PseudoFileInputChannel() {
            @Override
            public long size() throws IOException {
                return file.length();
            }

            @Override
            public int read(ByteBuffer dst) throws IOException {
                int pos = dst.arrayOffset();
                int len = dst.remaining();
                int read = delegate.read(dst.array(), pos, len);
                if (read > 0) {
                    dst.position(dst.position() + read);
                }
                return read;
            }

            public void close() throws IOException {
                PseudoFileInputStream.this.close();
            }
        };
    }

}