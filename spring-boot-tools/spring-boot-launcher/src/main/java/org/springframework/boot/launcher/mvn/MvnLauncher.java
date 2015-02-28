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

import org.springframework.boot.launcher.MvnLauncherCfg;
import org.springframework.boot.launcher.MvnLauncherException;
import org.springframework.boot.launcher.util.Log;
import org.springframework.boot.launcher.util.StatusLine;
import org.springframework.boot.loader.ExecutableArchiveLauncher;
import org.springframework.boot.loader.LaunchedURLClassLoader;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.util.UrlSupport;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.SortedSet;

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

    static {
        UrlSupport.init();
    }

	private MvnArtifact artifact;

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
			List<Archive> archives = getClassPathArchives(artifact);
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
		} finally {
            System.gc();
        }
	}

    public ClassLoader resolve(MvnArtifact artifact, ClassLoader parent) {
        return resolve(artifact, null, parent);
    }

    ///

	protected List<Archive> getClassPathArchives(MvnArtifact mvnartifact) throws Exception {

        List<Archive> archives = new LinkedList<Archive>();

        ResolverContext context = new ResolverContext(mvnartifact);
        try {
            Resolver main = new Resolver(context, mvnartifact);

            int count = 0;
            int size = 0;
            int downloaded = 0;
            int errors = 0;
            int warnings = 0;
            int requests = 0;

            try {
                context.startProgressMonitor();

                // tiny single line but this is where all happens
                SortedSet<Resolver> resolvers = main.resolveAll();

                Log.debug("Dependencies (alphabetical):");

                for (Resolver r : resolvers) {

                    // this may block until resolved artifact is available
                    MvnArtifact ma = r.getResolvedArtifact();

                    if (ma.getFile() != null && ma.getFile().exists()) {
                        archives.add(new JarFileArchive(ma.getFile()));
                    }

                    Log.log(toLevel(ma.getStatus()),
                            "- %-12s: %-60s %s",
                            ma.getStatus(), ma,
                            ma.getRepositoryId() != null
                                    ? String.format("(%3dKB @%s)", ma.getFile() != null && ma.getFile().exists() ? ma.getFile().length() / 1024 : 0, ma.getRepositoryId())
                                    : ""
                    );
                    // update some stats
                    if (ma.isError()) { errors++; }
                    if (ma.isWarning()) { warnings++; }
                    if (ma.getFile() != null && ma.getFile().exists()) {
                        size += ma.getFile().length();
                    }
                    downloaded += ma.downloaded;
                    requests += ma.requests;
                }

                count = resolvers.size();

            } finally {
                context.stopProgressMonitor();
            }

            // if enabled, print some final report
            if (!MvnLauncherCfg.quiet.asBoolean()) {
                long elapsed = System.currentTimeMillis() - context.created;
                Log.info(String.format(
                        "Summary: %d archives, %d KB total (resolved in %d msec, downloaded %d KB in %d requests, %d KBps). Warnings/Errors: %d/%d.",
                        count, size / 1024, elapsed, downloaded / 1024, requests,
                        downloaded / 1024 * 1000 / elapsed,
                        warnings, errors));
            }

            // if there are errors and fail-on-error property has not been reset, fail
            if (MvnLauncherCfg.failOnError.asBoolean() && errors > 0) {
                throw new MvnLauncherException(String.format(
                        "%d errors resolving dependencies. Use --%s to view details or --%s to ignore these errors and continue",
                        errors, MvnLauncherCfg.debug.name(), MvnLauncherCfg.failOnError.name()));
            }

            return archives;

        } finally {
            context.close();
            StatusLine.resetLine();
        }
    }

    public void launch(Queue<String> args) throws Exception {
        launch(args.toArray(new String[args.size()]));
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

    ///

    private Log.Level toLevel(MvnArtifact.Status status) {
        switch (status) {
            case NotFound:
                return Log.Level.WRN;
            default:
                return Log.Level.DBG;
        }
    }


}
