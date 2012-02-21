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
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.MultipleArtifactsNotFoundException;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.profiles.DefaultProfileManager;
import org.apache.maven.project.DuplicateProjectException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.MissingProjectException;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectSorter;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.dag.CycleDetectedException;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @goal run
 * @execute phase="test-compile"
 * @requiresDependencyResolution compile+runtime
 */
public class RunMojo extends AbstractMojo {
    /**
     * The module that the goal should apply to. Specify either groupId:artifactId or just plain artifactId.
     *
     * @parameter expression="${jszip.run.module}"
     */
    private String runModule;

    /**
     * List of the packaging types will be considered for executing this goal. Normally you do not
     * need to configure this parameter unless you have a custom war packaging type. Defaults to <code>war</code>
     *
     * @parameter
     */
    private String[] runPackages;

    /**
     * @component
     * @required
     */
    private MavenProjectBuilder projectBuilder;

    /**
     * @parameter expression="${reactorProjects}"
     * @required
     * @readonly
     */
    protected List<MavenProject> reactorProjects;

    /**
     * @component
     * @required
     */
    protected ArtifactResolver artifactResolver;

    /**
     * @component
     * @required
     */
    protected ArtifactFactory artifactFactory;

    /**
     * Location of the local repository.
     *
     * @parameter expression="${localRepository}"
     * @readonly
     * @required
     */
    private ArtifactRepository localRepository;

    /**
     * The current build session instance. This is used for plugin manager API calls.
     *
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    private MavenSession session;

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * @component
     * @required
     */
    private ArtifactMetadataSource metadataSource;

    /**
     * Used to resolve transitive dependencies in Maven 3.
     *
     * @component role="org.apache.maven.ProjectDependenciesResolver"
     */
    private Object projectDependenciesResolver;

    private final String scope = "test";

