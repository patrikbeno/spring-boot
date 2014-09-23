/*
 * Copyright 2012-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.boot.loader.mvn;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.JarFileArchive;

/**
 * @author Patrik Beno
 */
public class MvnArtifact {

	// compares artifacts by group:artifact:version:packaging:classifier
	static public Comparator<MvnArtifact> COMPARATOR = new Comparator<MvnArtifact>() {
		@Override
		public int compare(MvnArtifact o1, MvnArtifact o2) {
			return o1.asString().compareTo(o2.asString());
		}
	};

	// note: naming convention broken intentionally for simplicity; these enums are also
	// used for debug logging, and
	// the output looks better in CamelCase that UPPER_CASE.
	static public enum Status {
		NotModified, Downloaded, Updated, Cached, Offline, NotFound, Invalid
	}

	private String groupId;

	private String artifactId;

	private String version;

	private String classifier;

	private String packaging;

	private String resolvedSnapshotVersion; // e.g. 1.0-SNAPSHOT (logical) ->
											// 1.0.20140131.123456 (timestamped)

	private Status status; // resolution status

	private URL source; // origin

	private File file; // cached

	private Throwable error; // resolver error, if any (for reporting purposes)

	static public MvnArtifact create(String groupId, String artifactId, String version, String type, String classifier) {
		MvnArtifact mvnuri = new MvnArtifact();
		mvnuri.groupId = groupId;
		mvnuri.artifactId = artifactId;
		mvnuri.version = version;
		mvnuri.packaging = type;
		mvnuri.classifier = classifier;
        mvnuri.fixupExplicitSnapshotVersion();
		return mvnuri;
	}

	/**
	 * Parses given Maven artifact URI. URI syntax is {@code groupId}
	 * :{artifactId}:{version}[:{packaging}[:{classifier}]]}. Packaging can be omitted,
	 * and in such case it defaults to "jar".
	 * <p>
	 * (Usually only group:artifact:version need to be specified.)
	 * @param uri
	 * @return
	 */
	static public MvnArtifact parse(String uri) {

		String[] parts = uri.split(":");

		if (parts.length < 3 || parts.length > 5) {
			throw new MvnLauncherException(
					"Invalid Maven URI. Expected {groupId}:{artifactId}:{version}[:{packaging}[:{classifier}]]. "
                    + "Found: " + uri);
		}

		MvnArtifact mvnuri = new MvnArtifact();

		// first 3 parts are mandatory
		mvnuri.groupId = parts[0];
		mvnuri.artifactId = parts[1];
		mvnuri.version = parts[2];
		// optional, defaults to "jar"
		mvnuri.packaging = (parts.length > 3) ? parts[3] : "jar";
		// optional, defaults to null
		mvnuri.classifier = (parts.length > 4) ? parts[4] : null;
        mvnuri.fixupExplicitSnapshotVersion();

		return mvnuri;
	}

	protected MvnArtifact() {
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

	public String getClassifier() {
		return classifier;
	}

	public String getPackaging() {
		return packaging;
	}

	public String getResolvedSnapshotVersion() {
		return resolvedSnapshotVersion;
	}

	public void setResolvedSnapshotVersion(String resolvedSnapshotVersion) {
		this.resolvedSnapshotVersion = resolvedSnapshotVersion;
	}

	// /

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public void setSource(URL source) {
		this.source = source;
	}

	public URL getSource() {
		return source;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public File getFile() {
		return file;
	}

	public Throwable getError() {
		return error;
	}

	public void setError(Throwable error) {
		this.error = error;
	}

	// /

	Archive getArchive() {
		try {
			return new JarFileArchive(file);
		}
		catch (IOException e) {
			throw new MvnLauncherException(e, "Cannot create archive for " + file);
		}
	}

	public boolean isUpdated() {
		switch (getStatus()) {
		case Downloaded:
		case Updated:
			return true;
		default:
			return false;
		}
	}

	public boolean isSnapshot() {
		return version.endsWith("-SNAPSHOT");
	}

	public boolean isRelease() {
		return !isSnapshot();
	}

    private void fixupExplicitSnapshotVersion() {
        Pattern pattern = Pattern.compile("(.*)-\\p{Digit}{8}\\.\\p{Digit}{6}-\\p{Digit}+");
        Matcher m = pattern.matcher(version);
        if (m.matches()) {
            resolvedSnapshotVersion = version;
            version = m.replaceFirst("$1-SNAPSHOT");
        }
    }

	// /

	public boolean isError() {
		switch (status) {
		case NotFound:
		case Invalid:
			return true;
		default:
			return false;
		}
	}

	public boolean isWarning() {
		return false;
	}

	public String asString() {
		String resolvedVersion = Objects.toString(resolvedSnapshotVersion, version);
		return (classifier != null) ? String.format("%s:%s:%s:%s:%s", groupId,
				artifactId, resolvedVersion, packaging, classifier) : String.format(
				"%s:%s:%s:%s", groupId, artifactId, resolvedVersion, packaging);
	}

	/**
	 * Returns the relative path of this artifact in Maven repository
	 * @return
	 */
	public String getPath() {
		String sversion = (resolvedSnapshotVersion != null && !resolvedSnapshotVersion
				.isEmpty()) ? resolvedSnapshotVersion : version;
		String path = (classifier != null) ? String.format(
				"%1$s/%2$s/%3$s/%2$s-%4$s-%5$s.%6$s", groupId.replace('.', '/'),
				artifactId, version, sversion, classifier, packaging) : String.format(
				"%1$s/%2$s/%3$s/%2$s-%4$s.%5$s", groupId.replace('.', '/'), artifactId,
				version, sversion, packaging);
		return path;

	}

    public URL getUrl() {
        try {
            return new URL(source, getPath());
        } catch (MalformedURLException e) {
            throw new UnsupportedOperationException(e);
        }
    }

	@Override
	public String toString() {
		return asString();
	}

}
