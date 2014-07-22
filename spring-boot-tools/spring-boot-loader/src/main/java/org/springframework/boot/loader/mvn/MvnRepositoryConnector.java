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

import org.springframework.boot.loader.Log;

import org.w3c.dom.Document;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.springframework.boot.loader.mvn.MvnLauncherCfg.*;

/**
 * @author Patrik Beno
 */
public class MvnRepositoryConnector {

	URL repository = MvnLauncherCfg.repositoryUrl.asURL();

	MvnRepositoryCredentials credentials;

	File cache = MvnLauncherCfg.cache.asFile();

//	MvnLauncherCredentialStore store;

	/**
	 * Temporary executor for used to asynchronously download artifacts
	 * @see #close()
	 */
	ExecutorService executor = Executors.newFixedThreadPool(1);

	DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	XPathFactory xpf = XPathFactory.newInstance();

	boolean connectionVerified;

	/**
	 * If the connection to repository seems invalid, throw an exception
	 */
	void verifyConnection() {
		if (connectionVerified) { return; }

		MvnLauncherCredentialStore store = MvnLauncherCredentialStore.instance();

		if (credentials == null) {
			credentials = store.get(repository);
		}
		if (credentials == null) {
			credentials = new MvnRepositoryCredentials(
					repository, MvnLauncherCfg.repositoryUsername.value(), MvnLauncherCfg.repositoryPassword.value()
			);
			if (credentials.hasPassword() && MvnLauncherCfg.saveCredentials.isSet()) { store.save(credentials); }
		}

		Log.debug("> Verifying connection to %s", urlWithUserName(repository, credentials.getUserName()));

		// check http:// and https:// repositories: must be able to connect successfully
		if (repository.getProtocol().matches("https?")) {
			try {
				URLConnection con = urlcon(repository, false);
				con.setConnectTimeout(1000);
				con.connect();
				connectionVerified = true;
			} catch (IOException e) {
				throw new MvnLauncherException(e, "Invalid or misconfigured repository "+repository);
			}

		// verify file:// repositories: directory must exist
		} else if (repository.getProtocol().equals("file")) {
			File f = new File(repository.getPath());
			connectionVerified = f.exists();
			if (!f.exists()) { throw new MvnLauncherException("Invalid repository: "+repository); }

		// unknown / unrecognized protocol
		} else {
			Log.info("Cannot verify protocol %s://. Good luck!", repository.getProtocol());
			connectionVerified = true;
		}
	}

	/**
	 * Close & release executor
	 */
	public void close() {
		executor.shutdownNow();
		executor = null;
	}

	/**
	 * Attempts to resolve given artifacts: each artifact's remote file is downloaded or updated, verified (if required).
	 * @param artifacts
	 * @throws MvnLauncherException if any of the artifacts is invalid or unavailable,
	 *         and strict error checking is enabled
	 * @see MvnLauncherCfg#failOnError
	 */
	public void resolveArtifacts(List<MvnArtifact> artifacts) {

		long started = System.currentTimeMillis();

		// sort the artifacts by name; we want to provide a human-readable dependency report
		List<MvnArtifact> sorted = new ArrayList<MvnArtifact>(artifacts);
		Collections.sort(sorted, MvnArtifact.COMPARATOR);

		Log.debug("## Dependencies (alphabetical):");

		int size = 0;
		int downloaded = 0;
		int errors = 0;
		int warnings = 0;

		for (MvnArtifact ma : sorted) {
			if (ma == null) { continue; }

			// resolve it (main job). This will update $ma
			File f = resolve(ma);

			// update some stats
			if (ma.isError()) { errors++; }
			if (ma.isWarning()) { warnings++; }
			if (f.exists()) {
				size += f.length();
				switch (ma.getStatus()) {
					case Downloaded:
					case Updated:
						downloaded += ma.getFile().length();
				}
			}
		}

		// if enabled, print some final report
		if (debug.isSet()) {
			long elapsed = System.currentTimeMillis() - started;
			Log.debug(String.format(
					"## Summary: %d archives, %d KB total (resolved in %d msec, downloaded: %s KB). Warnings/Errors: %d/%d.",
					artifacts.size(), size / 1024, elapsed, downloaded / 1024, warnings, errors));
		}

		// if there are errors and fail-on-error property has not been reset, fail
		if (MvnLauncherCfg.failOnError.isSet() && errors > 0) {
			throw new MvnLauncherException(String.format(
					"%d errors resolving dependencies. Use -D%s to view details or -D%s to ignore these errors and continue",
					errors, MvnLauncherCfg.debug.getPropertyName(), MvnLauncherCfg.failOnError.getPropertyName()));
		}

	}

