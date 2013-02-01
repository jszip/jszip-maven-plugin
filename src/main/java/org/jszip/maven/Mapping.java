package org.jszip.maven;

import org.apache.maven.artifact.Artifact;
import org.codehaus.plexus.util.StringUtils;

/**
 * Represents a mapping
 */
public class Mapping {
    private String select;
    private String path;

    public Mapping() {
    }

    public Mapping(String select, String path) {
        this.select = select;
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getSelect() {
        return select;
    }

    public void setSelect(String select) {
        this.select = select;
    }

    public boolean isMatch(Artifact artifact) {
        if (StringUtils.isBlank(select)) {
            return true;
        }
        int index = select.indexOf(':');
        String groupId = index == -1 ? "*" : select.substring(0, index);
        String artifactId = index == -1 ? select : select.substring(index + 1);
        return matches(groupId, artifact.getGroupId()) && matches(artifactId, artifact.getArtifactId());
    }

    private boolean matches(String pattern, String token) {
        boolean matches;

        // support full wildcard and implied wildcard
        if ("*".equals(pattern) || pattern.length() == 0) {
            return true;
        }
        // support contains wildcard
        if (pattern.startsWith("*") && pattern.endsWith("*")) {
            final String contains = pattern.substring(1, pattern.length() - 1);

            return token.contains(contains);
        }
        // support leading wildcard
        if (pattern.startsWith("*")) {
            final String suffix = pattern.substring(1, pattern.length());

            return token.endsWith(suffix);
        }
        // support trailing wildcard
        if (pattern.endsWith("*")) {
            final String prefix = pattern.substring(0, pattern.length() - 1);

            return token.startsWith(prefix);
        }
        // support wildcards in the middle of a pattern segment
        if (pattern.indexOf('*') > -1) {
            String[] parts = pattern.split("\\*");
            int lastPartEnd = -1;

            for (String part : parts) {
                int idx = token.indexOf(part);
                if (idx <= lastPartEnd) {
                    return false;
                }

                lastPartEnd = idx + part.length();
            }

            return true;
        }
        return token.equals(pattern);

    }

    public static String getArtifactPath(Mapping[] mappings, Artifact artifact) {
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


}
