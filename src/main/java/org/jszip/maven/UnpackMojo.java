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
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.util.Set;

/**
 * Unpacks all the JSZip dependencies into a web application.
 *
 * @phase generate-resources
 * @goal unpack
 * @requiresDependencyResolution compile+runtime
 */
public class UnpackMojo extends AbstractJSZipMojo {

    /**
     * The directory where the webapp is built.
     *
     * @parameter expression="${project.build.directory}/${project.build.finalName}"
     * @required
     */
    private File webappDirectory;

    /**
     * The Zip unarchiver.
     *
     * @component role="org.codehaus.plexus.archiver.UnArchiver" roleHint="zip"
     */
    private ZipUnArchiver zipUnArchiver;

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute()
            throws MojoExecutionException, MojoFailureException {
        getLog().info("Starting unpack into " + webappDirectory);
        FilterArtifacts filter = new FilterArtifacts();

        filter.addFilter(new ProjectTransitivityFilter(project.getDependencyArtifacts(), false));

        filter.addFilter(new ScopeFilter("runtime", ""));

        filter.addFilter(new TypeFilter(JSZIP_TYPE, ""));

        // start with all artifacts.
        Set<Artifact> artifacts = project.getArtifacts();

        // perform filtering
        try {
            artifacts = filter.filter(artifacts);
        } catch (ArtifactFilterException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        for (Artifact artifact : artifacts) {
            getLog().info("Unpacking " + ArtifactUtils.key(artifact));
            unpack(artifact.getFile(), webappDirectory, null, null);
        }
    }

    protected void unpack(File file, File location, String includes, String excludes)
            throws MojoExecutionException {
        if (file.isDirectory()) {
            // TODO handle unpack when the artifact is a directory
            try {
                throw new UnsupportedOperationException(
                        "Have not written the code to handle unpacking from the reactor where the reactor has not "
                                + "advanced far enough to generate the packed artifact");
            } catch (UnsupportedOperationException e) {
                throw new MojoExecutionException("Try running with phase of 'package' or later", e);
            }
        } else {
            try {
                location.mkdirs();

                zipUnArchiver.setSourceFile(file);

                zipUnArchiver.setDestDirectory(location);

                if (StringUtils.isNotEmpty(excludes) || StringUtils.isNotEmpty(includes)) {
                    IncludeExcludeFileSelector[] selectors =
                            new IncludeExcludeFileSelector[]{new IncludeExcludeFileSelector()};

                    if (StringUtils.isNotEmpty(excludes)) {
                        selectors[0].setExcludes(excludes.split(","));
                    }

                    if (StringUtils.isNotEmpty(includes)) {
                        selectors[0].setIncludes(includes.split(","));
                    }

                    zipUnArchiver.setFileSelectors(selectors);
                }

                zipUnArchiver.extract();
            } catch (ArchiverException e) {
                e.printStackTrace();
                throw new MojoExecutionException("Error unpacking file: " + file + " to: " + location + "\r\n"
                        + e.toString(), e);
            }
        }
    }

}
