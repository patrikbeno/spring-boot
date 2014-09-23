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

import org.springframework.boot.loader.util.Log;

import java.io.*;
import java.lang.ref.SoftReference;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Secure credentials store implementation. Store contains list of URLs including
 * {@code user info} part. {@code User info} part contains both user name and the
 * password, and is encrypted using the user-specific key stored separately.
 * <p/>
 * Credential store is lazily loaded upon first access, and the encrypted data is
 * decrypted also lazily upon request.
 * @see org.springframework.boot.loader.mvn.MvnLauncherCfg#credentials
 * @see org.springframework.boot.loader.mvn.MvnLauncherCfg#credentialsKey
 * @see org.springframework.boot.loader.mvn.MvnLauncherCfg#saveCredentials
 * @see java.net.URL#getUserInfo()
 * @author Patrik Beno
 */
class MvnLauncherCredentialStore {

	static private SoftReference<MvnLauncherCredentialStore> INSTANCE;

	static MvnLauncherCredentialStore instance() {
		MvnLauncherCredentialStore store = INSTANCE != null ? INSTANCE.get() : null;
		if (store == null) {
			INSTANCE = new SoftReference<MvnLauncherCredentialStore>(store = new MvnLauncherCredentialStore());
		}
		return store;
	}

	private File keyFile = MvnLauncherCfg.credentialsKey.asFile();
	private File dataFile = MvnLauncherCfg.credentials.asFile();

	private String key;

	private Map<URL, MvnRepositoryCredentials> index = new LinkedHashMap<URL, MvnRepositoryCredentials>();

	private final Charset UTF8 = Charset.forName("UTF-8");
	private final String ALG = "AES";
	private final int BITS = 256;

	private MvnLauncherCredentialStore() {
		String key = loadKey();
		if (key == null) {
			Log.info("> Missing key: %s. Initializing new one...", keyFile);
			key = saveKey(generateKey());
		}
		this.key = key;
		this.index = load();
	}

	MvnRepositoryCredentials get(URL url) {
		return index.get(url);
	}

	String loadKey() {
		if (!keyFile.exists()) {
			return null;
		}
		try {
			BufferedReader r = new BufferedReader(new FileReader(keyFile));
			String key = r.readLine();
			r.close();
			return key;
		}
		catch (IOException e) {
			throw new UnsupportedOperationException(e);
		}
	}

	String saveKey(String key) {
		try {
			keyFile.getParentFile().mkdirs();
			FileWriter out = new FileWriter(keyFile);
			out.write(key);
			out.close();
			Log.info("> Generated unique user-specific encryption key. Protect the file: %s", keyFile);
			return key;
		}
		catch (IOException e) {
			throw new MvnLauncherException(e, "Error saving key");
		}
	}

	String generateKey() {
		try {
			KeyGenerator keygen = KeyGenerator.getInstance(ALG);
			keygen.init(BITS);
			SecretKey key = keygen.generateKey();
			String hexcoded = toHexString(key.getEncoded());
			return hexcoded;
		}
		catch (NoSuchAlgorithmException e) {
			throw new MvnLauncherException(e, "Error generating key");
		}
	}

	Map<URL, MvnRepositoryCredentials> load() {
		Map<URL, MvnRepositoryCredentials> credentials = new LinkedHashMap<URL, MvnRepositoryCredentials>();
		if (!dataFile.exists()) {
			return credentials;
		}

		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(dataFile));
			for (String s; (s = in.readLine()) != null;) {
				if ((s = s.trim()).isEmpty()) {
					continue;
				}
				URL url = new URL(s);
				MvnRepositoryCredentials creds = new MvnRepositoryCredentials(new URL(
						url.getProtocol(), url.getHost(), url.getFile()),
						url.getUserInfo());
				credentials.put(url, creds);
			}
			return credentials;
		}
		catch (IOException e) {
			throw new MvnLauncherException(e, "damn!");
		}
		finally {
			if (in != null) try { in.close(); } catch (IOException ignore) {}
		}
	}

	void save(MvnRepositoryCredentials creds) {
		index.put(creds.getURL(), creds);
		FileWriter out = null;
		try {
			Log.debug("> Saving encrypted credentials: %s", dataFile);
			dataFile.getParentFile().mkdirs();
			out = new FileWriter(dataFile, true);
			Formatter f = new Formatter(out);
			URL u = creds.getURL();
			String userinfo = (creds.getUserinfo() != null) ? creds.getUserinfo()
					: encrypt(creds.getUserName() + ":" + creds.getPassword());
			f.format("%n%s://%s@%s", u.getProtocol(), userinfo, u.getHost());
			if (u.getPort() != -1) {
				f.format(":%s", u.getPort());
			}
			f.format("%s", u.getFile());

		}
		catch (IOException e) {
			Log.error(e.toString());
		}
		finally {
			if (out != null) try { out.close(); } catch (IOException ignore) {}
		}
	}

	// Decodes hexadecimal string and decrypts the provided value using the build-in key.
	// Throws exception in case of failure.
	String decrypt(String hexcrypted) {
		if (hexcrypted == null) {
			return null;
		}
		try {
			Cipher cipher = Cipher.getInstance(ALG);
			SecretKeySpec secret = new SecretKeySpec(fromHexString(key), ALG);
			cipher.init(Cipher.DECRYPT_MODE, secret);
			byte[] encrypted = fromHexString(hexcrypted);
			byte[] decrypted = cipher.doFinal(encrypted);
			return new String(decrypted, UTF8);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// encrypt the value using build-in key and convert the result into hexadecimal
	// string.
	// Throws exception in case of failure.
	String encrypt(String value) {
		if (value == null) {
			return null;
		}
		try {
			Cipher cipher = Cipher.getInstance(ALG);
			SecretKeySpec secret = new SecretKeySpec(fromHexString(key), ALG);
			cipher.init(Cipher.ENCRYPT_MODE, secret);
			byte[] encrypted = cipher.doFinal(value.getBytes(UTF8));
			String hexcrypted = new BigInteger(encrypted).toString(16);
			return hexcrypted;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private byte[] fromHexString(String hexcrypted) {
		return new BigInteger(hexcrypted, 16).toByteArray();
	}

	private String toHexString(byte[] bytes) {
		return new BigInteger(bytes).toString(16);
	}

}
