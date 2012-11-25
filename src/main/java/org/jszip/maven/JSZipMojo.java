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
import org.apache.maven.model.Contributor;
import org.apache.maven.model.Developer;
import org.apache.maven.model.License;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.components.io.resources.AbstractPlexusIoResource;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * Produces a JSZip formatted zip file.
 */
@Mojo(name = "jszip", defaultPhase = LifecyclePhase.PACKAGE)
public class JSZipMojo extends AbstractJSZipMojo {

    /**
     * Directory containing the classes.
     */
    @Parameter(defaultValue = "src/main/js", required = true)
    private File contentDirectory;

    /**
     * Directory containing the resources.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File resourcesDirectory;

    /**
     * Directory containing the generated ZIP.
     */
    @Parameter(defaultValue = "${project.build.directory}", required = true)
    private File outputDirectory;

    /**
     * Name of the generated ZIP.
     */
    @Parameter(property = "zip.finalName", defaultValue = "${project.build.finalName}", required = true)
    private String finalName;

    /**
     * Classifier to add to the artifact generated. If given, the artifact will be an attachment instead.
     */
    @Parameter
    private String classifier;

    /**
     * The Jar archiver.
     *
     * @component role="org.codehaus.plexus.archiver.Archiver" roleHint="zip"
     */
    @Component(role = org.codehaus.plexus.archiver.Archiver.class, hint = "zip")
    private ZipArchiver zipArchiver;

    /**
     * Include or not empty directories
     */
    @Parameter(property = "zip.includeEmptyDirs", defaultValue = "false")
    private boolean includeEmptyDirs;

    /**
     * Whether creating the archive should be forced.
     */
    @Parameter(property = "zip.forceCreation", defaultValue = "false")
    private boolean forceCreation;

    /**
     * Adding pom.xml and pom.properties to the archive.
     */
    @Parameter(property = "zip.addMavenDescriptor", defaultValue = "true")
    private boolean addMavenDescriptor;

    /**
     * Maven ProjectHelper.
     */
    @Component
    private MavenProjectHelper projectHelper;

    public File getContentDirectory() {
        return contentDirectory;
    }

    public File getResourcesDirectory() {
        return resourcesDirectory;
    }

