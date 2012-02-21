package org.jszip.maven;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * @author stephenc
 * @since 21/02/2012 10:48
 */
public class BuildPlanModifiedException extends MojoExecutionException {
    public BuildPlanModifiedException(String message) {
        super(message);
    }

    public BuildPlanModifiedException(String message, Exception cause) {
        super(message, cause);
    }

    public BuildPlanModifiedException(String message, Throwable cause) {
        super(message, cause);
    }

    public BuildPlanModifiedException(Object source, String shortMessage, String longMessage) {
        super(source, shortMessage, longMessage);
    }
}
