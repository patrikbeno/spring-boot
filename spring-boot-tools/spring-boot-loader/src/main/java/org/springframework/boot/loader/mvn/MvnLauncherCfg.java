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
import org.springframework.boot.loader.MvnLauncher;
import org.springframework.boot.loader.util.SystemPropertyUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.springframework.boot.loader.util.SystemPropertyUtils.getProperty;
import static org.springframework.boot.loader.util.SystemPropertyUtils.resolvePlaceholders;

/**
 * MvnLauncher bootstrap configuration (loaded from external properties file or system properties).
 * <p/>
 * Each configuration property has its built-in default. Defaults can be either overridden from command line (using
 * {@code -DMvnLauncher.enumName=value} property syntax), or from a property file specified
 * in {@code -DMvnLauncher.defaults} property (which itself defaults
 * to {@code ${user.home}/.springboot/defaults.properties}).
 *
 * @see MvnLauncher
 * @author Patrik Beno
 */
public enum MvnLauncherCfg {

	// naming convention broken intentionally: enum name() is also property name used in -DMvnLauncher.{name}={value}

	/**
	 * Particular application home directory; Usually not current directory, not user directory, not a system folder.
	 * This one is first because it is referenced by {@code defaults}.
	 * Defaults to ${java.home}
	 * @see #defaults
	 */
	apphome(file("${java.home}")),

	/**
	 * Properties containing MvnLauncher user-specific configuration defaults; the file, if exists, is loaded during
	 * initialization of this property and its values are propagated into system properties (if not yet defined using
	 * {@code -Dname=value} on JVM command line)
	 *
	 * Keep this first to force loading of user-defined defaults before resolving other properties
	 */
	defaults(
			Properties.class,
			"file:springboot.properties", // current folder
			"file:///${MvnLauncher.apphome}/springboot.properties", // application home folder, if defined
			"file:///${user.home}/.springboot/defaults.properties", // user defaults
			"file:///etc/springboot/defaults.properties", // system defaults // todo: what about windows?
			"classpath:META-INF/springboot/defaults.properties" // application/library defaults
	),

	// keep this on top to enable logging of other configuration values when debug=true
	// leaving this below #defaults provides support for this property in user configuration file
	debug(false),

	/**
	 * Launcher cache directory. Defaults to ${user.home}/.springboot/cache
	 */
	cache(file("${user.home}/.springboot/cache")),

	/**
	 * If set, final resolved classpath will be logged (debug level must be enabled).
	 */
	showClasspath(false),

	/**
	 * If set, no artifacts are downloaded or updated from remote repository, and any artifact may end up
	 * unresolved (NotFound). Launcher rejects incomplete class paths and aborts, unless overriden
	 * using {@link #failOnError}.
	 */
	offline(false),

	/**
	 * Specifies interval (in minutes) within which result of the latest successful update operation remains valid.
	 * This means that remote repository is checked only once in a specified interval. In other words, if you run
	 * the application twice, it will check for updates only once (first run). Second run will behave as if the offline
	 * mode was enabled (no remote repository contact whatsoever, unless the dependency is missing). Next update check
	 * will be allowed only after the specified update interval elapsed.
	 * Default: 1 day (1440 minutes)
	 */
	updateInterval(TimeUnit.DAYS.toMinutes(1)),

	/**
	 * If set, downloads are verified using SHA1 signature provided by remote repository: signature mismatch
	 * is considered an error, and the artifact is marked {@code Invalid}. Enabled by default.
	 * @see MvnArtifact.Status#Invalid
	 */
	verify(true),

	/**
	 * If set, cache is ignored and all artifacts are re-downloaded.
	 */
	ignoreCache(false),

	/**
	 * If set, errors like missing artifacts and checksum mismatches cause launcher to reject
	 * application execution. Enabled by default.
	 */
	failOnError(true),

	/**
	 * By default file:// repository is used directly and artifacts are not cached in launcher cache.
	 * You may want to enable file:// protocol caching if you have concurrency issues using your file:// based
	 * repository.
	 */
	cacheFileProtocol(false),

	/**
	 * Once downloaded, release is considered immutable and not subject to change/update (default).
	 * Override this flag to make launcher check for remote updates of releases (this usually leads to
	 * slower application startup and {@code NotModified} statuses on release artifacts.
	 */
	updateReleases(false),

	/**
	 * Snapshots are considered volatile artifacts and are checked for updates (true) unless this check is disabled
	 * (false). Disable this to speed up application startup if you do not require latest snapshot updates,
	 * or you are aware there are no updates available.
	 */
	updateSnapshots(true),

	/**
	 * If set, MvnLauncher checks for and downloads updates but won't actually execute the application.
	 */
	updateOnly(false),

	/**
	 * URL of the remote (source) Maven repository.
	 * Defaults to local user repository: ${user.home}/.m2/repository
	 */
	repositoryUrl(url("file:///${user.home}/.m2/repository/"), true),

	/**
	 * Remote repository authentication (username). Only basic authentication is supported at the moment.
	 */
	repositoryUsername,

	/**
	 * Remote repository authentication (password). Only basic authentication is supported at the moment.
	 */
	repositoryPassword,

	credentialsKey(file("${user.home}/.springboot/credentials.key")),

	credentials(file("${user.home}/.springboot/credentials")),

	saveCredentials(false),

