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

package org.jszip.rhino;

import org.apache.maven.plugin.logging.Log;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

/**
 * An {@link ErrorReporter} that routes to a {@link Log}
 */
public class MavenLogErrorReporter implements ErrorReporter {

    private final Log log;

    public MavenLogErrorReporter(Log log) {
        this.log = log;
    }

    public void warning(String message, String sourceName, int line, String lineSource,
                        int lineOffset) {
        log.warn(message);
    }

    public void error(String message, String sourceName, int line, String lineSource,
                      int lineOffset) {
        log.error(message);
    }

    public EvaluatorException runtimeError(String message, String sourceName, int line,
                                           String lineSource, int lineOffset) {
        return new EvaluatorException(message, sourceName, line, lineSource, lineOffset);
    }
}
