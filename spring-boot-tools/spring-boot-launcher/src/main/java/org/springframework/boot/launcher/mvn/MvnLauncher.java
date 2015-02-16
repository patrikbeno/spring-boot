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
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.jar.Manifest;

import org.springframework.boot.launcher.MvnLauncherCfg;
import org.springframework.boot.launcher.MvnLauncherException;
import org.springframework.boot.launcher.util.Log;
import org.springframework.boot.launcher.util.StatusLine;
import org.springframework.boot.loader.ExecutableArchiveLauncher;
import org.springframework.boot.loader.LaunchedURLClassLoader;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.JarFileArchive;

import static org.springframework.boot.launcher.MvnLauncherCfg.debug;
import static org.springframework.boot.launcher.MvnLauncherCfg.showClasspath;

/**
 * Specialized implementation of the {@code Launcher} that intelligently downloads
 * dependencies from configured Maven repository.
 *
 * @see MvnLauncherBase
 * @see org.springframework.boot.loader.ExecutableArchiveLauncher
 *
 * @author Patrik Beno
 */
public class MvnLauncher extends ExecutableArchiveLauncher {

	/**
	 * Name of the manifest attribute containing the comma-delimited list Maven URIs
	 * (runtime dependencies, provided build-time)
	 */
	static public final String MF_DEPENDENCIES = "Spring-Boot-Dependencies";

	private MvnArtifact artifact;

	// if -DMvnLauncher.artifact is defined, its Start-Class overrides the value defined
	// by this archive
	// lazily resolved in #getArtifacts(MvnArtifact)
	private String mainClass;

	public MvnLauncher(MvnArtifact artifact) {
		this.artifact = artifact;
	}

	@Override
	protected String getMainClass() throws Exception {
		return (mainClass != null) ? mainClass : super.getMainClass();
	}

	@Override
	protected List<Archive> getClassPathArchives() throws Exception {
		return getClassPathArchives(artifact);
	}

	/**
	 * Always returns false: there are no nested archives.
	 */
	@Override
	protected boolean isNestedArchive(Archive.Entry entry) {
		return false; // unsupported / irrelevant
	}

	/// API for embedded use

	/**
	 * Resolves given artifact and all its dependencies, and configures a class loader
	 * linked to a specified parent. Caller is responsible for a proper use of the
	 * resulting class loader and all the classes loaded.
	 * @param artifact
	 * @param parent
	 * @return
	 */
    public ClassLoader resolve(MvnArtifact artifact, List<MvnArtifact> ext, ClassLoader parent) {
		try {
			List<Archive> archives = getClassPathArchives(artifact, ext);
			List<URL> urls = new ArrayList<URL>(archives.size());
			for (Archive archive : archives) {
				urls.add(archive.getUrl());
			}
			ClassLoader cl = new LaunchedURLClassLoader(urls.toArray(new URL[urls.size()]), parent);
			return cl;
		}
		catch (Exception e) {
			throw new MvnLauncherException(e,
					"Cannot resolve artifact or its dependencies: " + artifact.asString());
		}
	}

    public ClassLoader resolve(MvnArtifact artifact, ClassLoader parent) {
        return resolve(artifact, null, parent);
    }

    ///

	protected List<Archive> getClassPathArchives(MvnArtifact mvnartifact) throws Exception {
        return getClassPathArchives(mvnartifact, null);
    }

	protected List<Archive> getClassPathArchives(MvnArtifact mvnartifact, List<MvnArtifact> ext) throws Exception {

        MvnRepositoryConnectorContext context = new MvnRepositoryConnectorContext();
        try {
            MvnRepositoryConnector connector = buildMvnRepositoryConnector(context);

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
        } finally {
            context.close();
            StatusLine.resetLine();
        }
    }

	@Override
	public void launch(String[] args) {
		super.launch(args); // todo implement this
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
        Log.debug("Resolving main artifact: %s", ma);

		StatusLine.push("Resolving %s", ma);
        try {
            File f = connector.resolve(ma);
            if (!f.exists()) {
                throw new MvnLauncherException("Cannot resolve " + ma.asString() + ": " + ma.getError());
            }
            StatusLine.update("Resolving %s", ma);
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
			result.add(new MvnArtifact(s));
		}
		return result;
	}

    MvnRepositoryConnector buildMvnRepositoryConnector(MvnRepositoryConnectorContext context) {
        List<String> ids = MvnLauncherCfg.repositories.asList();
        Collections.reverse(ids);
        MvnRepositoryConnector connector = null;
        for (String id : ids) {
            MvnRepository repo = MvnRepository.forRepositoryId(id);
            connector = new MvnRepositoryConnector(repo, context, connector);
        }
        Log.debug("Using repositories:");
        for (MvnRepositoryConnector c = connector; c != null; c = c.parent) {
            Log.debug("- %8s : %s", c.repository.getId(), c.repository.getURL());
        }
        return connector;
    }
}
