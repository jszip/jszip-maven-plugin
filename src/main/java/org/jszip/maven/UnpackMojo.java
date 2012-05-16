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
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugin.PluginContainerException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.aether.repository.RemoteRepository;

import java.io.File;
import java.io.IOException;
import java.util.List;
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
     * @parameter expression="${reactorProjects}"
     * @required
     * @readonly
     */
    protected List<MavenProject> reactorProjects;

    /**
     * The Maven plugin Manager
     *
     * @component
     * @readonly
     * @required
     */
    private MavenPluginManager mavenPluginManager;

    /**
     * The current build session instance. This is used for plugin manager API calls.
     *
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    private MavenSession session;

    /**
     * @parameter expression="${plugin}"
     * @readonly
     */
    private PluginDescriptor pluginDescriptor;

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
            unpack(artifact, webappDirectory, null, null);
        }
    }

    protected void unpack(Artifact artifact, File location, String includes, String excludes)
            throws MojoExecutionException {
        File file = artifact.getFile();
        if (file.isDirectory()) {
            MavenProject fromReactor = findProject(reactorProjects, artifact);
            if (fromReactor != null) {
                MavenSession session = this.session.clone();
                session.setCurrentProject(fromReactor);
                Plugin plugin = findThisPluginInProject(fromReactor);
                try {
                    // we cheat here and use our version of the plugin... but this is less of a cheat than the only
                    // other way which is via reflection.
                    MojoDescriptor jszipDescriptor = findMojoDescriptor(this.pluginDescriptor, JSZipMojo.class);

                    for (PluginExecution pluginExecution : plugin.getExecutions()) {
                        if (!pluginExecution.getGoals().contains(jszipDescriptor.getGoal())) {
                            continue;
                        }
                        MojoExecution mojoExecution =
                                createMojoExecution(plugin, pluginExecution, jszipDescriptor);
                        JSZipMojo mojo = (JSZipMojo)mavenPluginManager
                                .getConfiguredMojo(Mojo.class, session, mojoExecution);
                        try {
                            File contentDirectory = mojo.getContentDirectory();
                            if (contentDirectory.isDirectory()) {
                                FileUtils.copyDirectory(contentDirectory, location);
                            }
                            // TODO filtering support
                            // Note filtering support may not be required at this point because unpack
                            File resourcesDirectory = mojo.getResourcesDirectory();
                            if (resourcesDirectory.isDirectory()) {
                                FileUtils.copyDirectory(resourcesDirectory, location);
                            }
                        } finally {
                            mavenPluginManager.releaseMojo(mojo, mojoExecution);
                        }
                    }
                } catch (PluginConfigurationException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                } catch (IOException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                } catch (PluginContainerException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            } else {
                throw new MojoExecutionException("Cannot find jzsip artifact: " + artifact.getId());
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
