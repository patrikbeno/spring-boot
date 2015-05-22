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

import org.springframework.boot.launcher.LauncherCfg;
import org.springframework.boot.launcher.LauncherException;
import org.springframework.boot.launcher.util.Log;
import org.springframework.boot.launcher.util.StatusLine;
import org.springframework.boot.loader.Launcher;
import org.w3c.dom.Document;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.springframework.boot.launcher.mvn.Artifact.Status.Downloaded;
import static org.springframework.boot.launcher.util.IOHelper.close;

/**
 * @author Patrik Beno
 */
public class RepositoryConnector {

    static private enum UrlConMethod { HEAD, GET }

    static private final String USER_AGENT = String.format(
            "SpringBoot-MvnLauncher/%s", Launcher.class.getPackage().getImplementationVersion());

    Repository repository;

    RepositoryConnector parent;

    ResolverContext context;

	boolean connectionVerified = LauncherCfg.offline.asBoolean();

    int retries = LauncherCfg.retries.asInt();

    public RepositoryConnector(Repository repository, ResolverContext context, RepositoryConnector parent) {
        this.repository = repository;
        this.context = context;
        this.parent = parent;
    }

    /**
	 * If the connection to repository seems invalid, throw an exception
	 */
	void verifyConnection() {
		if (connectionVerified) { return; }

        synchronized (this) {
            if (connectionVerified) { return; }

            // check http:// and https:// repositories: must be able to connect successfully
            if (repository.getURL().getProtocol().matches("https?")) {
                URLConnection con = null;
                StatusLine.push("Verifying connection to %s", repository.getURL().getHost());
                try {
                    con = urlcon(repository.getURL(), false, UrlConMethod.HEAD, null);
                    con.setConnectTimeout(1000);
                    con.connect();
                    connectionVerified = true;
                } catch (IOException e) {
                    throw new LauncherException(e, "Invalid or misconfigured repository " + repository.getURL());
                } finally {
                    close(con);
                    StatusLine.pop();
                }

                // verify file:// repositories: directory must exist
            }
            else if (repository.getURL().getProtocol().equals("file")) {
                File f = new File(repository.getURL().getPath());
                connectionVerified = f.exists();
                if (!f.exists()) {
                    throw new LauncherException("Invalid repository: " + repository.getURL());
                }

                // unknown / unrecognized protocol
            }
            else {
                Log.debug("Cannot verify protocol %s://. Good luck!", repository.getURL().getProtocol());
                connectionVerified = true;
            }
        }
    }

	/**
	 * Attempts to resolve given artifacts: each artifact's remote file is downloaded or
	 * updated, verified (if required).
	 * @param artifacts
	 * @throws org.springframework.boot.launcher.LauncherException if any of the artifacts is invalid or unavailable, and
	 * strict error checking is enabled
	 * @see org.springframework.boot.launcher.LauncherCfg#failOnError
	 */
	public void resolveArtifacts(List<Artifact> artifacts) {

		long started = System.currentTimeMillis();

		// sort the artifacts by name; we want to provide a human-readable dependency
		// report
		final List<Artifact> sorted = new ArrayList<Artifact>(artifacts);
		Collections.sort(sorted, Artifact.COMPARATOR);

        for (Artifact ma : sorted) {
            if (ma.getStatus() == null) { ma.setStatus(Artifact.Status.Resolving); }
        }

        List<Callable<Artifact>> resolvers = new LinkedList<Callable<Artifact>>();
        for (final Artifact ma : sorted) {
            resolvers.add(new Callable<Artifact>() {
                @Override
                public Artifact call() throws Exception {
                    resolve(ma);
                    switch (ma.getStatus()) {
                        case NotFound:
                            if (parent != null) {
                                parent.resolve(ma);
                            }
                    }
                    return ma;
                }
            });
		}

	}

