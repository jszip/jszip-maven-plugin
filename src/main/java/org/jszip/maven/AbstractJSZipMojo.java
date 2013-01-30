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
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Common base class for all the JSZip goals.
 */
public abstract class AbstractJSZipMojo extends AbstractMojo {
    /**
     * The type of packaging.
     */
    public static final String JSZIP_TYPE = "jszip";

    /**
     * The maven project.
     */
    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;
    /**
     * The current plugin.
     */
    @Parameter(property = "plugin.groupId", readonly = true, required = true)
    private String pluginGroupId;
    /**
     * The current plugin.
     */
    @Parameter(property = "plugin.artifactId", readonly = true, required = true)
    private String pluginArtifactId;
    /**
     * The current plugin.
     */
    @Parameter(property = "plugin.version", readonly = true, required = true)
    private String pluginVersion;

    protected static <T> T invokeMethod(Object object, Class<T> rvClass, String method, Object... args)
            throws MojoExecutionException {
        Class[] argClasses = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            argClasses[i] = args.getClass();
        }
        try {
            Method m = object.getClass().getMethod(method, argClasses);
            return rvClass.cast(m.invoke(object, args));
        } catch (InvocationTargetException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (NoSuchMethodException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (IllegalAccessException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }


    protected MojoExecution createMojoExecution(Plugin plugin, PluginExecution pluginExecution,
                                                MojoDescriptor mojoDescriptor) {
        MojoExecution mojoExecution = new MojoExecution(plugin, mojoDescriptor.getGoal(), pluginExecution.getId());
        mojoExecution.setConfiguration(convert(mojoDescriptor));
        if (plugin.getConfiguration() != null || pluginExecution.getConfiguration() != null) {
            Xpp3Dom pluginConfiguration =
                    plugin.getConfiguration() == null ? new Xpp3Dom("fake")
                            : (Xpp3Dom) plugin.getConfiguration();

            Xpp3Dom mergedConfigurationWithExecution =
                    Xpp3DomUtils.mergeXpp3Dom(
                            (Xpp3Dom) pluginExecution.getConfiguration(),
                            pluginConfiguration);

            Xpp3Dom mergedConfiguration =
                    Xpp3DomUtils.mergeXpp3Dom(mergedConfigurationWithExecution,
                            convert(mojoDescriptor));

            Xpp3Dom cleanedConfiguration = new Xpp3Dom("configuration");
            if (mergedConfiguration.getChildren() != null) {
                for (Xpp3Dom parameter : mergedConfiguration.getChildren()) {
                    if (mojoDescriptor.getParameterMap().containsKey(parameter.getName())) {
                        cleanedConfiguration.addChild(parameter);
                    }
                }
            }
            if (getLog().isDebugEnabled()) {
                getLog().debug("mojoExecution mergedConfiguration: " + mergedConfiguration);
                getLog().debug("mojoExecution cleanedConfiguration: " + cleanedConfiguration);
            }

            mojoExecution.setConfiguration(cleanedConfiguration);

        }
        mojoExecution.setMojoDescriptor(mojoDescriptor);
        return mojoExecution;
    }

    protected MojoDescriptor findMojoDescriptor(PluginDescriptor pluginDescriptor, Class<? extends Mojo> mojoClass) {
        MojoDescriptor mojoDescriptor = null;
        for (MojoDescriptor d : pluginDescriptor.getMojos()) {
            if (mojoClass.getName().equals(d.getImplementation())) {
                mojoDescriptor = pluginDescriptor.getMojo(d.getGoal());
                break;
            }
        }

        if (mojoDescriptor == null) {
            getLog().error("Cannot find goal that corresponds to " + mojoClass);
            throw new IllegalStateException("This plugin should always have the " + mojoClass.getName() + " goal");
        }
        return mojoDescriptor;
    }

    protected Plugin findThisPluginInProject(MavenProject project) {
        Plugin plugin = null;
        for (Plugin b : project.getBuild().getPlugins()) {
            if (pluginGroupId.equals(b.getGroupId()) && pluginArtifactId.equals(b.getArtifactId())) {
                plugin = b.clone();
                plugin.setVersion(pluginVersion); // we want to use our version
                break;
            }
        }
        if (plugin == null) {
            getLog().debug("Falling back to our own plugin");
            plugin = new Plugin();
            plugin.setGroupId(pluginGroupId);
            plugin.setArtifactId(pluginArtifactId);
            plugin.setVersion(pluginVersion);
        }
        return plugin;
    }

    private Xpp3Dom convert(MojoDescriptor mojoDescriptor) {
        PlexusConfiguration config = mojoDescriptor.getMojoConfiguration();
        return (config != null) ? convert(config) : new Xpp3Dom("configuration");
    }

    private Xpp3Dom convert(PlexusConfiguration config) {
        if (config == null) {
            return null;
        }

        Xpp3Dom dom = new Xpp3Dom(config.getName());
        dom.setValue(config.getValue(null));

        for (String attrib : config.getAttributeNames()) {
            dom.setAttribute(attrib, config.getAttribute(attrib, null));
        }

        for (int n = config.getChildCount(), i = 0; i < n; i++) {
            dom.addChild(convert(config.getChild(i)));
        }

        return dom;
    }

    protected MavenProject findProject(List<MavenProject> projects, Artifact artifact) {
        for (MavenProject project : projects) {
            if (StringUtils.equals(artifact.getGroupId(), project.getGroupId())
                    && StringUtils.equals(artifact.getArtifactId(), project.getArtifactId())
                    && StringUtils.equals(artifact.getVersion(), project.getVersion())) {
                return project;
            }
        }
        return null;
    }

    protected static String[] processIncludesExcludes(List<String> list) {
        List<String> result = new ArrayList<String>();
        for (String entry : list) {
            String[] entries = entry.split(",");
            Collections.addAll(result, entries);
        }
        return result.toArray(new String[result.size()]);
    }

}
