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

package org.jszip.maven;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * The build plan has been modified in such a way that it cannot be compensated for and Maven must be restarted.
 *
 * @author stephenc
 * @since 21/02/2012 10:48
 */
public class BuildPlanModifiedException extends MojoExecutionException {
    /**
     * {@inheritDoc}
     */
    public BuildPlanModifiedException(String message) {
        super(message);
    }

    /**
     * {@inheritDoc}
     */
    public BuildPlanModifiedException(String message, Exception cause) {
        super(message, cause);
    }

    /**
     * {@inheritDoc}
     */
    public BuildPlanModifiedException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * {@inheritDoc}
     */
    public BuildPlanModifiedException(Object source, String shortMessage, String longMessage) {
        super(source, shortMessage, longMessage);
    }
}
