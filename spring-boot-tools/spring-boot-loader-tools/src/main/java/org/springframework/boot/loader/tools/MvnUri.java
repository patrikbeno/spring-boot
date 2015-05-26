package org.springframework.boot.loader.tools;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class MvnUri {

    private String groupId;
    private String artifactId;
    private String version;
    private String packaging;
    private String classifier;

    public MvnUri(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, "jar", null);
    }

    public MvnUri(String groupId, String artifactId, String version, String packaging, String classifier) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.packaging = packaging;
        this.classifier = classifier;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getPackaging() {
        return packaging;
    }

    public String getClassifier() {
        return classifier;
    }

    /**
     * @return {@code groupId:artifactId:version[:packaging[:classifier]]}
     */
    public String asString() {
        StringBuilder sb = new StringBuilder()
                .append(groupId).append(':')
                .append(artifactId).append(':')
                .append(version);
        if (!"jar".equals(packaging) || classifier != null) {
            sb.append(':').append(packaging);
        }
        if (classifier != null) {
            sb.append(':').append(classifier);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return asString();
    }
}