	/**
	 * Resolve single artifact: download or update & verify.
	 * Most errors are reported via {@code MvnArtifact.status} and described using {@code MvnArtifact.error}.
	 * The only exception is generic {@code IOException} which is rethrown as {@code MvnLauncherException}
	 * @param artifact
	 * @return cached artifact's file (also saved in {@code MvnArtifact.file}
	 * @see MvnLauncherException
	 * @see MvnArtifact#file
	 * @see MvnArtifact#error
	 * @see MvnArtifact.Status
	 */
	public File resolve(final MvnArtifact artifact) {

		// if the source is file://, there's no point caching it (unless forced)
		if (repository.getProtocol().equals("file") && !cacheFileProtocol.isSet()) {
			try {
				URL url = new URL(repository, artifact.getPath());
				return resource(artifact, MvnArtifact.Status.Cached, url, new File(url.getPath()), null);
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}

		try {
			// target file in cache
			final File f = new File(cache, artifact.getPath());
			final File fLastUpdated = getLastUpdatedMarkerFile(f);
			final long lastUpdated = fLastUpdated.exists() ? fLastUpdated.lastModified() : 0;
			final long validUntil = lastUpdated + (TimeUnit.MINUTES.toMillis(MvnLauncherCfg.updateInterval.longValue()));
			final boolean expired = validUntil < System.currentTimeMillis();

			// in offline mode, artifact is either available or not; we're not doing anything about it
			if (offline.isSet()) {
				return resource(
						artifact,
						f.exists() ? MvnArtifact.Status.Offline : MvnArtifact.Status.NotFound,
						repository,
						f,
						f.exists() ? null : new FileNotFoundException(f.getAbsolutePath()));
			}

			// should we try to update the file?
			boolean update
					= (artifact.isSnapshot() && updateSnapshots.isSet() && expired)
					|| (artifact.isRelease() && updateReleases.isSet() && expired);
			boolean nocache = ignoreCache.isSet();

			// file is in cache already and update is disabled
			if (f.exists() && !update && !nocache) {
				return resource(artifact, MvnArtifact.Status.Cached, repository, f, null);
			}

			// ok, we're going remote...

			// in case of snapshots, resolve the latest version
			if (artifact.isSnapshot() ) {
				resolveSnapshotVersion(artifact);
			}

			// source URL
			final URL url = new URL(repository, artifact.getPath());
			URLConnection con = urlcon(url);
			final long lastModified = con.getLastModified();
			final long size = con.getContentLengthLong();

			// checking the cache: it the cached file is up to date, use it
			if (f.exists() && f.lastModified() == lastModified && !nocache) {
				return resource(artifact, MvnArtifact.Status.NotModified, url, f, null);
			}

			// cache miss or ignore, proceed to download
			try {
				// prepare
				f.getParentFile().mkdirs();

				final long updateTimestamp = System.currentTimeMillis();

				// download: use temp. file, rename after success. download
				final InputStream in = con.getInputStream();
				final File tmp = new File(f.getParentFile(), UUID.randomUUID() + ".tmp");
				final Future<?> future = executor.submit(new Runnable() {
					@Override
					public void run() {
						try {
							Files.copy(in, tmp.toPath());
						} catch (IOException e) {
							throw new RuntimeException(e);
						} finally {
							if (in != null) try { in.close(); } catch (IOException ignore) {}
						}
					}
				});
				monitorDownloadProgress(artifact, future, tmp, size);

				// verify the checksum; report the errors if enabled
				if (verify.isSet() && !verify(tmp, url)) {
					// invalid, drop it & report
					Files.delete(tmp.toPath());
					return resource(artifact, MvnArtifact.Status.Invalid, url, null, null);
				}

				// updated existing or downloaded new?
				boolean updated = f.exists() && f.delete();
				// atomic rename: put target in place as quick as possible
				Files.move(tmp.toPath(), f.toPath());
				// also align timestamps of source and cache
				f.setLastModified(lastModified);

				// done, report result
				MvnArtifact.Status status = (updated) ? MvnArtifact.Status.Updated : MvnArtifact.Status.Downloaded;
				return resource(artifact, status, repository, f, null);

			} catch (FileNotFoundException e) {
				// nope
				return resource(artifact, MvnArtifact.Status.NotFound, repository, f, e);
			}

		} catch (IOException e) {
			// infrastructure failure? just give up
			throw new MvnLauncherException(e, "Error resolving "+artifact.asString());

		}
	}

	private File getLastUpdatedMarkerFile(File f) {
		return new File(f.getParentFile(), f.getName()+".lastUpdated");
	}

	private void rememberLastUpdateTime(File fLastUpdated, long updateTimestamp) {
		try {
			// touch .lastUpdated flag file
			fLastUpdated.createNewFile();
			if (fLastUpdated.exists()) { fLastUpdated.setLastModified(updateTimestamp); }
		} catch (IOException e) {
			Log.debug(e.toString());
		}
	}

	/**
	 * Print out download status information every second until the operation is completed.
	 * Only if the system console is available otherwise just silently wait for asynchronous job completion.
	 * @param artifact
	 * @param future async. job
	 * @param tmp temporary file where the data is being downloaded
	 * @param size total size of the download
	 */
	private void monitorDownloadProgress(MvnArtifact artifact, Future<?> future, File tmp, long size) {
		// initialize download status line
		if (debug.isSet() && System.console() != null) {
			System.out.printf("Downloading\t%s (%d KB)", artifact, size/1024);
		}
		do {
			try { future.get(1, TimeUnit.SECONDS); } catch (Exception ignore) {}
			if (debug.isSet() && System.console() != null) {
				System.out.printf("\rDownloading\t%s (%d KB) %d%%", artifact, size/1024, tmp.length()*100/size);
			}
		} while ((!future.isDone()));

		// reset status line
		if (debug.isSet() && System.console() != null) {
			System.console().printf("\r");
		}
	}

	/**
	 * Populate internal structures of Maven artifact as needed, report status if enabled
	 * @return cached file ready to use (if any)
	 */
	File resource(MvnArtifact artifact, MvnArtifact.Status status, URL source, File file, Throwable error) {
		artifact.setStatus(status);
		artifact.setSource(source);
		artifact.setFile(file);
		artifact.setError(error);

		switch (status) {
			case Downloaded:
			case Updated:
			case NotModified:
				rememberLastUpdateTime(getLastUpdatedMarkerFile(file), System.currentTimeMillis());
				break;
			case Cached:
			case NotFound:
			case Offline:
			case Invalid:
				break;
			default:
				throw new AssertionError(status);
		}

		Log.debug(
				"%-15s: %s (%s KB)    ",
				artifact.getStatus(),
				artifact,
				artifact.getFile() != null && artifact.getFile().exists() ? artifact.getFile().length() / 1024 : "?");

		return artifact.getFile();
	}

	/**
	 * Resolve timestamped downloadable snapshot version of a given snapshot artifact
	 */
	void resolveSnapshotVersion(MvnArtifact artifact) {
		try {
			// metadata: remote URL and cached local file
			URL url = new URL(repository, artifact.getPath());
			URL murl = new URL(url, "maven-metadata.xml");
			File mfile = new File(new File(cache, artifact.getPath()).getParentFile(), "maven-metadata.xml");

			// should we try and update?
			boolean update = updateSnapshots.isSet() || ignoreCache.isSet();

			// metadata
			URLConnection metadata = urlcon(murl);
			long lastModified = metadata.getLastModified();

			// is our local copy up to date?
			boolean recent = mfile.exists() && mfile.lastModified() >= lastModified;

			if (!recent || update) {
				// download latest metadata; use temporary file
				InputStream in = metadata.getInputStream();
				try {
					mfile.getParentFile().mkdirs();
					File tmp = new File(mfile.getParentFile(), UUID.randomUUID().toString()+".tmp");
					Files.copy(in, tmp.toPath());
					Files.move(tmp.toPath(), mfile.toPath(), StandardCopyOption.REPLACE_EXISTING);
					mfile.setLastModified(lastModified);
				} finally {
					try { in.close(); } catch (IOException ignore) {}
				}
			}

			// load latest snapshot version from metadata
			String version = getSnapshotVersionFromMetadata(mfile);

			// and set the result
			artifact.setResolvedSnapshotVersion(artifact.getVersion().replaceFirst("SNAPSHOT$", version));

		} catch (FileNotFoundException e) {
			// no metadata, artifact probably does not exist
			artifact.setError(e);

		} catch (Exception e) {
			// nope, something went wrong
			throw new RuntimeException(e);
		}
	}

	/**
	 * Extract timestamped snapshot version from downloaded/cached metadata
	 * @param file metadata
	 * @return timestamped snapshot version
	 */
	String getSnapshotVersionFromMetadata(File file) {
		try {
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(file);
			XPath xpath = xpf.newXPath();
			String version = String.format(
					"%s-%s",
					xpath.evaluate("//versioning/snapshot/timestamp", doc),
					xpath.evaluate("//versioning/snapshot/buildNumber", doc)
			);
			return version;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Compute downloaded file checksum and verify computed value against the declared remote value.
	 * @return true if computed checksum matches the declared one, false otherwise
	 */
	boolean verify(File f, URL source) {

		String ALG = "SHA1"; // or MD5

		InputStream in = null;
		try {
			// construct checksum resoruce URL and open connection
			URL url = new URL(source.toExternalForm() + "." + ALG.toLowerCase());
			URLConnection con = urlcon(url);
			in = con.getInputStream();

			// load declared; QDH see https://weblogs.java.net/blog/pat/archive/2004/10/stupid_scanner_1.html
			String declared = new Scanner(in, "ASCII").useDelimiter("\\A").next().trim();
			// compute actual
			String computed = getFileChecksum(f, ALG);

			// compare/validate
			return declared.equals(computed);

		} catch (IOException e) {
			// uh-oh
			throw new MvnLauncherException(e, "Error verifying "+f);

		} finally {
			// cleanup
			if (in != null) try { in.close(); } catch (IOException ignore) {}
		}
	}

	/**
	 * Compute checksum of a given file, using given algorithm, and convert it to hex-string
	 */
	private String getFileChecksum(File f, String alg) {
		byte[] bytes = getChecksumBytes(f, alg);
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
		}
		return sb.toString();
	}

	/**
	 * Compute checksum of a given file using specified algorithm
	 * @see stackoverflow http://stackoverflow.com/questions/304268/getting-a-files-md5-checksum-in-java
	 */
	private byte[] getChecksumBytes(File f, String alg) {
		InputStream in = null;
		try {
			in = new FileInputStream(f);

			byte[] buffer = new byte[1024 * 64];
			MessageDigest complete = MessageDigest.getInstance(alg);
			int numRead;

			do {
				numRead = in.read(buffer);
				if (numRead > 0) { complete.update(buffer, 0, numRead); }
			} while (numRead != -1);

			return complete.digest();

		} catch (Exception e) {
			throw new RuntimeException(e);

		} finally {
			if (in != null) { try { in.close(); } catch (IOException ignore) {} }
		}
	}


	private URLConnection urlcon(URL url) {
		return urlcon(url, !MvnLauncherCfg.offline.isSet());
	}

	private URLConnection urlcon(URL url, boolean verify) {
		if (verify) { verifyConnection(); }
		try {
			URLConnection con = url.openConnection();
			if (credentials.hasPassword()) { // credentials are lazily initialized in #verifyConnection()
				String auth = String.format("%s:%s", credentials.getUserName(), credentials.getPassword());
				con.setRequestProperty("Authorization", String.format(
						"Basic %s", DatatypeConverter.printBase64Binary(auth.getBytes("UTF-8"))));
			}
			return con;
		} catch (IOException e) {
			throw new MvnLauncherException(e, "Error opening connection "+url);
		}
	}

	URL urlWithUserName(URL url, String username) {
		if (username == null) { return url; }
		try {
			return new URL(String.format("%s://%s@%s%s", url.getProtocol(), username, url.getHost(), url.getFile()));
		} catch (MalformedURLException e) {
			throw new AssertionError(e);
		}
	}

}
