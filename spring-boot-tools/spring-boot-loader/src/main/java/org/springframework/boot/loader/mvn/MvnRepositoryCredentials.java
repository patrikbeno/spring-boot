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

import java.net.URL;

/**
 * @author Patrik Beno
 */
class MvnRepositoryCredentials {

	private URL url;
	private String userinfo; // encrypted
	private String username;
	private String password;

	MvnRepositoryCredentials(URL url, String userinfo) {
		this.url = url;
		this.userinfo = userinfo;
	}

	MvnRepositoryCredentials(URL url, String username, String password) {
		this.url = url;
		this.username = username;
		this.password = password;
	}

	URL getURL() {
		return url;
	}

	String getUserinfo() {
		return userinfo;
	}

	String getUserName() {
		if (username == null) { decrypt();}
		return username;
	}

	String getPassword() {
		if (password == null) { decrypt();}
		return password;
	}

	private void decrypt() {
		String decrypted = MvnLauncherCredentialStore.instance().decrypt(userinfo);
		String[] parts = (decrypted != null) ? decrypted.split(":") : null;
		username = (parts != null && parts.length > 0) ? parts[0] : null;
		password = (parts != null && parts.length > 1) ? parts[1] : null;
	}

	boolean hasPassword() {
		return getUserName() != null && getPassword() != null;
	}


}
