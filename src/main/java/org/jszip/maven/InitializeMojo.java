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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;

/**
 * Initializes the JSZIP artifact.
 *
 * @phase compile
 * @goal initialize
 */
public class InitializeMojo extends AbstractJSZipMojo {

    /**
     * Directory containing the classes.
     *
     * @parameter expression="src/main/js"
     * @required
     */
    private File contentDirectory;

    /**
     * Classifier to add to the artifact generated. If given, the artifact will be an attachment instead.
     *
     * @parameter
     */
    private String classifier;

    /**
     * Maven ProjectHelper.
     *
     * @component
     * @readonly
     */
    private MavenProjectHelper projectHelper;

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        // In order to behave like JAR projects, we point to the unpacked files up between the phases
        // compile and package. Once at the package phase, the packed artifact is used.

        if (StringUtils.isEmpty(classifier)) {
            project.getArtifact().setFile(contentDirectory);
        } else {
            boolean found = false;
            for (Artifact artifact : project.getAttachedArtifacts()) {
                if (StringUtils.equals(artifact.getGroupId(), project.getGroupId())
                        && StringUtils.equals(artifact.getArtifactId(), project.getArtifactId())
                        && StringUtils.equals(artifact.getVersion(), project.getVersion())
                        && StringUtils.equals(artifact.getClassifier(), classifier)
                        && StringUtils.equals(artifact.getType(), JSZIP_TYPE)) {
                    if (artifact.getFile() == null) {
                        artifact.setFile(contentDirectory);
                    }
                    found = true;
                }
            }
            if (!found) {
                projectHelper.attachArtifact(project, JSZIP_TYPE, classifier, contentDirectory);
            }
        }
    }

}
