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
package org.springframework.boot.launcher;

import static org.springframework.boot.loader.util.SystemPropertyUtils.resolvePlaceholders;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.launcher.mvn.MvnArtifact;
import org.springframework.boot.launcher.mvn.MvnLauncher;
import org.springframework.boot.launcher.mvn.MvnRepository;
import org.springframework.boot.launcher.util.Log;
import org.springframework.boot.launcher.util.StatusLine;
import org.springframework.boot.launcher.vault.Vault;
import org.springframework.boot.loader.Launcher;
import org.springframework.boot.loader.util.SystemPropertyUtils;

/**
 * MvnLauncher bootstrap configuration (loaded from external properties file or system
 * properties).
 * <p/>
 * Each configuration property has its built-in default. Defaults can be either overridden
 * from command line (using {@code -DMvnLauncher.enumName=value} property syntax), or from
 * a property file specified in {@code -DMvnLauncher.defaults} property (which itself
 * defaults to {@code $ user.home}/.springboot/defaults.properties}).
 *
 * @see org.springframework.boot.launcher.mvn.MvnLauncher
 * @author Patrik Beno
 */
public enum MvnLauncherCfg {

	// naming convention broken intentionally: enum name() is also property name used in
	// -DMvnLauncher.{name}={value} or --MvnLauncher.{name}={value}

	/**
	 * Particular application home directory; Usually not current directory, not user
	 * directory, not a system folder. Might be an application installation folder or an
	 * application data root folder. This one is first because it is referenced by
	 * {@code defaults}. Defaults to ${java.home}
	 * @see #defaults
	 */
	apphome("${java.home}"),

	/**
	 * Particular application name, typically base name of the script
	 */
	appname("SpringBootApp"),

	/**
	 * Properties containing MvnLauncher user-specific configuration defaults; the file,
	 * if exists, is loaded during initialization of this property and its values are
	 * propagated into system properties (if not yet defined using {@code -Dname=value} on
	 * JVM command line)
	 *
	 * Keep this first to force loading of user-defined defaults before resolving other
	 * properties
	 */
	defaults(list("file:springboot.properties", // current folder
			"file:///${MvnLauncher.apphome}/${MvnLauncher.appname}.properties", // application defaults
			"file:///${MvnLauncher.apphome}/springboot.properties", // application home folder
			"file:///${user.home}/.springboot/defaults.properties", // user defaults
			"file:///etc/springboot/defaults.properties", // system defaults // todo: what about windows?
			"classpath:META-INF/springboot/defaults.properties" // application/library defaults
	)),

    /**
     * Maven repositories to use (comma separated list).
     * Defaults: github,central (repository URLs are automatically exported to system properties if undefined, hence
     * providing fallback in case of missing configuration)
     */
    repositories("github,central"),

    /**
     * Launcher cache directory. Defaults to {@code $ user.home}/.springboot/cache}
     */
    cache("${user.home}/.springboot/cache"),

    /**
     * Maven artifact entrypoint URI in form {@code groupId:artifactId:version}. If
     * defined, launcher resolves it and uses its metadata to configure classpath and main
     * class. If undefined (default), launcher proceeds as usual, using its own archive to
     * load dependencies and resolve main class. This option enables using SpringBoot
     * MvnLauncher as generic repo-based application launcher.
     */
    artifact,


    /**
     * Enable configuration and connector logging
     */
	debug(false),

    /**
     * Supresses status line and all other messages except errors
     */
    quiet(false),

    /**
     * Report activity status
     */
    statusLine(System.console() != null),

	/**
	 * If set, final resolved classpath will be logged (debug level must be enabled).
	 */
	showClasspath(false),

	/**
	 * If set, no artifacts are downloaded or updated from remote repository, and any
	 * artifact may end up unresolved (NotFound). Launcher rejects incomplete class paths
	 * and aborts, unless overriden using {@link #failOnError}.
	 */
	offline(false),

