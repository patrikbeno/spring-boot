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

import org.springframework.boot.launcher.MvnLauncherException;
import org.springframework.boot.launcher.vault.Vault;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

/**
 * Encapsulates Maven repository information. Maven repository is is identified by user-defined ID, and contains
 * repository URL and credentials: username and password.
 * This object can save itself into {@code SpringBoot Vault}, using repository ID as key, and storing plain
 * unencrypted URL value as reference, and user encrypted user credentials related to this repository configuration.
 * <p>
 * Values are stored under {@code springboot.mvnlauncher.repository.$repositoryId.(url|credentials)} properties, where
 * $repositoryId refers to any user-given repository ID, and credentials containing encprypted username:password
 * tuple.
 *
 * @author Patrik Beno
 */
public class MvnRepository {

    static private final String P_URL           = "springboot.mvnlauncher.repository.%s.url";
    static private final String P_CREDENTIALS   = "springboot.mvnlauncher.repository.%s.credentials";

    static public MvnRepository forRepositoryId(String repositoryId) {

        Vault vault = Vault.instance();

        String purl = String.format(P_URL, repositoryId);
        String pcredentials = String.format(P_CREDENTIALS, repositoryId);

        String url = vault.getProperty(purl);
        if (url == null) { return null; }

        String userinfo = vault.getProperty(pcredentials);

        if (userinfo == null) {
            return new MvnRepository(repositoryId, URI.create(url), null, null);
        }

        String[] userpass = userinfo.split(":", 2);

        return new MvnRepository(repositoryId, URI.create(url), userpass[0], userpass[1]);
    }

    private String id;
	private URI uri;
	private String username;
	private String password;

	public MvnRepository(String id, URI uri, String username, String password) {
        this.id = id;
        this.uri = uri;
        this.username = username;
        this.password = password;
    }

    public String getId() {
        return id;
    }

    public URI getURI() {
		return uri;
	}

	public String getUserName() {
		return username;
	}

	String getPassword() {
		return password;
	}

	boolean hasPassword() {
		return getUserName() != null && getPassword() != null;
	}

    public URL getURL() {
        try {
            return uri.toURL();
        } catch (MalformedURLException e) {
            throw new MvnLauncherException(e);
        }
    }

    ///

    public void save() {
        Vault vault = Vault.instance();
        String purl = String.format(P_URL, id);
        String pcredentials = String.format(P_CREDENTIALS, id);
        vault.setProperty(purl, uri.toASCIIString(), false);
        vault.setProperty(pcredentials, String.format("%s:%s", username, password), true);
        vault.save();
    }

}
