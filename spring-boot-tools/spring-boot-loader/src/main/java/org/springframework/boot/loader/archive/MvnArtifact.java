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
package org.springframework.boot.loader.archive;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	private String groupId;

	private String artifactId;

	private String version;

	private String classifier;

	private String packaging;


	public MvnArtifact(String groupId, String artifactId, String version, String type, String classifier) {
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
		this.packaging = type;
		this.classifier = classifier;
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
	public MvnArtifact(String uri) {

		String[] parts = uri.split(":");

		if (parts.length < 3 || parts.length > 5) {
			throw new IllegalArgumentException(
					"Invalid Maven URI. Expected {groupId}:{artifactId}:{version}[:{packaging}[:{classifier}]]. "
                    + "Found: " + uri);
		}

		// first 3 parts are mandatory
		this.groupId = parts[0];
		this.artifactId = parts[1];
		this.version = parts[2];
		// optional, defaults to "jar"
		this.packaging = (parts.length > 3) ? parts[3] : "jar";
		// optional, defaults to null
		this.classifier = (parts.length > 4) ? parts[4] : null;
	}

	protected MvnArtifact() {
	}

	public String getGroupId() {
		return groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	protected void setVersion(String version) {
		this.version = version;
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

	///

	public boolean isSnapshot() {
		return version.endsWith("-SNAPSHOT");
	}

	public boolean isRelease() {
		return !isSnapshot();
	}

	///

	public String asString() {
		String resolvedVersion = version;
		return (classifier != null) ? String.format("%s:%s:%s:%s:%s", groupId,
				artifactId, resolvedVersion, packaging, classifier) : String.format(
				"%s:%s:%s:%s", groupId, artifactId, resolvedVersion, packaging);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		MvnArtifact that = (MvnArtifact) o;

		if (!artifactId.equals(that.artifactId)) return false;
		if (classifier != null ? !classifier.equals(that.classifier) : that.classifier != null) return false;
		if (!groupId.equals(that.groupId)) return false;
		if (packaging != null ? !packaging.equals(that.packaging) : that.packaging != null) return false;
		if (!version.equals(that.version)) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = groupId.hashCode();
		result = 31 * result + artifactId.hashCode();
		result = 31 * result + version.hashCode();
		result = 31 * result + (classifier != null ? classifier.hashCode() : 0);
		result = 31 * result + (packaging != null ? packaging.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return asString();
	}

}