    /**
	 * Resolve single artifact: download or update & verify. Most errors are reported via
	 * {@code MvnArtifact.status} and described using {@code MvnArtifact.error}. The only
	 * exception is generic {@code IOException} which is rethrown as
	 * {@code MvnLauncherException}
	 * @param artifact
	 * @return cached artifact's file (also saved in {@code MvnArtifact.file}
	 * @see org.springframework.boot.launcher.LauncherException
	 * @see Artifact#file
	 * @see Artifact#error
	 * @see Artifact.Status
	 */
	public File resolve(final Artifact artifact) {

        if (artifact.getFile() != null && artifact.getFile().exists()) {
            return artifact.getFile();
        }

        // in case of snapshots, resolve the latest version
        if (artifact.isSnapshot()) {
            resolveSnapshotVersion(artifact);
        }

        URLConnection con = null;
        try {
            // target file in cache
            final File f = new File(context.cache, artifact.getPath());
            final File fLastUpdated = getLastUpdatedMarkerFile(f);
            final boolean expired = isExpired(fLastUpdated);

            // in offline mode, artifact is either available or not; we're not doing
            // anything about it
            if (LauncherCfg.offline.asBoolean()) {
                return resource(
                        artifact,
                        f.exists() ? Artifact.Status.Offline : Artifact.Status.NotFound,
                        repository.getURL(), f,
                        f.exists() ? null : new FileNotFoundException(f.getAbsolutePath()));
            }

            // should we try to update the file?
            boolean update = LauncherCfg.update.asBoolean()
                    || (artifact.isSnapshot() && LauncherCfg.updateSnapshots.asBoolean() && expired)
                    || (artifact.isRelease() && LauncherCfg.updateReleases.asBoolean() && expired);
            boolean nocache = LauncherCfg.ignoreCache.asBoolean();

            // file is in cache already and update is disabled
            if (f.exists() && !update && !nocache) {
                return resource(artifact, Artifact.Status.Cached, repository.getURL(), f, null);
            }

            // ok, we're going remote...

            // source URL
            URL url = new URL(repository.getURL(), artifact.getPath());
            con = urlcon(url, UrlConMethod.HEAD, null);
            boolean available = isAvailable(con);

            if (!available) {
                return parent != null
                        ? parent.resolve(artifact)
                        : resource(artifact, Artifact.Status.NotFound, url, null, null);
            }

            final long lastModified = con.getLastModified();

            // checking the cache: it the cached file is up to date, use it
            if (f.exists() && f.lastModified() == lastModified && !nocache) {
                return resource(artifact, Artifact.Status.NotModified, url, f, null);
            }

            // cache miss or ignore, proceed to download
            artifact.size = con.getContentLength();

            // use temp. file, rename after success
            final File tmp = new File(f.getParentFile(), UUID.randomUUID() + ".tmp");

            artifact.con = con;
            artifact.tmp = tmp;

            return resource(artifact, Artifact.Status.Downloadable, url, f, null);
        } catch (IOException e) {
            // infrastructure failure? just give up
            throw new LauncherException(e, "Error resolving " + artifact.asString());
        } finally {
            close(con);
        }
    }

    private boolean isAvailable(URLConnection con) {
        try {
            return con != null && con.getLastModified() > 0;
        }
        catch (Exception ignore) { return false; }
    }

    private File getLastUpdatedMarkerFile(File f) {
		return new File(f.getParentFile(), f.getName() + ".lastUpdated");
	}

	private void rememberLastUpdateTime(File fLastUpdated, long updateTimestamp) {
		try {
			// touch .lastUpdated flag file
			fLastUpdated.createNewFile();
			if (fLastUpdated.exists()) {
				fLastUpdated.setLastModified(updateTimestamp);
			}
		}
		catch (IOException e) {
			Log.debug(e.toString());
		}
	}

    File download(Artifact artifact) throws IOException {

        if (!artifact.getStatus().equals(Artifact.Status.Downloadable)) {
            return artifact.getFile();
        }

		if (!context.isDownloadAllowed(artifact)) {
			return null;
		}

        URLConnection con = null;
        try {
            URL url = artifact.con.getURL();
            con = urlcon(url, UrlConMethod.GET, null);

            File tmp = artifact.tmp;
            File f = artifact.getFile();

            // download
            download(artifact, con, tmp);

            boolean isVerifyEnabled = LauncherCfg.verify.asBoolean() && !url.getProtocol().equals("file");

            // verify the checksum; report the errors if enabled
            if (isVerifyEnabled && !verify(tmp, url)) {
                // invalid, drop it & report
                Files.delete(tmp.toPath());
                return resource(artifact, Artifact.Status.Invalid, url, null, null);
            }

            // updated existing or downloaded new?
            boolean updated = f.exists();

            // save
            commit(tmp, f, con.getLastModified());

            // done, report result
            Artifact.Status status = (updated) ? Artifact.Status.Updated : Downloaded;
            return resource(artifact, status, url, f, null);
        } finally {
            close(con);
        }
    }