    public void execute()
            throws MojoExecutionException, MojoFailureException {
        if (runPackages == null || runPackages.length == 0) {
            runPackages = new String[]{"war"};
        }
        if (!Arrays.asList(runPackages).contains(project.getPackaging())) {
            getLog().info("Skipping JSZip run: module " + ArtifactUtils.versionlessKey(project.getGroupId(),
                    project.getArtifactId()) + " as not specified in runPackages");
            return;
        }
        if (StringUtils.isNotBlank(runModule)
                && !project.getArtifactId().equals(runModule)
                && !ArtifactUtils.versionlessKey(project.getGroupId(), project.getArtifactId()).equals(runModule)) {
            getLog().info("Skipping JSZip run: module " + ArtifactUtils.versionlessKey(project.getGroupId(),
                    project.getArtifactId()) + " as requested runModule is " + runModule);
            return;
        }
        MavenProject project = this.project;
        long lastPomChange = getPomsLastModified();
        getLog().info("Servlet container started. Will restart if changes to poms detected.");
        while (true) {
            long pomsLastModified = getPomsLastModified();
            if (lastPomChange == pomsLastModified) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    getLog().debug("Interrupted", e);
                }
            } else {

                getLog().info("Change in poms detected, re-parsing to evaluate impact...");
                lastPomChange = pomsLastModified; // we will now process this change, so from now on don't re-process
                // even if we have issues processing

                List<MavenProject> newReactorProjects;
                try {
                    newReactorProjects = buildReactorProjects();
                } catch (ProjectBuildingException e) {
                    getLog().error(e.getLocalizedMessage());
                    getLog().info("Re-parse aborted due to malformed pom.xml file(s)");
                    continue;
                } catch (CycleDetectedException e) {
                    getLog().error(e.getLocalizedMessage());
                    getLog().info("Re-parse aborted due to dependency cycle in project model");
                    continue;
                } catch (DuplicateProjectException e) {
                    getLog().error(e.getLocalizedMessage());
                    getLog().info("Re-parse aborted due to duplicate projects in project model");
                    continue;
                } catch (Exception e) {
                    getLog().error(e.getLocalizedMessage());
                    getLog().info("Re-parse aborted due a problem that prevented sorting the project model");
                    continue;
                }
                if (!buildPlanEqual(newReactorProjects, this.reactorProjects)) {
                    throw new BuildPlanModifiedException("A pom.xml change has impacted the build plan.");
                }
                MavenProject newProject = findProject(newReactorProjects, this.project);
                if (newProject == null) {
                    throw new BuildPlanModifiedException(
                            "A pom.xml change appears to have removed " + this.project.getId()
                                    + " from the build plan.");
                }

                newProject.setArtifacts(resolve(newProject, "runtime"));

                getLog().info("Comparing effective classpath of new and old models");
                boolean classPathChanged;
                try {
                    classPathChanged = classpathsEqual(project, newProject, scope);
                } catch (DependencyResolutionRequiredException e) {
                    getLog().error(e.getLocalizedMessage());
                    getLog().info("Re-parse aborted due to dependency resolution problems");
                    continue;
                }
                if (classPathChanged) {
                    getLog().info("Effective classpath of " + project.getId() + " has changed.");
                } else {
                    getLog().info("Effective classpath is unchanged.");
                }

                getLog().debug("Comparing effective overlays of new and old models");
                boolean overlaysChanged;
                try {
                    overlaysChanged = overlaysEqual(project, newProject);
                } catch (OverConstrainedVersionException e) {
                    getLog().error(e.getLocalizedMessage());
                    getLog().info("Re-parse aborted due to dependency resolution problems");
                    continue;
                } catch (ArtifactFilterException e) {
                    getLog().error(e.getLocalizedMessage());
                    getLog().info("Re-parse aborted due to overlay resolution problems");
                    continue;
                }
                if (overlaysChanged || classPathChanged) {
                    getLog().info("Restarting servlet container to take account of changes...");
                }

                getLog().info("");

                project = newProject;
            }

        }
    }

    private List<MavenProject> buildReactorProjects() throws Exception {
        List<MavenProject> projects = new ArrayList<MavenProject>();
        DefaultProfileManager profileManager = new DefaultProfileManager(session.getContainer(),
                session.getExecutionProperties());
        for (MavenProject p : reactorProjects) {
            projects.add(projectBuilder.build(p.getFile(), localRepository, profileManager));
        }
        return new ProjectSorter(projects).getSortedProjects();
    }

    private boolean classpathsEqual(MavenProject oldProject, MavenProject newProject, String scope)
            throws DependencyResolutionRequiredException {
        int seq = 0;
        List<String> newCP = getClasspathElements(newProject, scope);
        List<String> oldCP = getClasspathElements(oldProject, scope);
        boolean classPathChanged = newCP.size() != oldCP.size();
        for (Iterator<String> i = newCP.iterator(), j = oldCP.iterator(); i.hasNext() || j.hasNext(); ) {
            String left = i.hasNext() ? i.next() : "(empty)";
            String right = j.hasNext() ? j.next() : "(empty)";
            if (!StringUtils.equals(left, right)) {
                getLog().debug("classpath[" + seq + "]");
                getLog().debug("  old = " + left);
                getLog().debug("  new = " + right);
                classPathChanged = true;
            }
            seq++;
        }
        return classPathChanged;
    }

    private MavenProject findProject(List<MavenProject> newReactorProjects, MavenProject oldProject) {
        final String targetId = oldProject.getId();
        for (MavenProject newProject : newReactorProjects) {
            if (targetId.equals(newProject.getId())) {
                return newProject;
            }
        }
        return null;
    }

    private boolean buildPlanEqual(List<MavenProject> newPlan, List<MavenProject> oldPlan) {
        if (newPlan.size() != oldPlan.size()) return false;
        int seq = 0;
        for (Iterator<MavenProject> i = newPlan.iterator(), j = oldPlan.iterator(); i.hasNext() && j.hasNext(); ) {
            MavenProject left = i.next();
            MavenProject right = j.next();
            getLog().debug(
                    "[" + (seq++) + "] = " + left.equals(right) + (left == right ? " same" : " diff") + " : "
                            + left.getName() + "[" + left.getDependencies().size() + "], " + right.getName()
                            + "["
                            + right.getDependencies().size() + "]");
            if (!left.equals(right)) {
                return false;
            }
            if (left.getDependencies().size() != right.getDependencies().size()) {
                getLog().info("Dependency tree of " + left.getId() + " has been modified");
            }
        }
        return true;
    }

    private boolean overlaysEqual(MavenProject oldProject, MavenProject newProject)
            throws ArtifactFilterException, OverConstrainedVersionException {
        boolean overlaysChanged = false;
        Set<Artifact> newOA = getOverlayArtifacts(newProject, scope);
        Set<Artifact> oldOA = getOverlayArtifacts(oldProject, scope);
        overlaysChanged = newOA.size() != oldOA.size();
        for (Artifact n : newOA) {
            boolean found = false;
            for (Artifact o : oldOA) {
                if (StringUtils.equals(n.getArtifactId(), o.getArtifactId()) && StringUtils
                        .equals(n.getGroupId(), o.getGroupId())) {
                    if (o.getSelectedVersion().equals(n.getSelectedVersion())) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                getLog().debug("added overlay artifact: " + n);
                overlaysChanged = true;
            }
        }
        for (Artifact o : oldOA) {
            boolean found = false;
            for (Artifact n : newOA) {
                if (StringUtils.equals(n.getArtifactId(), o.getArtifactId()) && StringUtils
                        .equals(n.getGroupId(), o.getGroupId())) {
                    if (o.getSelectedVersion().equals(n.getSelectedVersion())) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                getLog().debug("removed overlay artifact: " + o);
                overlaysChanged = true;
            }
        }
        if (overlaysChanged) {
            getLog().info("Effective overlays of " + oldProject.getId() + " have changed.");
        } else {
            getLog().debug("Effective overlays are unchanged.");
        }
        return overlaysChanged;
    }

    @SuppressWarnings("unchecked")
    private Set<Artifact> getOverlayArtifacts(MavenProject project, String scope) throws ArtifactFilterException {
        FilterArtifacts filter = new FilterArtifacts();

        filter.addFilter(new ProjectTransitivityFilter(project.getDependencyArtifacts(), false));

        filter.addFilter(new ScopeFilter(scope, ""));

        filter.addFilter(new TypeFilter("jszip", ""));

        return filter.filter(project.getArtifacts());
    }

    private long getPomsLastModified() {
        long result = Long.MIN_VALUE;
        for (MavenProject p : reactorProjects) {
            result = Math.max(p.getFile().lastModified(), result);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> getClasspathElements(MavenProject project, String scope)
            throws DependencyResolutionRequiredException {
        if ("test".equals(scope)) {
            return (List<String>) project.getTestClasspathElements();
        }
        if ("compile".equals(scope)) {
            return (List<String>) project.getCompileClasspathElements();
        }
        if ("runtime".equals(scope)) {
            return (List<String>) project.getRuntimeClasspathElements();
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Set<Artifact> resolve(MavenProject newProject, String scope)
            throws MojoExecutionException {
        if (projectDependenciesResolver == null) {
            getLog().debug("Using Maven 2 strategy to resolve project dependencies");
            Set<Artifact> resolvedArtifacts;
            try {
                ArtifactResolutionResult result =
                        artifactResolver.resolveTransitively(newProject.getDependencyArtifacts(),
                                artifactFactory.createBuildArtifact(newProject.getGroupId(),
                                        newProject.getArtifactId(),
                                        newProject.getVersion(), newProject.getPackaging()),
                                newProject.getManagedVersionMap(),
                                session.getLocalRepository(),
                                newProject.getRemoteArtifactRepositories(),
                                metadataSource,
                                new ScopeArtifactFilter(scope));
                resolvedArtifacts = (Set<Artifact>) result.getArtifacts();
            } catch (MultipleArtifactsNotFoundException me) {
                throw new MojoExecutionException(me.getMessage(), me);
            } catch (ArtifactNotFoundException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } catch (ArtifactResolutionException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
            return resolvedArtifacts;
        } else {
            getLog().debug("Using Maven e strategy to resolve project dependencies");

            try {
                return (Set<Artifact>) projectDependenciesResolver.getClass()
                        .getMethod("resolve", MavenProject.class, Collection.class, MavenSession.class)
                        .invoke(projectDependenciesResolver, newProject, Collections.singletonList(scope), session);
            } catch (IllegalAccessException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } catch (InvocationTargetException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } catch (NoSuchMethodException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }
}
