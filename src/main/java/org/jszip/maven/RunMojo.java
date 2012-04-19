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

import org.apache.maven.ProjectDependenciesResolver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.DuplicateProjectException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectSorter;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

/**
 * Starts a Jetty servlet container with resources resolved from the reactor projects to enable live editing of those
 * resources and pom and classpath scanning to restart the servlet container when the classpath is modified. Note that
 * if the poms are modified in such a way that the reactor build plan is modified, we have no choice but to stop the
 * servlet container and require the maven session to be restarted, but best effort is made to ensure that restart
 * is only when required.
 *
 * @goal run
 * @execute phase="test-compile"
 * @requiresDependencyResolution compile+runtime
 */
public class RunMojo extends AbstractJSZipMojo {
    /**
     * If true, the &lt;testOutputDirectory&gt;
     * and the dependencies of &lt;scope&gt;test&lt;scope&gt;
     * will be put first on the runtime classpath.
     *
     * @parameter alias="useTestClasspath" default-value="false"
     */
    private boolean useTestScope;


    /**
     * The default location of the web.xml file. Will be used
     * if &lt;webApp&gt;&lt;descriptor&gt; is not set.
     *
     * @parameter expression="${maven.war.webxml}"
     * @readonly
     */
    private String webXml;


    /**
     * The directory containing generated classes.
     *
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     */
    private File classesDirectory;


    /**
     * The directory containing generated test classes.
     *
     * @parameter expression="${project.build.testOutputDirectory}"
     * @required
     */
    private File testClassesDirectory;

    /**
     * Root directory for all html/jsp etc files
     *
     * @parameter expression="${maven.war.src}"
     */
    private File webAppSourceDirectory;


    /**
     * List of connectors to use. If none are configured
     * then the default is a single SelectChannelConnector at port 8080. You can
     * override this default port number by using the system property jetty.port
     * on the command line, eg:  mvn -Djetty.port=9999 jetty:run. Consider using instead
     * the &lt;jettyXml&gt; element to specify external jetty xml config file.
     *
     * @parameter
     */
    protected Connector[] connectors;

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
    private ProjectBuilder projectBuilder;

    /**
     * @parameter expression="${reactorProjects}"
     * @required
     * @readonly
     */
    protected List<MavenProject> reactorProjects;

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
     * The forked project.
     *
     * @parameter expression="${executedProject}"
     * @required
     * @readonly
     */
    private MavenProject executedProject;

    /**
     * Used to resolve transitive dependencies.
     *
     * @component
     */
    private ProjectDependenciesResolver projectDependenciesResolver;

    /**
     * Maven ProjectHelper.
     *
     * @component
     * @readonly
     */
    private MavenProjectHelper projectHelper;

    private final String scope = "test";
    private final long classpathCheckInterval = TimeUnit.SECONDS.toMillis(10);

