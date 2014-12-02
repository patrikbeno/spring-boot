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
package org.springframework.boot.launcher.mvn;

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
public class MvnArtifact extends org.springframework.boot.loader.archive.MvnArtifact {

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


	private String resolvedSnapshotVersion; // e.g. 1.0-SNAPSHOT (logical) ->
	// 1.0.20140131.123456 (timestamped)

	private Status status; // resolution status

	private URL source; // origin

	private File file; // cached

	private Throwable error; // resolver error, if any (for reporting purposes)


	protected MvnArtifact(String groupId, String artifactId, String version, String type, String classifier) {
		super(groupId, artifactId, version, type, classifier);
		fixupExplicitSnapshotVersion();
	}

	public MvnArtifact(String uri) {
		super(uri);
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
			throw new RuntimeException("Cannot create archive for " + file, e);
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
		return getVersion().endsWith("-SNAPSHOT");
	}

	public boolean isRelease() {
		return !isSnapshot();
	}

    private void fixupExplicitSnapshotVersion() {
        Pattern pattern = Pattern.compile("(.*)-\\p{Digit}{8}\\.\\p{Digit}{6}-\\p{Digit}+");
        Matcher m = pattern.matcher(getVersion());
        if (m.matches()) {
            resolvedSnapshotVersion = getVersion();
			setVersion(m.replaceFirst("$1-SNAPSHOT"));
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
		String resolvedVersion = Objects.toString(resolvedSnapshotVersion, getVersion());
		return (getClassifier() != null) ? String.format("%s:%s:%s:%s:%s", getGroupId(),
				getArtifactId(), resolvedVersion, getPackaging(), getClassifier()) : String.format(
				"%s:%s:%s:%s", getGroupId(), getArtifactId(), resolvedVersion, getPackaging());
	}

	/**
	 * Returns the relative path of this artifact in Maven repository
	 * @return
	 */
	public String getPath() {
		String sversion = (resolvedSnapshotVersion != null && !resolvedSnapshotVersion
				.isEmpty()) ? resolvedSnapshotVersion : getVersion();
		String path = (getClassifier() != null) ? String.format(
				"%1$s/%2$s/%3$s/%2$s-%4$s-%5$s.%6$s", getGroupId().replace('.', '/'),
				getArtifactId(), getVersion(), sversion, getClassifier(), getPackaging()) : String.format(
				"%1$s/%2$s/%3$s/%2$s-%4$s.%5$s", getGroupId().replace('.', '/'), getArtifactId(),
				getVersion(), sversion, getPackaging());
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