	/**
	 * Specifies interval (in minutes) within which result of the latest successful update
	 * operation remains valid. This means that remote repository is checked only once in
	 * a specified interval. In other words, if you run the application twice, it will
	 * check for updates only once (first run). Second run will behave as if the offline
	 * mode was enabled (no remote repository contact whatsoever, unless the dependency is
	 * missing). Next update check will be allowed only after the specified update
	 * interval elapsed. Default: 1 day (1440 minutes)
	 */
	updateInterval(Long.toString(TimeUnit.DAYS.toMinutes(1))),

	/**
	 * If set, downloads are verified using SHA1 signature provided by remote repository:
	 * signature mismatch is considered an error, and the artifact is marked
	 * {@code Invalid}. Enabled by default.
	 * @see org.springframework.boot.loader.archive.MvnArtifact.Status#Invalid
	 */
	verify(true),

	/**
	 * If set, cache is ignored and all artifacts are re-downloaded.
	 */
	ignoreCache(false),

	/**
	 * If set, errors like missing artifacts and checksum mismatches cause launcher to
	 * reject application execution. Enabled by default.
	 */
	failOnError(true),

	/**
	 * By default file:// repository is used directly and artifacts are not cached in
	 * launcher cache. You may want to enable file:// protocol caching if you have
	 * concurrency issues using your file:// based repository.
	 */
	cacheFileProtocol(false),

	/**
	 * Once downloaded, release is considered immutable and not subject to change/update
	 * (default). Override this flag to make launcher check for remote updates of releases
	 * (this usually leads to slower application startup and {@code NotModified} statuses
	 * on release artifacts.
	 */
	updateReleases(false),

	/**
	 * Snapshots are considered volatile artifacts and are checked for updates (true)
	 * unless this check is disabled (false). Disable this to speed up application startup
	 * if you do not require latest snapshot updates, or you are aware there are no
	 * updates available.
	 */
	updateSnapshots(true),

	/**
	 * If reset (false), MvnLauncher checks for and downloads updates but won't actually
	 * execute the application. Default: {@code true}
	 */
	execute(true),

	/**
	 * Shortcut configuration property designed to force global update without the need to
	 * separately set individual fine-graned properties. Default is {@code false}.
	 * @see #updateSnapshots
	 * @see #updateReleases
	 * @see #updateInterval
	 * @see #offline
	 * @see #ignoreCache
	 */
	update(false),

    /**
     * Maximum number of concurrent artifact resolvers.
     */
    resolvers("15"),

    /**
     * Maximum number of concurrent artifact downloaders. Download is triggered by a resolver.
     */
    downloaders("5"),

	;

	private String dflt;

	MvnLauncherCfg() {
		dflt(null);
	}

	MvnLauncherCfg(String dflt) {
		dflt(dflt);
	}

	MvnLauncherCfg(boolean dflt) {
		dflt(Boolean.toString(dflt));
	}

	// /

	public String get() {
		String pname = getPropertyName();
		return System.getProperty(pname, dflt);
	}

	void set(String value) {
		if (value != null) {
			System.getProperties().setProperty(getPropertyName(), value);
		}
		else {
			System.getProperties().remove(getPropertyName());
		}
	}

	void dflt(String value) {
		this.dflt = value;
	}

	/**
	 * Return recognized system property name mapped to this enum constant; all supported
	 * properties use common {@code MvnLauncher.*} prefix
	 */
	public String getPropertyName() {
		return String.format("MvnLauncher.%s", name());
	}

	/**
	 * Returns {@code true} if the property value is defined (i.e. not null)
	 */
	public boolean isDefined() {
		return System.getProperty(getPropertyName()) != null;
	}

	/**
	 * Returns value as {@code String}
	 * @return
	 */
	public String raw() {
		return get();
	}

	public String asString() {
		return resolvePlaceholders(get());
	}

    public List<String> asList() {
        return Arrays.asList(asString().split(","));
    }

	public boolean asBoolean() {
		return Boolean.parseBoolean(asString());
	}

	public int asInt() {
		return Integer.parseInt(asString());
	}

	public long asLong() {
		return Long.parseLong(asString());
	}

	public URL asURL(boolean directory) {
		return url(asString(), directory);
	}

