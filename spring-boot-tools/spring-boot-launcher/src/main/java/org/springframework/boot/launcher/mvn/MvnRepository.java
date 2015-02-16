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
import org.springframework.boot.launcher.util.Log;
import org.springframework.boot.launcher.vault.Vault;
import org.springframework.boot.launcher.vault.VaultPermission;

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
    static private final String P_USERNAME      = "springboot.mvnlauncher.repository.%s.username";
    static private final String P_PASSWORD      = "springboot.mvnlauncher.repository.%s.password";

    static public MvnRepository forRepositoryId(String repositoryId) {

        final Vault vault = Vault.instance();

        String purl = String.format(P_URL, repositoryId);
        String pusername = String.format(P_USERNAME, repositoryId);
        final String ppassword = String.format(P_PASSWORD, repositoryId);

        String url = vault.getProperty(purl);
        if (url == null) { return null; }

        String username = vault.getProperty(pusername);
        Decryptable password = new Decryptable() {
            @Override
            public String getValue() {
                return vault.getProperty(ppassword);
            }
        };

        return new MvnRepository(repositoryId, URI.create(url), username, password);
    }

    private String id;
	private URI uri;
	private String username;
	private Decryptable password;

	public MvnRepository(String id, URI uri, String username, Decryptable password) {
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

	public String getPassword() {
        VaultPermission.READ_PERMISSION.check();
        return password.getValue();
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
        if (username == null) {
            Log.info("Username is unspecified. Repository `%s` will be saved without credentials", id);
        }
        if (username != null && password == null) {
            Log.error(null, "Rejecting to save credentials with empty password. Repository: `%s`, username: `%s`", id, username);
            throw new MvnLauncherException("Missing password");
        }

        Vault vault = Vault.instance();

        String purl = String.format(P_URL, id);
        String pusername = String.format(P_USERNAME, id);
        String ppassword = String.format(P_PASSWORD, id);

        vault.setProperty(purl, uri.toASCIIString());
        if (username != null) vault.setProperty(pusername, username);
        if (password.getValue() != null) vault.setEncryptedProperty(ppassword, password.getValue());

        vault.save();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MvnRepository{");
        sb.append("id='").append(id).append('\'');
        sb.append(", uri=").append(uri);
        sb.append(", username='").append(username).append('\'');
        sb.append(", password='").append(password).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
