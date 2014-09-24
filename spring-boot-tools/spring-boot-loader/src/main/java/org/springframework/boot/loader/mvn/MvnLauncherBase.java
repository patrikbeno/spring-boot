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

import static org.springframework.boot.loader.mvn.MvnLauncherCfg.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.Manifest;

import org.springframework.boot.loader.ExecutableArchiveLauncher;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.util.Log;
import org.springframework.boot.loader.util.StatusLine;

/**
 * Specialized implementation of the {@code Launcher} that intelligently downloads
 * dependencies from configured URL-based Maven repository used as a distribution site
 * (e.g. Sonatype Nexus)
 *
 * @see org.springframework.boot.loader.ExecutableArchiveLauncher
 *
 * @author Patrik Beno
 */
public abstract class MvnLauncherBase extends ExecutableArchiveLauncher {

	/**
	 * Name of the manifest attribute containing the comma-delimited list Maven URIs
	 * (runtime dependencies, provided build-time)
	 */
	static public final String MF_DEPENDENCIES = "Spring-Boot-Dependencies";

	// if -DMvnLauncher.artifact is defined, its Start-Class overrides the value defined
	// by this archive
	// lazily resolved in #getArtifacts(MvnArtifact)
	private String mainClass;

	/**
	 * If any external artifact is defined as main entry point, this method returns
	 * {@code Start-Class} defined in its manifest. Otherwise, original implementation is
	 * called which returns {@code Start-Class} as defined in this archive's manifest.
	 * @see MvnLauncherCfg#artifact
	 * @see #getClassPathArchives()
	 * @see #getArtifacts(MvnRepositoryConnector, MvnArtifact)
	 */
	@Override
	protected String getMainClass() throws Exception {
		// mainClass is set by #getArtifacts
		return (mainClass != null) ? mainClass : super.getMainClass();
	}

	/**
	 * Reads {@link #MF_DEPENDENCIES Spring-Boot-Dependencies} manifest attribute from
	 * this archive or from Maven artifact defined by {@code -DMvnLauncher.artifact}, and
	 * resolves all the Maven artifact references using {@code MvnRepositoryConnector}.
	 * Remote artifacts are downloaded and cached in repository layout in MvnLauncher's
	 * cache which is used to construct the final classpath.
	 * @see MvnRepositoryConnector
	 * @see MvnLauncherCfg#artifact
	 * @see MvnLauncherCfg#cache
	 * @see MvnLauncherCfg#cacheFileProtocol
	 */
	@Override
	protected List<Archive> getClassPathArchives() throws Exception {
		return getClassPathArchives(artifact.isDefined() ? artifact.asMvnArtifact() : null);
	}

	protected List<Archive> getClassPathArchives(MvnArtifact mvnartifact) throws Exception {
        return getClassPathArchives(mvnartifact, null);
    }

    protected List<Archive> getClassPathArchives(MvnArtifact mvnartifact, List<MvnArtifact> ext) throws Exception {

		MvnRepositoryConnector connector = new MvnRepositoryConnector();
		try {
			// get list of dependencies from external root artifact (if defined), or
			// current archive (default)
			List<MvnArtifact> artifacts = (mvnartifact != null) ? getArtifacts(connector, mvnartifact) : getArtifacts();

            // register also every extra artifact not mentioned in main artifact and its dependencies
            ext = new ArrayList<MvnArtifact>(ext != null ? ext : Collections.<MvnArtifact>emptyList());
            for (MvnArtifact ma : ext) {
                if (!artifacts.contains(ma)) {
                    artifacts.add(ma);
                }
            }

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

			// report class path
			if (showClasspath.asBoolean() && debug.asBoolean()) {
				Log.debug("Classpath Archives (in actual order):");
				for (Archive a : archives) {
                    Log.debug("- %s", a);
                }
            }

			return archives;
		}
		finally {
			connector.close();
            StatusLine.resetLine();
		}
	}

	@Override
	protected void launch(String[] args, String mainClass, ClassLoader classLoader) throws Exception {
		if (!MvnLauncherCfg.execute.asBoolean()) {
			Log.info("Application updated. Execution skipped (%s=false)", MvnLauncherCfg.execute.getPropertyName());
			return;
		}
		if (MvnLauncherCfg.debug.asBoolean()) {
			Log.debug("## Application Arguments:");
			for (String s : args) {
				Log.debug(s);
			}
			Log.debug("##");
		}
        MvnLauncherCfg.export();
		super.launch(args, mainClass, classLoader);
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
			String mfdeps = mf.getMainAttributes().getValue(MF_DEPENDENCIES);
			if (mfdeps == null) {
				throw new MvnLauncherException(String.format(
                        "%s undefined in MANIFEST. This is not SpringBoot MvnLauncher-enabled artifact: %s",
						MF_DEPENDENCIES, archive));
			}
			String[] manifestDependencies = mfdeps.split(",");
			List<MvnArtifact> artifacts = toArtifacts(manifestDependencies);
			return artifacts;
		}
		catch (IOException e) {
			throw new MvnLauncherException(e, "Cannot resolve artifacts for archive " + archive);
		}
	}

	/**
	 * Resolves specified Maven artifact and reads both list of dependencies and main
	 * class name from its manifest.
	 */
	protected List<MvnArtifact> getArtifacts(MvnRepositoryConnector connector, MvnArtifact ma) throws Exception {
        Log.debug("## Resolving main artifact");
		StatusLine.push("Resolving %s", ma);
        try {
            File f = connector.resolve(ma);
            if (!f.exists()) {
                throw new MvnLauncherException("Cannot resolve " + ma.asString() + ": " + ma.getError());
            }
            JarFileArchive jar = new JarFileArchive(f);
            mainClass = jar.getMainClass(); // simple but inappropriate place to do this
            List<MvnArtifact> artifacts = new LinkedList<MvnArtifact>();
            artifacts.add(ma);
            artifacts.addAll(getArtifacts(jar));
            return artifacts;
        }
        finally {
            StatusLine.pop();
        }
	}

	// parses Maven URIs and converts them into list of Maven artifacts
	private List<MvnArtifact> toArtifacts(String[] strings) {
		if (strings == null) {
			return Collections.emptyList();
		}
		List<MvnArtifact> result = new ArrayList<MvnArtifact>();
		for (String s : strings) {
			if (s == null || s.trim().isEmpty()) {
				continue;
			}
			result.add(MvnArtifact.parse(s));
		}
		return result;
	}
}
