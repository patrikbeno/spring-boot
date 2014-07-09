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
package org.springframework.boot.loader;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.JarFileArchive;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.Manifest;

import static org.springframework.boot.loader.MvnLauncherCfg.*;

/**
 * Specialized implementation of the {@code Launcher} that intelligently downloads dependencies from configured
 * Maven repository
 *
 *
 * @see org.springframework.boot.loader.ExecutableArchiveLauncher
 *
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class MvnLauncher extends ExecutableArchiveLauncher {

	static public final String MF_DEPENDENCIES = "Spring-Boot-Dependencies";

	// if -DMvnLauncher.artifact is defined, its Start-Class overrides the value defined by this archive
	// lazily resolved in #getArtifacts(MvnArtifact)
	String mainClass;

	/**
	 * If any external artifact is defined as main entry point, this method returns {@code Start-Class} defined in its
	 * manifest. Otherwise, original implementation is called which returns {@code Start-Class} as defined in this
	 * archive's manifest.
	 * @see org.springframework.boot.loader.MvnLauncherCfg#artifact
	 */
	@Override
	protected String getMainClass() throws Exception {
		return (mainClass != null) ? mainClass : super.getMainClass();
	}

	/**
	 * Always returns false: there are note nested archives.
	 */
	@Override
	protected boolean isNestedArchive(Archive.Entry entry) {
		return false; // unsupported
	}

	/**
	 * Reads {@link #MF_DEPENDENCIES Spring-Boot-Dependencies} manifest attribute from this archive or from Maven artifact
	 * defined by {@code -DMvnLauncher.artifact}, and resolves all the Maven artifact references using
	 * {@code MvnRepositoryConnector}. Remote artifacts are downloaded and cached in repository layout
	 * in MvnLauncher's cache which is used to construct the final classpath.
	 * @see org.springframework.boot.loader.MvnRepositoryConnector
	 * @see org.springframework.boot.loader.MvnLauncherCfg#artifact
	 * @see MvnLauncherCfg#cache
	 * @see MvnLauncherCfg#cacheFileProtocol
	 */
	@Override
	protected List<Archive> getClassPathArchives() throws Exception {

		MvnRepositoryConnector connector = new MvnRepositoryConnector();
		try {
			// fail-fast: first, verify repository connection, avoid re-using invalid one to resolve multiple artifacts.
			connector.verifyConnection();

			// get list of dependencies from external root artifact (if defined), or current archive (default)
			List<MvnArtifact> artifacts = artifact.isDefined()
					? getArtifacts(artifact.asMvnArtifact())
					: getArtifacts();

			// resolve/download/update/cache all referenced artifacts
			connector.resolveArtifacts(artifacts);

			// create list of archives for all resolved and available artifacts
			List<Archive> archives = new LinkedList<Archive>();
			archives.add(getArchive()); // current archive first
			for (MvnArtifact ma : artifacts) {
				if (ma.getFile() != null && ma.getFile().exists()) {
					archives.add(new JarFileArchive(ma.getFile()));
				}
			}

			// report, if debug is enabled
			if (debug.isEnabled() && showClasspath.isEnabled()) {
				System.out.println("## Classpath Archives (in actual order):");
				for (Archive a : archives) {
					System.out.println(a.toString());
				}
			}

			return archives;

		} finally {
			connector.close();
		}
	}

	/**
	 * Load list of dependencies for a current archive, as defined in its manifest.
	 */
	protected List<MvnArtifact> getArtifacts() throws Exception {
		return getArtifacts(getArchive());
	}

	/**
	 * Load list of Maven dependencies from manifest of a specified archive
	 */
	private List<MvnArtifact> getArtifacts(Archive archive) {
		try {
			Manifest mf = archive.getManifest();
			String[] manifestDependencies = mf.getMainAttributes().getValue(MF_DEPENDENCIES).split(",");
			List<MvnArtifact> artifacts = toArtifacts(manifestDependencies);
			return artifacts;
		} catch (IOException e) {
			throw new MvnLauncherException("Cannot resolve artifacts for archive "+archive, e);
		}
	}

	/**
	 * Resolves specified Maven artifact and reads both list of dependencies and main class name from its manifest.
	 */
	protected List<MvnArtifact> getArtifacts(MvnArtifact ma) throws Exception {
		if (debug.isEnabled()) {
			System.out.println("## Resolving main artifact:");
		}
		File f = new MvnRepositoryConnector().resolve(ma);
		if (!f.exists()) {
			throw new MvnLauncherException("Cannot resolve " + ma.asString() + "" + ma.getError());
		}
		JarFileArchive jar = new JarFileArchive(f);
		mainClass = jar.getMainClass();  // simple but inappropriate place to do this
		List<MvnArtifact> artifacts = new LinkedList<MvnArtifact>();
		artifacts.add(ma);
		artifacts.addAll(getArtifacts(jar));
		return artifacts;
	}

	// parses Maven URIs and converts them into list of Maven artifacts
	private List<MvnArtifact> toArtifacts(String[] strings) {
		if (strings == null) { return Collections.emptyList(); }
		List<MvnArtifact> result = new ArrayList<MvnArtifact>();
		for (String s : strings) {
			if (s == null || s.trim().isEmpty()) { continue; }
			result.add(MvnArtifact.parse(s));
		}
		return result;
	}

	/**
	 * Entry point used by build plugin (Main-Class manifest attribute). This can be overriden in build by specifying
	 * {@code launcherClass} parameter.
	 * @param args
	 */
	static public void main(String[] args) {
		new MvnLauncher().launch(args);
	}


	/**
	 * Alternative entry point: first argument is the main artifact Maven URI
	 * @see org.springframework.boot.loader.MvnLauncherCfg#artifact
	 */
	static public void main(Class<?> main, String[] args) {

		// extract the main artifact Maven URI from args
		String artifact = (args.length > 0) ? args[0] : null;

		if (artifact == null) {
			System.err.printf("Usage: %s {groupId}:{artifactId}:{version}[:{packaging}[:{classifier}]] ... %n", main.getName());
			System.exit(-1);
		}

		// and save it in MvnLauncher.artifact property
		// don't use MvnLauncherCfg.artifact enum reference here, we don't want to trigger the configuration sequence yet
		System.setProperty("MvnLauncher.artifact", artifact);

		// remove the first argument from args
		String[] newargs = new String[args.length - 1];
		System.arraycopy(args, 1, newargs, 0, newargs.length);

		// and launch the bootstrap sequence
		new MvnLauncher().launch(newargs);
	}

}