	public URI asURI(boolean directory) {
        try {
            return url(asString(), directory).toURI();
        } catch (URISyntaxException e) {
            throw new MvnLauncherException(e);
        }
    }

	public File asFile() {
		return new File(asString());
	}

	public MvnArtifact asMvnArtifact() {
		return new MvnArtifact(asString());
	}

	// /

	static public void configure() {
		String version = Launcher.class.getPackage().getImplementationVersion();

		Log.debug("Configuring SpringBoot MvnLauncher %s", (version != null ? version : "(unknown version)"));

		Properties props = properties(MvnLauncherCfg.defaults.get().split(","));

		// propagate all yet undefined foreign properties from loaded resources into
		// system properties
		// foreign == not MvnLauncher.*
		String header = "Setting system properties defined in defaults:";
		for (String pname : props.stringPropertyNames()) {
			if (pname.startsWith("MvnLauncher.")) {
				continue;
			}

			String value = props.getProperty(pname);
			String previous = System.setProperty(pname, System.getProperty(pname, value));

			if (!value.equals(previous)) {
				if (header != null) {
					Log.debug(header);
					header = null;
				}
				Log.debug("- %-30s : %s", pname, value);
			}
		}

		// propagate defaults, if available
		for (MvnLauncherCfg v : values()) {
			String value = System.getProperty(v.getPropertyName());
			if (value == null && v.dflt != null) {
				System.getProperty(v.getPropertyName(), SystemPropertyUtils.resolvePlaceholders(v.dflt));
			}
		}

		header = "MvnLauncher configuration:";
		for (MvnLauncherCfg v : values()) {
			// override built-in default with value from resources
			v.dflt = props.getProperty(v.getPropertyName(), v.dflt);

			if (isDebugEnabled()) {
				if (header != null) {
					Log.debug(header);
					header = null;
				}
				Log.debug("- %-30s : %s", v.getPropertyName(), SystemPropertyUtils.resolvePlaceholders(v.asString()));
			}
		}

        if (downloaders.asInt() < resolvers.asInt()) {
            Log.warn("Suboptimal configuration: number of downloaders should not be lower than number of resolvers (%d<%d)",
                    downloaders.asInt(), resolvers.asInt());
        }
	}
    
    static public void export() {
		for (MvnLauncherCfg v : MvnLauncherCfg.values()) {
            if (v.get() == null) { continue; }
            System.setProperty(v.getPropertyName(), v.asString());
        }
    }

    static public boolean isDebugEnabled() {
		return (debug != null && debug.asBoolean()) || (debug == null && Boolean.getBoolean("MvnLauncher.debug"));
	}

	static private String list(String... properties) {
		StringBuilder sb = new StringBuilder();
		for (String s : properties) {
			if (sb.length() > 0) {
				sb.append(",");
			}
			sb.append(s);
		}
		return sb.toString();
	}

	static private Properties properties(String... uris) {
		Properties props = null;
		for (int i = uris.length - 1; i >= 0; i--) {
			URL url = url(uris[i], false);
			if (url == null) {
				continue;
			}
            StatusLine.push("Loading %s", url);
			try {
				InputStream in = url.openStream();
				Properties loaded = new Properties(props);
				loaded.load(in);
				props = loaded; // do assignment only after successful load
				Log.debug("Loaded %s", url);
			}
			catch (FileNotFoundException ignore) {
				// diagnostics: in debug mode, the "> Loaded" string is not printed...
				// this should be enough for this case
			}
			catch (IOException e) {
				if (isDebugEnabled()) {
					e.printStackTrace();
				}
			}
            finally {
                StatusLine.pop();
            }
		}
		return (props != null) ? props : new Properties();
	}

	static private URL url(String surl, boolean isDirectory) {
		try {
			if (isDirectory && !surl.endsWith("/")) {
				surl += "/";
			}
			String resolved = resolvePlaceholders(surl).replace('\\', '/');
			return new URL(resolved);
		}
		catch (MalformedURLException e) {
			throw new MvnLauncherException(e, "Invalid URL: " + surl);
		}
	}

	///

	@Override
	public String toString() {
		return name() + "=" + raw();
	}

}