    void download(final Artifact artifact, final URLConnection con, final File file) throws IOException {
        artifact.setStatus(Artifact.Status.Downloading);
        file.getParentFile().mkdirs();
        for (int attempt = 1; attempt <= retries && !artifact.getStatus().equals(Downloaded); attempt++) {
            InputStream in = null;
            try {
                artifact.size = con.getContentLength();
                artifact.setFile(file);
                in = con.getInputStream();
                Files.copy(in, file.toPath());
                artifact.downloaded += file.length();
                artifact.setStatus(Downloaded);
                break;
            }
            catch (IOException e) {
                Log.debug("Error (attempt %d/%d): %s", attempt, retries, con.getURL());
                if (attempt == retries) { throw e; }
            }
            finally {
                close(in);
                close(con);
                artifact.requests++;
            }
        }
    }

	void commit(File tmp, File dst, long lastModified) {
		try {
            Files.move(tmp.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dst.setLastModified(lastModified);
			File fmarker = getLastUpdatedMarkerFile(dst);
			fmarker.createNewFile();
			fmarker.setLastModified(System.currentTimeMillis());
		}
		catch (AccessDeniedException e) {
            // probably locked by other process
            if (dst.exists() && getFileChecksum(tmp, "SHA1").equals(getFileChecksum(dst, "SHA1"))) {
                // assume file is already there, just locked
                Log.warn("Locked? Cannot update %s", dst);
                tmp.delete();
            } else {
                throw new RuntimeException(e);
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
		}
	}

	/**
	 * Populate internal structures of Maven artifact as needed, report status if enabled
	 * @return cached file ready to use (if any)
	 */
	File resource(final Artifact artifact, Artifact.Status status, URL source, File file, Throwable error) {
		artifact.setStatus(status);
		artifact.setSource(source);
		artifact.setFile(file);
		if (error != null && artifact.getError() == null) { artifact.setError(error); }

        switch (status) {
            case Downloadable:
                artifact.setRepositoryId(repository.getId());
                break;
            case Downloaded:
            case Updated:
            case NotModified:
                artifact.setRepositoryId(repository.getId());
                rememberLastUpdateTime(getLastUpdatedMarkerFile(file), System.currentTimeMillis());
                break;
            case Cached:
            case Offline:
                artifact.setRepositoryId("cache");
            case Invalid:
            case NotFound:
                break;
            default:
                throw new AssertionError(status);
        }

        return artifact.getFile();
	}

	/**
	 * Resolve timestamped downloadable snapshot version of a given snapshot artifact
	 */
	void resolveSnapshotVersion(Artifact artifact) {
        URLConnection metadata = null;
        try {
            // metadata: remote URL and cached local file
            URL url = new URL(repository.getURL(), artifact.getPath());
            URL murl = new URL(url, "maven-metadata.xml");
            File mfile = new File(new File(context.cache, artifact.getPath()).getParentFile(), "maven-metadata.xml");
            File fLastUpdated = getLastUpdatedMarkerFile(mfile);

            final boolean expired = isExpired(fLastUpdated);

            // should we try and update?
            boolean update = LauncherCfg.update.asBoolean()
                    || (LauncherCfg.updateSnapshots.asBoolean() && expired
                    || LauncherCfg.ignoreCache.asBoolean());

            // metadata
            if (update) {
                metadata = urlcon(murl, UrlConMethod.GET, mfile.exists() ? mfile.lastModified() : null);
                metadata.connect();
            }
            long lastModified = isAvailable(metadata) ? metadata.getLastModified() : mfile.lastModified();
            if (update) artifact.requests++;

			// latest snapshot version from metadata
			String snapshotVersion;

            // is our local copy up to date?
            boolean recent = mfile.exists() && mfile.lastModified() >= lastModified;

			boolean downloadAllowed = context.isDownloadAllowed(artifact);

			if (!recent) {
                File tmp = new File(mfile.getParentFile(), UUID.randomUUID().toString() + ".tmp");
                download(artifact, metadata, tmp);
				snapshotVersion = getSnapshotVersionFromMetadata(tmp);
				if (downloadAllowed) {
					commit(tmp, mfile, lastModified);
				} else {
					tmp.delete();
				}
            } else {
				snapshotVersion = getSnapshotVersionFromMetadata(mfile);
			}

            // and set the result
            artifact.setResolvedSnapshotVersion(artifact.getVersion().replaceFirst("SNAPSHOT$", snapshotVersion));

        } catch (FileNotFoundException e) {
            // no metadata, artifact probably does not exist
            artifact.setError(e);

        } catch (IOException e) {
            // nope, something went wrong
            throw new LauncherException(e, "Could not resolve snapshot version of " + artifact);
        } finally {
            close(metadata);
        }
    }

	private boolean isExpired(File f) {
		final long lastUpdated = f.exists() ? f.lastModified() : 0;
		final long validUntil = lastUpdated
				+ (TimeUnit.MINUTES.toMillis(LauncherCfg.updateInterval.asLong()));
		return validUntil < System.currentTimeMillis();
	}

	/**
	 * Extract timestamped snapshot version from downloaded/cached metadata
	 * @param file metadata
	 * @return timestamped snapshot version
	 */
	String getSnapshotVersionFromMetadata(File file) {
		try {
			DocumentBuilder db = context.dbf.newDocumentBuilder();
			Document doc = db.parse(file);
			XPath xpath = context.xpf.newXPath();
			String version = String.format("%s-%s",
					xpath.evaluate("//versioning/snapshot/timestamp", doc),
					xpath.evaluate("//versioning/snapshot/buildNumber", doc));
			return version;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Compute downloaded file checksum and verify computed value against the declared
	 * remote value.
	 * @return true if computed checksum matches the declared one, false otherwise
	 */
	boolean verify(File f, URL source) {

        String ALG = "SHA1"; // or MD5

        InputStream in = null;
        URLConnection con = null;
        try {
            // construct checksum resoruce URL and open connection
            URL url = new URL(source.toExternalForm() + "." + ALG.toLowerCase());
            con = urlcon(url, UrlConMethod.GET, null);
            in = con.getInputStream();

            // load declared; QDH see
            // https://weblogs.java.net/blog/pat/archive/2004/10/stupid_scanner_1.html
            // also, some non-standard SHA1 sums files have ignorable suffixes that need to be stripped, hence
            // the final regexp (e.g. http://goo.gl/JbfT2P)
            String declared = new Scanner(in, "ASCII").useDelimiter("\\A").next().trim().replaceFirst("[ \t].*", "");
            // compute actual
            String computed = getFileChecksum(f, ALG);

            // compare/validate
            return declared.equals(computed);

        } catch (IOException e) {
            // uh-oh
            throw new LauncherException(e, "Error verifying " + f);

        } finally {
            // cleanup
            close(in);
            close(con);
        }
    }

	/**
	 * Compute checksum of a given file, using given algorithm, and convert it to
	 * hex-string
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
	 * @see stackoverflow
	 * http://stackoverflow.com/questions/304268/getting-a-files-md5-checksum-in-java
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
				if (numRead > 0) {
					complete.update(buffer, 0, numRead);
				}
			}
			while (numRead != -1);

			return complete.digest();

		}
		catch (Exception e) {
			throw new RuntimeException(e);

		}
		finally {
			if (in != null) {
				try {
					in.close();
				}
				catch (IOException ignore) {
				}
			}
		}
	}

	private URLConnection urlcon(URL url, UrlConMethod method, Long ifModifiedSince) {
		return urlcon(url, !connectionVerified, method, ifModifiedSince);
	}

	private URLConnection urlcon(URL url, boolean verify, UrlConMethod method, Long ifModifiedSince) {
		if (verify) {
			verifyConnection();
		}
		try {
			URLConnection con = url.openConnection();

            if (con instanceof HttpURLConnection) {
                HttpURLConnection hcon = (HttpURLConnection) con;
                hcon.setRequestProperty("User-Agent", USER_AGENT);
                hcon.setRequestMethod(method.name());
                if (ifModifiedSince != null) { hcon.setIfModifiedSince(ifModifiedSince); }
            }

            // credentials are lazily initialized in #verifyConnection()
			if (repository != null && repository.hasPassword()) {
				String auth = String.format("%s:%s", repository.getUserName(), repository.getPassword());
                con.setRequestProperty(
                        "Authorization", String.format(
                                "Basic %s", DatatypeConverter.printBase64Binary(auth.getBytes("UTF-8"))));
			}
			return con;
		}
		catch (IOException e) {
			throw new LauncherException(e, "Error opening connection " + url);
		}
	}


}