    protected File getZipFile(File basedir, String finalName, String classifier) {
        if (classifier == null) {
            classifier = "";
        } else if (classifier.trim().length() > 0 && !classifier.startsWith("-")) {
            classifier = "-" + classifier;
        }

        return new File(basedir, finalName + classifier + ".zip");
    }

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute()
            throws MojoExecutionException, MojoFailureException {
        try {

            File zipFile = getZipFile(outputDirectory, finalName, classifier);

            zipArchiver.setDestFile(zipFile);
            zipArchiver.setIncludeEmptyDirs(includeEmptyDirs);
            zipArchiver.setCompress(true);
            zipArchiver.setForced(forceCreation);

            if (addMavenDescriptor) {
                if (project.getArtifact().isSnapshot()) {
                    project.setVersion(project.getArtifact().getVersion());
                }

                String groupId = project.getGroupId();

                String artifactId = project.getArtifactId();

                zipArchiver.addFile(project.getFile(), "META-INF/maven/" + groupId + "/" + artifactId + "/pom.xml");
                zipArchiver.addResource(new PomPropertiesResource(project),
                        "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties",
                        zipArchiver.getOverrideFileMode());
            }
            zipArchiver.addResource(new PackageJsonResource(project), "package.json",
                    zipArchiver.getOverrideFileMode());
            if (contentDirectory.isDirectory()) {
                zipArchiver.addDirectory(contentDirectory);
            }
            if (resourcesDirectory.isDirectory()) {
                zipArchiver.addDirectory(resourcesDirectory);
            }
            zipArchiver.createArchive();

            if (StringUtils.isEmpty(classifier)) {
                project.getArtifact().setFile(zipFile);
            } else {
                boolean found = false;
                for (Artifact artifact : project.getAttachedArtifacts()) {
                    if (StringUtils.equals(artifact.getGroupId(), project.getGroupId())
                            && StringUtils.equals(artifact.getArtifactId(), project.getArtifactId())
                            && StringUtils.equals(artifact.getVersion(), project.getVersion())
                            && StringUtils.equals(artifact.getClassifier(), classifier)
                            && StringUtils.equals(artifact.getType(), JSZIP_TYPE)) {
                        artifact.setFile(zipFile);
                        found = true;
                    }
                }
                if (!found) {
                    projectHelper.attachArtifact(project, JSZIP_TYPE, classifier, zipFile);
                }
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Error assembling ZIP", e);
        }

    }

    private static class PackageJsonResource extends AbstractPlexusIoResource {
        private final byte[] bytes;
        private final long lastModified;

        public PackageJsonResource(MavenProject project) throws IOException, MojoExecutionException {
            this.lastModified = project.getFile().lastModified();
            Map<String, Object> p = new TreeMap<String, Object>();
            p.put("name", project.getGroupId() + "." + project.getArtifactId());
            p.put("version", project.getVersion());
            addFirstNotEmpty(p, "description", project.getDescription());
            addFirstNotEmpty(p, "homepage", project.getUrl());
            if (project.getDevelopers() != null && !project.getDevelopers().isEmpty()) {
                List<Object> devs = new ArrayList<Object>();
                for (Developer d : (List<Developer>) project.getDevelopers()) {
                    Map<String, Object> dev = new TreeMap<String, Object>();
                    addFirstNotEmpty(dev, "name", d.getName(), d.getId(), d.getEmail());
                    addFirstNotEmpty(dev, "email", d.getEmail());
                    addFirstNotEmpty(dev, "web", d.getUrl());
                    if (dev.containsKey("name")) {
                        devs.add(dev);
                    }
                }
                p.put("maintainers", devs);
            }
            if (project.getContributors() != null && !project.getContributors().isEmpty()) {
                List<Object> contribs = new ArrayList<Object>();
                for (Contributor c : (List<Contributor>) project.getContributors()) {
                    Map<String, Object> contrib = new TreeMap<String, Object>();
                    addFirstNotEmpty(contrib, "name", c.getName(), c.getEmail());
                    addFirstNotEmpty(contrib, "email", c.getEmail());
                    addFirstNotEmpty(contrib, "web", c.getUrl());
                    if (contrib.containsKey("name")) {
                        contribs.add(contrib);
                    }
                }
                p.put("contributors", contribs);
            }
            if (project.getIssueManagement() != null) {
                addFirstNotEmpty(p, "bugs", project.getIssueManagement().getUrl());
            }
            if (project.getLicenses() != null && !project.getLicenses().isEmpty()) {
                List<Object> licenses = new ArrayList<Object>();
                for (License l : (List<License>) project.getLicenses()) {
                    Map<String, Object> license = new TreeMap<String, Object>();
                    addFirstNotEmpty(license, "type", l.getName());
                    addFirstNotEmpty(license, "url", l.getUrl());
                    licenses.add(license);
                }
                p.put("licenses", licenses);

            }
            FilterArtifacts filter = new FilterArtifacts();

            filter.addFilter(new ProjectTransitivityFilter(project.getDependencyArtifacts(), true));

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

            Map<String, String> dependencies = new LinkedHashMap<String, String>();
            for (Artifact artifact : artifacts) {
                dependencies.put(artifact.getGroupId() + "." + artifact.getArtifactId(), artifact.getVersion());
            }
            p.put("dependencies", dependencies);

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                ObjectMapper m = new ObjectMapper();
                m.setSerializationConfig(m.getSerializationConfig().with(SerializationConfig.Feature.INDENT_OUTPUT));
                m.writeValue(os, p);
            } finally {
                IOUtil.close(os);
            }
            bytes = os.toByteArray();

        }

        private void addFirstNotEmpty(Map<String, Object> map, String name, String... values) {
            for (String value : values) {
                if (StringUtils.isNotEmpty(value)) {
                    map.put(name, value);
                    return;
                }
            }
        }

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public boolean isExisting() {
            return true;
        }

        @Override
        public long getLastModified() {
            return lastModified;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        public URL getURL() throws IOException {
            return null;
        }

        public InputStream getContents() throws IOException {
            return new ByteArrayInputStream(bytes);
        }
    }

    private static class PomPropertiesResource extends AbstractPlexusIoResource {
        private static final String GENERATED_BY_MAVEN = "Generated by Maven";
        private final byte[] bytes;
        private final MavenProject project;

        public PomPropertiesResource(MavenProject project) throws IOException {
            this.project = project;

            Properties p = new Properties();

            p.setProperty("groupId", project.getGroupId());

            p.setProperty("artifactId", project.getArtifactId());

            p.setProperty("version", project.getVersion());

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                p.store(os, GENERATED_BY_MAVEN);
            } finally {
                IOUtil.close(os);
            }

            bytes = os.toByteArray();
        }

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public boolean isExisting() {
            return true;
        }

        @Override
        public long getLastModified() {
            return project.getFile().lastModified();
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        public URL getURL() throws IOException {
            return null;
        }

        public InputStream getContents() throws IOException {
            return new ByteArrayInputStream(bytes);
        }
    }
}
