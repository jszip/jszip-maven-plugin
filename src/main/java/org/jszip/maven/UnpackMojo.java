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
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugin.PluginContainerException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Unpacks all the JSZip dependencies into a web application.
 */
@org.apache.maven.plugins.annotations.Mojo(name = "unpack",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class UnpackMojo extends AbstractJSZipMojo {

    /**
     * The artifact path mappings for unpacking.
     */
    @Parameter(property = "mappings")
    private Mapping[] mappings;

    /**
     * The directory where the webapp is built.
     */
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}", required = true)
    private File webappDirectory;

    /**
     * The Zip unarchiver.
     */
    @Component(role = org.codehaus.plexus.archiver.UnArchiver.class, hint = "zip")
    private ZipUnArchiver zipUnArchiver;

    /**
     * The reactor projects
     */
    @Parameter(property = "reactorProjects", required = true, readonly = true)
    protected List<MavenProject> reactorProjects;

    /**
     * The Maven plugin Manager
     */
    @Component
    private MavenPluginManager mavenPluginManager;

    /**
     * The current build session instance. This is used for plugin manager API calls.
     */
    @Parameter(property = "session", required = true, readonly = true)
    private MavenSession session;

    /**
     * This plugin's descriptor
     */
    @Parameter(property = "plugin", readonly = true)
    private PluginDescriptor pluginDescriptor;

    /**
     * A list of &lt;include&gt; elements specifying the files (by pattern) that should be included in
     * unpacking.
     */
    @Parameter
    private List<String> unpackIncludes;

    /**
     * A list of &lt;exclude&gt; elements specifying the files (by pattern) that should be excluded from
     * unpacking. The default is
     * <pre>
     *     &lt;unpackExclude&gt;META-INF/maven/&#42;&#42;/pom.&#42;&lt;/unpackExclude&gt;
     *     &lt;unpackExclude&gt;package.json&lt;/unpackExclude&gt;
     *     &lt;unpackExclude&gt;&#42;&#42;/&#42;.less&lt;/unpackExclude&gt;
     *     &lt;unpackExclude&gt;&#42;&#42;/&#42;.sass&lt;/unpackExclude&gt;
     *     &lt;unpackExclude&gt;&#42;&#42;/&#42;.scss&lt;/unpackExclude&gt;
     * </pre>
     */
    @Parameter
    private List<String> unpackExcludes;

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

        String includes;
        String excludes;
        if (unpackIncludes != null && !unpackIncludes.isEmpty()) {
            includes = StringUtils.join(unpackIncludes.iterator(), ",");
        } else {
            includes = null;
        }

        if (unpackExcludes != null && !unpackExcludes.isEmpty()) {
            excludes = StringUtils.join(unpackExcludes.iterator(), ",");
        } else {
            excludes="META-INF/maven/**/pom.*,package.json,**/*.less,**/*.sass,**/*.scss";
        }



        for (Artifact artifact : artifacts) {
            String path = getPath(artifact);
            File artifactDirectory;
            if (StringUtils.isBlank(path)) {
                getLog().info("Unpacking " + ArtifactUtils.key(artifact));
                artifactDirectory = webappDirectory;
            } else {
                getLog().info("Unpacking " + ArtifactUtils.key(artifact) + " at path " + path);
                artifactDirectory = new File(webappDirectory, path);
            }
            unpack(artifact, artifactDirectory, includes, excludes);
        }
    }

    private String getPath(Artifact artifact) {
        if (mappings == null) return "";
        for (Mapping mapping: mappings) {
            if (mapping.isMatch(artifact))
                return StringUtils.clean(mapping.getPath());
        }
        return "";
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
                        JSZipMojo mojo = (JSZipMojo) mavenPluginManager
                                .getConfiguredMojo(Mojo.class, session, mojoExecution);
                        try {
                            File contentDirectory = mojo.getContentDirectory();
                            if (contentDirectory.isDirectory()) {
                                FileUtils.copyDirectory(contentDirectory, location);
                            }
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
