package org.jszip.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.util.List;

@Mojo(name = "compile-sass", defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class CompileSASSMojo extends AbstractPseudoFileSystemProcessorMojo {

    /**
     * Skip compilation.
     */
    @Parameter(property = "jszip.sass.skip", defaultValue = "false")
    private boolean sassSkip;

    /**
     * Indicates whether the build will continue even if there are compilation errors.
     */
    @Parameter(property = "jszip.sass.failOnError", defaultValue = "true")
    private boolean sassFailOnError;

    /**
     * A list of &lt;include&gt; elements specifying the sass files (by pattern) that should be included in
     * processing.
     */
    @Parameter
    private List<String> sassIncludes;

    /**
     * A list of &lt;exclude&gt; elements specifying the sass files (by pattern) that should be excluded from
     * processing.
     */
    @Parameter
    private List<String> sassExcludes;

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (sassSkip) {
            getLog().info("SASS compilation skipped.");
            return;
        }
        if (webappDirectory.isFile()) {
            throw new MojoExecutionException("Webapp directory '" + webappDirectory + "' is not a directory");
        }
        if (!webappDirectory.isDirectory()) {
            getLog().info("Webapp directory '" + webappDirectory + " does not exist. Nothing to do.");
            return;
        }
        throw new UnsupportedOperationException("Unimplemented");
    }
}
