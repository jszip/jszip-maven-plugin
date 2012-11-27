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
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class PseudoFileOutputStream extends OutputStream {
    private final OutputStream delegate;

    public PseudoFileOutputStream(PseudoFile file) throws IOException {
        this.delegate = file.$newOutputStream();
    }

    public PseudoFileOutputStream(String filename) throws IOException {
        this.delegate = PseudoFileSystem.current().getPseudoFile(filename).$newOutputStream();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void write(byte[] b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
    }

    public PseudoFileOutputChannel getChannel() {
        return new PseudoFileOutputChannel() {
            @Override
            public int write(ByteBuffer src) throws IOException {
                final int remaining = src.remaining();
                delegate.write(src.array(), src.arrayOffset(), remaining);
                src.position(src.position() + remaining);
                return remaining;
            }

            public void close() throws IOException {
                PseudoFileOutputStream.this.close();
            }
        };
    }
}
