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

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class PseudoFileInputChannel implements Closeable {

    public abstract long size() throws IOException;

    public abstract int read(ByteBuffer dst) throws IOException;

    public long transferTo(long position, long count, PseudoFileOutputChannel target) throws IOException {
        if (position != 0 || count != size()) {
            throw new UnsupportedOperationException();
        }
        ByteBuffer buf = ByteBuffer.allocate(8192);
        while (read(buf) != -1) {
            buf.flip();
            target.write(buf);
            buf.clear();
        }
        return count;
    }

}