	/**
	 * Maven artifact entrypoint URI in form {@code groupId:artifactId:version}.
	 * If defined, launcher resolves it and uses its metadata to configure classpath and main class.
	 * If undefined (default), launcher proceeds as usual, using its own archive to load dependencies
	 * and resolve main class.
	 * This option enables using SpringBoot MvnLauncher as generic repo-based application launcher.
	 */
	artifact,

	;

	private Object value;

	MvnLauncherCfg() {
		this((String) null);
	}

	MvnLauncherCfg(Class type, String ... properties) {
		StringBuilder sb = new StringBuilder();
		for (String s : properties) {
			if (sb.length()>0) { sb.append(","); }
			sb.append(s);
		}
		String s = getProperty(getPropertyName(), sb.toString());
		load(properties(s.split(",")));
		this.value = s;
	}

	MvnLauncherCfg(String dflt) {
		this.value = getProperty(getPropertyName(), dflt);
	}

	MvnLauncherCfg(boolean dflt) {
		this.value = getProperty(getPropertyName(), Boolean.toString(dflt)).equalsIgnoreCase("true");
	}

	MvnLauncherCfg(URL dflt, boolean isDirectory) {
		this.value = url(getProperty(getPropertyName(), dflt.toString()), isDirectory);
	}

	MvnLauncherCfg(File dflt) {
		this.value = file(getProperty(getPropertyName(), dflt.toString()));
	}

	MvnLauncherCfg(long dflt) {
		this.value = Long.valueOf(getProperty(getPropertyName(), Long.toString(dflt)));
	}

	///

	/**
	 * Return recognized system property name mapped to this enum constant; all supported properties use common
	 * {@code MvnLauncher.*} prefix
	 */
	public String getPropertyName() {
		return String.format("%s.%s", MvnLauncher.class.getSimpleName(), name());
	}

	/**
	 * Returns {@code true} if the property value is defined (i.e. not null)
	 */
	public boolean isDefined() {
		return value != null;
	}

	/**
	 * Returns {@code true} if the property is defined and its value is boolean of {@code true}
	 * @return
	 */
	public boolean isSet() {
		return isDefined() && value instanceof Boolean && (Boolean) value;
	}

	/**
	 * Returns value as {@code String}
	 * @return
	 */
	public String value() {
		return value != null ? value.toString() : null;
	}

	public Object object() {
		return value;
	}

	public long longValue() {
		return (value != null && value instanceof Long) ? (Long) value : 0;
	}

	public URL asURL() {
		return (URL) value;
	}

	public File asFile() {
		return (File) value;
	}

	public MvnArtifact asMvnArtifact() {
		String s = value();
		return (s != null) ? MvnArtifact.parse(s) : null;
	}

	///

	/**
	 * Resolves property placeholders in a given system property or provided default, logs and returns resulting value
	 * @param key
	 * @param dflt
	 * @return
	 * @see #logPropertyValue(String, String)
	 */
	static private String getProperty(String key, String dflt) {
		String value = resolvePlaceholders(SystemPropertyUtils.getProperty(key, dflt));
		logPropertyValue(key, value);
		return value;
	}

	/**
	 * If {@link #debug} is enabled, information {@code key=value} is printed to system output
	 * @param key
	 * @param value
	 */
	private static void logPropertyValue(String key, String value) {
		// bootstrap considerations: "debug" value may not yet be initialized in which case consult the system property
		// directly
		if (isDebugEnabled()) {
			String s = (key.matches("(?i).*password.*")) ? "***" : value; // masking password; QDH solution
			Log.debug("%-30s : %s", key, s);
		}
	}

	static private boolean isDebugEnabled() {
		return (debug != null && debug.isSet()) || (debug == null && Boolean.getBoolean("MvnLauncher.debug"));
	}

	static private Properties properties(String ... urls) {
		Properties props = System.getProperties();
		for (int i = urls.length-1; i>=0; i--) {
			URL url = url(urls[i]);
			if (url == null) { continue; }
			try {
				InputStream in = url.openStream();
				Properties loaded = new Properties(props);
				loaded.load(in);
				props = loaded; // do assignment only after successful load
				Log.debug("> Loaded %s", url);
			} catch (FileNotFoundException ignore) {
			} catch (IOException e) {
				if (isDebugEnabled()) { e.printStackTrace(); }
			}
		}
		return props;
	}

	static private URL url(String surl) {
		return url(surl, false);
	}

	static private URL url(String surl, boolean isDirectory) {
		try {
			if (isDirectory && !surl.endsWith("/")) { surl += "/"; }
			String resolved = resolvePlaceholders(surl).replace('\\', '/');
			if (resolved.isEmpty()) { return null; }
			if (resolved.startsWith("classpath:")) {
				String path = resolved.replaceFirst("^classpath:/*", "");
				return Thread.currentThread().getContextClassLoader().getResource(path);
			} else {
				return new URL(resolved);
			}
		} catch (MalformedURLException e) {
			throw new MvnLauncherException(e, "Invalid URL: "+ surl);
		}
	}

	static private File file(String file) {
		return new File(resolvePlaceholders(file));
	}

	static private String trim(String s) {
		return (s != null) ? s.trim() : s;
	}

	private void load(Properties props) {
		// propagate all yet undefined (!) properties into system properties; no placeholders are resolved yet
		for (String key : props.stringPropertyNames()) {
			System.setProperty(key, System.getProperty(key, trim(props.getProperty(key))));
		}
	}


}
