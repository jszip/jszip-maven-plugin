package org.jszip.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugin.PluginContainerException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.codehaus.plexus.util.StringUtils;
import org.jszip.pseudo.io.PseudoFileSystem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author stephenc
 * @since 21/12/2012 15:02
 */
public abstract class AbstractPseudoFileSystemProcessorMojo extends AbstractJSZipMojo {
    /**
     * The directory where the webapp is built.
     */
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}", required = true)
    protected File webappDirectory;
    /**
     * The reactor projects
     */
    @Parameter(property = "reactorProjects", required = true, readonly = true)
    protected List<MavenProject> reactorProjects;
    /**
     * The artifact path mappings for unpacking.
     */
    @Parameter(property = "mappings")
    private Mapping[] mappings;
    /**
     * Single directory for extra files to include in the WAR.
     */
    @Parameter(defaultValue = "${basedir}/src/main/webapp", required = true)
    private File warSourceDirectory;
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

    private String getPath(Artifact artifact) {
        if (mappings == null) {
            return "/virtual";
        }
        for (Mapping mapping: mappings) {
            if (mapping.isMatch(artifact)) {
                final String path = StringUtils.clean(mapping.getPath());
                if (StringUtils.isBlank(path) || "/".equals(path)) {
                    return "/virtual";
                }
                return "/virtual/" + StringUtils.strip(path, "/");
            }
        }
        return "/virtual";
    }

    protected List<PseudoFileSystem.Layer> buildVirtualFileSystemLayers() throws MojoExecutionException {
        List<PseudoFileSystem.Layer> layers = new ArrayList<PseudoFileSystem.Layer>();
        layers.add(new PseudoFileSystem.FileLayer("/target", webappDirectory));
        layers.add(new PseudoFileSystem.FileLayer("/virtual", warSourceDirectory));
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
            String path = getPath(artifact);
            getLog().info("Adding " + ArtifactUtils.key(artifact) + " to virtual filesystem");
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
                                    .getConfiguredMojo(org.apache.maven.plugin.Mojo.class, session, mojoExecution);
                            try {
                                File contentDirectory = mojo.getContentDirectory();
                                if (contentDirectory.isDirectory()) {
                                    getLog().debug("Merging directory " + contentDirectory + " into " + path);
                                    layers.add(new PseudoFileSystem.FileLayer(path, contentDirectory));
                                }
                                File resourcesDirectory = mojo.getResourcesDirectory();
                                if (resourcesDirectory.isDirectory()) {
                                    getLog().debug("Merging directory " + contentDirectory + " into " + path);
                                    layers.add(new PseudoFileSystem.FileLayer(path, resourcesDirectory));
                                }
                            } finally {
                                mavenPluginManager.releaseMojo(mojo, mojoExecution);
                            }
                        }
                    } catch (PluginConfigurationException e) {
                        throw new MojoExecutionException(e.getMessage(), e);
                    } catch (PluginContainerException e) {
                        throw new MojoExecutionException(e.getMessage(), e);
                    }
                } else {
                    throw new MojoExecutionException("Cannot find jzsip artifact: " + artifact.getId());
                }
            } else {
                try {
                    getLog().debug("Merging .zip file " + file + " into " + path);
                    layers.add(new PseudoFileSystem.ZipLayer(path, file));
                } catch (IOException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
        }
        return layers;
    }
}