    public void execute()
            throws MojoExecutionException, MojoFailureException {
        if (runPackages == null || runPackages.length == 0) {
            runPackages = new String[]{"war"};
        }

        injectMissingArtifacts(project, executedProject);

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
        getLog().info("Starting JSZip run: module " + ArtifactUtils.versionlessKey(project.getGroupId(),
                project.getArtifactId()));
        MavenProject project = this.project;
        long lastClassChange = System.currentTimeMillis();
        long lastPomChange = getPomsLastModified();

        Server server = new Server();
        if (connectors == null || connectors.length == 0) {
            SelectChannelConnector selectChannelConnector = new SelectChannelConnector();
            selectChannelConnector.setPort(8080);
            connectors = new Connector[]{
                    selectChannelConnector
            };
        }
        server.setConnectors(connectors);
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        HandlerCollection handlerCollection = new HandlerCollection(true);
        DefaultHandler defaultHandler = new DefaultHandler();
        handlerCollection.setHandlers(new Handler[]{contexts, defaultHandler});
        server.setHandler(handlerCollection);
        try {
            server.start();
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        List<MavenProject> reactorProjects = this.reactorProjects;
        WebAppContext webAppContext;
        Resource webXml;
        try {
            List<Resource> resources = new ArrayList<Resource>();
            for (Artifact a : getOverlayArtifacts(project, scope)) {
                MavenProject fromReactor = findProject(reactorProjects, a);
                if (fromReactor != null) {
                    File jsDir = new File(fromReactor.getBasedir(), "src/main/js");
                    if (jsDir.isDirectory()) {
                        resources.add(Resource.newResource(jsDir));
                    }
                    // TODO filtering support
                    File resDir = new File(fromReactor.getBasedir(), "src/main/resources");
                    if (resDir.isDirectory()) {
                        resources.add(Resource.newResource(resDir));
                    }
                } else {
                    resources.add(Resource.newResource("jar:" + a.getFile().toURI().toURL() + "!/"));
                }
            }
            if (webAppSourceDirectory == null) {
                webAppSourceDirectory = new File(project.getBasedir(), "src/main/webapp");
            }
            if (webAppSourceDirectory.isDirectory()) {
                resources.add(Resource.newResource(webAppSourceDirectory));
            }
            Collections.reverse(resources);
            final ResourceCollection resourceCollection =
                    new ResourceCollection(resources.toArray(new Resource[resources.size()]));

            webAppContext = new WebAppContext();
            webAppContext.setWar(webAppSourceDirectory.getAbsolutePath());
            webAppContext.setBaseResource(resourceCollection);

            WebAppClassLoader classLoader = new WebAppClassLoader(webAppContext);
            for (String s : getClasspathElements(project, scope)) {
                classLoader.addClassPath(s);
            }
            webAppContext.setClassLoader(classLoader);

            contexts.setHandlers(new Handler[]{webAppContext});
            contexts.start();
            webAppContext.start();
            Resource webInf = webAppContext.getWebInf();
            webXml = webInf != null ? webInf.getResource("web.xml") : null;
        } catch (ArtifactFilterException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (MalformedURLException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        long webXmlLastModified = webXml == null ? 0L : webXml.lastModified();
        try {

            getLog().info("Context started. Will restart if changes to poms detected.");
            long nextClasspathCheck = System.currentTimeMillis() + classpathCheckInterval;
            while (true) {
                long pomsLastModified = getPomsLastModified();
                boolean pomsChanged = lastPomChange < pomsLastModified;
                boolean overlaysChanged = false;
                boolean classPathChanged = webXmlLastModified < (webXml == null ? 0L : webXml.lastModified());
                if (nextClasspathCheck < System.currentTimeMillis()) {
                    long classChange = classpathLastModified(project);
                    if (classChange > lastClassChange) {
                        classPathChanged = true;
                        lastClassChange = classChange;
                    }
                    nextClasspathCheck = System.currentTimeMillis() + classpathCheckInterval;
                }
                if (!classPathChanged && !overlaysChanged && !pomsChanged) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        getLog().debug("Interrupted", e);
                    }
                    continue;
                }
                if (pomsChanged) {
                    getLog().info("Change in poms detected, re-parsing to evaluate impact...");
                    // we will now process this change,
                    // so from now on don't re-process
                    // even if we have issues processing
                    lastPomChange = pomsLastModified;
                    List<MavenProject> newReactorProjects;
                    try {
                        newReactorProjects = buildReactorProjects();
                    } catch (ProjectBuildingException e) {
                        getLog().error(e.getLocalizedMessage(), e);
                        getLog().info("Re-parse aborted due to malformed pom.xml file(s)");
                        continue;
                    } catch (CycleDetectedException e) {
                        getLog().error(e.getLocalizedMessage(), e);
                        getLog().info("Re-parse aborted due to dependency cycle in project model");
                        continue;
                    } catch (DuplicateProjectException e) {
                        getLog().error(e.getLocalizedMessage(), e);
                        getLog().info("Re-parse aborted due to duplicate projects in project model");
                        continue;
                    } catch (Exception e) {
                        getLog().error(e.getLocalizedMessage(), e);
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
                    try {
                        classPathChanged = classPathChanged || classpathsEqual(project, newProject, scope);
                    } catch (DependencyResolutionRequiredException e) {
                        getLog().error(e.getLocalizedMessage(), e);
                        getLog().info("Re-parse aborted due to dependency resolution problems");
                        continue;
                    }
                    if (!classPathChanged) {
                        if (classPathChanged) {
                            getLog().info("Effective classpath of " + project.getId() + " has changed.");
                        } else {
                            getLog().info("Effective classpath is unchanged.");
                        }
                    }

                    getLog().debug("Comparing effective overlays of new and old models");
                    try {
                        overlaysChanged = overlaysEqual(project, newProject);
                    } catch (OverConstrainedVersionException e) {
                        getLog().error(e.getLocalizedMessage(), e);
                        getLog().info("Re-parse aborted due to dependency resolution problems");
                        continue;
                    } catch (ArtifactFilterException e) {
                        getLog().error(e.getLocalizedMessage(), e);
                        getLog().info("Re-parse aborted due to overlay resolution problems");
                        continue;
                    }

                    project = newProject;
                    reactorProjects = newReactorProjects;
                }

                if (!overlaysChanged && !classPathChanged) {
                    continue;
                }
                getLog().info("Restarting context to take account of changes...");
                try {
                    webAppContext.stop();
                } catch (Exception e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }

                if (classPathChanged) {
                    getLog().info("Updating classpath...");
                    try {
                        WebAppClassLoader classLoader = new WebAppClassLoader(webAppContext);
                        for (String s : getClasspathElements(project, scope)) {
                            classLoader.addClassPath(s);
                        }
                        webAppContext.setClassLoader(classLoader);
                    } catch (Exception e) {
                        throw new MojoExecutionException(e.getMessage(), e);
                    }
                }

                if (overlaysChanged) {
                    getLog().info("Updating overlays...");
                    try {
                        List<Resource> resources = new ArrayList<Resource>();
                        for (Artifact a : getOverlayArtifacts(project, scope)) {
                            MavenProject fromReactor = findProject(reactorProjects, a);
                            if (fromReactor != null) {
                                File jsDir = new File(fromReactor.getBasedir(), "src/main/js");
                                if (jsDir.isDirectory()) {
                                    resources.add(Resource.newResource(jsDir));
                                }
                                File resDir = new File(fromReactor.getBasedir(), "src/main/resources");
                                if (resDir.isDirectory()) {
                                    resources.add(Resource.newResource(resDir));
                                }
                            } else {
                                resources.add(Resource.newResource("jar:" + a.getFile().toURI().toURL() + "!/"));
                            }
                        }
                        if (webAppSourceDirectory.isDirectory()) {
                            resources.add(Resource.newResource(webAppSourceDirectory));
                        }
                        Collections.reverse(resources);
                        final ResourceCollection resourceCollection =
                                new ResourceCollection(resources.toArray(new Resource[resources.size()]));

                        webAppContext.setBaseResource(resourceCollection);
                    } catch (Exception e) {
                        throw new MojoExecutionException(e.getMessage(), e);
                    }
                }
                try {
                    webAppContext.start();
                } catch (Exception e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
                webXmlLastModified = webXml == null ? 0L : webXml.lastModified();
                getLog().info("Context restarted.");
            }

        } finally {
            try {
                server.stop();
            } catch (Exception e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

    private void injectMissingArtifacts(MavenProject destination, MavenProject source) {
        if (destination.getArtifact().getFile() == null && source.getArtifact().getFile() != null) {
            getLog().info("Pushing primary artifact from forked execution into current execution");
            destination.getArtifact().setFile(source.getArtifact().getFile());
        }
        for (Artifact executedArtifact : source.getAttachedArtifacts()) {
            String executedArtifactId =
                    (executedArtifact.getClassifier() == null ? "." : "-" + executedArtifact.getClassifier() + ".")
                            + executedArtifact.getType();
            if (StringUtils.equals(executedArtifact.getGroupId(), destination.getGroupId())
                    && StringUtils.equals(executedArtifact.getArtifactId(), destination.getArtifactId())
                    && StringUtils.equals(executedArtifact.getVersion(), destination.getVersion())) {
                boolean found = false;
                for (Artifact artifact : destination.getAttachedArtifacts()) {
                    if (StringUtils.equals(artifact.getGroupId(), destination.getGroupId())
                            && StringUtils.equals(artifact.getArtifactId(), destination.getArtifactId())
                            && StringUtils.equals(artifact.getVersion(), destination.getVersion())
                            && StringUtils.equals(artifact.getClassifier(), executedArtifact.getClassifier())
                            && StringUtils.equals(artifact.getType(), executedArtifact.getType())) {
                        if (artifact.getFile() == null) {
                            getLog().info("Pushing " + executedArtifactId
                                    + " artifact from forked execution into current execution");
                            artifact.setFile(executedArtifact.getFile());
                        }
                        found = true;
                    }
                }
                if (!found) {
                    getLog().info("Attaching " +
                            executedArtifactId
                            + " artifact from forked execution into current execution");
                    projectHelper
                            .attachArtifact(destination, executedArtifact.getType(), executedArtifact.getClassifier(),
                                    executedArtifact.getFile());
                }
            }
        }
    }

    private List<MavenProject> buildReactorProjects() throws Exception {

        List<MavenProject> projects = new ArrayList<MavenProject>();
        for (MavenProject p : reactorProjects) {
            ProjectBuildingRequest request = new DefaultProjectBuildingRequest();

            request.setProcessPlugins(true);
            request.setProfiles(request.getProfiles());
            request.setActiveProfileIds(session.getRequest().getActiveProfiles());
            request.setInactiveProfileIds(session.getRequest().getInactiveProfiles());
            request.setRemoteRepositories(session.getRequest().getRemoteRepositories());
            request.setSystemProperties(session.getSystemProperties());
            request.setUserProperties(session.getUserProperties());
            request.setRemoteRepositories(session.getRequest().getRemoteRepositories());
            request.setPluginArtifactRepositories(session.getRequest().getPluginArtifactRepositories());
            request.setRepositorySession(session.getRepositorySession());
            request.setLocalRepository(localRepository);
            request.setBuildStartTime(session.getRequest().getStartTime());
            request.setResolveDependencies(true);
            request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_STRICT);
            projects.add(projectBuilder.build(p.getFile(), request).getProject());
        }
        return new ProjectSorter(projects).getSortedProjects();
    }

    private long classpathLastModified(MavenProject project) {
        long result = Long.MIN_VALUE;
        try {
            for (String element : getClasspathElements(project, scope)) {
                File elementFile = new File(element);
                if (elementFile.exists()) {
                    result = Math.max(elementFile.lastModified(), result);
                    if (elementFile.isDirectory()) {
                        Stack<Iterator<File>> stack = new Stack<Iterator<File>>();
                        stack.push(contentsAsList(elementFile).iterator());
                        while (!stack.empty()) {
                            Iterator<File> i = stack.pop();
                            while (i.hasNext()) {
                                File file = i.next();
                                result = Math.max(file.lastModified(), result);
                                if (file.isDirectory()) {
                                    stack.push(i);
                                    i = contentsAsList(file).iterator();
                                }
                            }
                        }
                    }
                }
            }
        } catch (DependencyResolutionRequiredException e) {
            // ignore
        }
        return result;

    }

    private static List<File> contentsAsList(File directory) {
        File[] files = directory.listFiles();
        return files == null ? Collections.<File>emptyList() : Arrays.asList(files);
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

    private MavenProject findProject(List<MavenProject> projects, Artifact artifact) {
        for (MavenProject project : projects) {
            if (StringUtils.equals(artifact.getGroupId(), project.getGroupId())
                    && StringUtils.equals(artifact.getArtifactId(), project.getArtifactId())
                    && StringUtils.equals(artifact.getVersion(), project.getVersion())) {
                return project;
            }
        }
        return null;
    }

    private boolean buildPlanEqual(List<MavenProject> newPlan, List<MavenProject> oldPlan) {
        if (newPlan.size() != oldPlan.size()) {
            return false;
        }
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
        boolean overlaysChanged;
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

        filter.addFilter(new TypeFilter(JSZIP_TYPE, ""));

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
            return project.getTestClasspathElements();
        }
        if ("compile".equals(scope)) {
            return project.getCompileClasspathElements();
        }
        if ("runtime".equals(scope)) {
            return project.getRuntimeClasspathElements();
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Set<Artifact> resolve(MavenProject newProject, String scope)
            throws MojoExecutionException {
        try {
            return projectDependenciesResolver.resolve(newProject, Collections.singletonList(scope), session);
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
