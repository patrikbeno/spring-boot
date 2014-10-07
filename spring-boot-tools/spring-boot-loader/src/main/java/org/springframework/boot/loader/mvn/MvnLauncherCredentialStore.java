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
import org.springframework.boot.loader.util.StatusLine;

import java.io.*;
import java.lang.ref.SoftReference;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static java.lang.String.format;

/**
 * Secure credentials store implementation. Store contains list of URLs including
 * {@code user info} part. {@code User info} part contains both user name and the
 * password, and is encrypted using the user-specific key stored separately.
 * <p/>
 * Credential store is lazily loaded upon first access, and the encrypted data is
 * decrypted also lazily upon request.
 * @see org.springframework.boot.loader.mvn.MvnLauncherCfg#credentials
 * @see org.springframework.boot.loader.mvn.MvnLauncherCfg#keyfile
 * @see org.springframework.boot.loader.mvn.MvnLauncherCfg#saveCredentials
 * @see java.net.URL#getUserInfo()
 * @author Patrik Beno
 */
public class MvnLauncherCredentialStore {

	static private SoftReference<MvnLauncherCredentialStore> INSTANCE;

	static MvnLauncherCredentialStore instance() {
		MvnLauncherCredentialStore store = INSTANCE != null ? INSTANCE.get() : null;
		if (store == null) {
			INSTANCE = new SoftReference<MvnLauncherCredentialStore>(store = new MvnLauncherCredentialStore());
		}
		return store;
	}

    static public void save(String id, URL url, String username, String password) {
        instance().save(new MvnRepository(id, url, username, password));
    }

	private File keyFile = MvnLauncherCfg.keyfile.asFile();
	private File dataFile = MvnLauncherCfg.credentials.asFile();

	private String key;
	private Map<String, MvnRepository> indexByRepoId;

	private final Charset UTF8 = Charset.forName("UTF-8");
	private final String ALG = "AES";

	MvnRepository get(String repositoryId) {
        if (key != null) { key = loadKey(); }
        if (indexByRepoId == null) { indexByRepoId = load(); }
		return (indexByRepoId != null) ? indexByRepoId.get(repositoryId) : null;
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

	void saveKey(String key) {
		try {
			keyFile.getParentFile().mkdirs();
			FileWriter out = new FileWriter(keyFile);
			out.write(key);
			out.close();
		}
		catch (IOException e) {
			throw new MvnLauncherException(e, "Error saving key");
		}
	}

	String generateKey() {
		try {
			KeyGenerator keygen = KeyGenerator.getInstance(ALG);
            // use 256 bits for unlimited strength JCE, fallback to 128 if unavalable
            int bits = Math.max(256, Math.min(128, Cipher.getMaxAllowedKeyLength(ALG)));
            keygen.init(bits);
			SecretKey key = keygen.generateKey();
            String hexcoded = toHexString(key.getEncoded());
            return hexcoded;
		}
		catch (NoSuchAlgorithmException e) {
			throw new MvnLauncherException(e, "Error generating key");
		}
	}

    void init() {
        if (key == null) { key = loadKey(); }
        if (key == null) { key = generateKey(); }
        if (indexByRepoId == null) { indexByRepoId = load(); }
    }

	Map<String, MvnRepository> load() {
		Map<String, MvnRepository> credentials = new LinkedHashMap<String, MvnRepository>();
		if (!dataFile.exists()) {
            return credentials;
		}
		InputStream in = null;
		try {
            in = new BufferedInputStream(new FileInputStream(dataFile));
            Properties props = new Properties();
            props.load(in);
            for (String name : props.stringPropertyNames()) {
                if (!name.endsWith(".url")) { continue; }

                String id = name.replaceFirst("\\.url$", "");
                String url = props.getProperty(format("%s.url", id));
                String userinfo = props.getProperty(format("%s.userinfo", id));
                MvnRepository creds = new MvnRepository(id, new URL(url), userinfo);
                credentials.put(id, creds);
            }
        } catch (IOException e) {
			Log.error(e, "Error loading credentials: %s", dataFile);
		}
		finally {
			if (in != null) try { in.close(); } catch (IOException ignore) {}
		}
        return credentials;
	}

	void save(MvnRepository creds) {
        init();
        Log.debug("Saving credentials for repository id=%s, user=%s, url=%s", creds.getId(), creds.getUserName(), creds.getURL());
        indexByRepoId.put(creds.getId(), creds);
        save();
    }

    private synchronized void save() {
        init();

        if (indexByRepoId == null || indexByRepoId.isEmpty()) { return; }

        if (!keyFile.exists()) {
            StatusLine.push("Missing key. Initializing new one: %s", keyFile);
            try {
                if (key == null) { key = generateKey(); }
                saveKey(key);
                Log.info("Initialized new key file: %s. Protect it!", keyFile);
            } catch (Exception e) {
                Log.warn("Failed to initialize user's key file: %s : %s", keyFile, e);
            } finally {
                StatusLine.pop();
            }
        }

        if (!keyFile.exists()) {
            Log.error(null, "User's key file does not exist. Cannot save credentials. Check previous errors.");
            return;
        }

        Properties props = new Properties();

        for (MvnRepository creds : indexByRepoId.values()) {
            props.setProperty(format("%s.url", creds.getId()), creds.getURL().toExternalForm());
            props.setProperty(format("%s.userinfo", creds.getId()), creds.getUserinfo());
        }

        OutputStream out = null;
        try {
            out = new FileOutputStream(dataFile);
            dataFile.getParentFile().mkdirs();
            props.store(out, "SpringBoot MvnLauncher Credential Store");
            Log.info("Saved credentials: %s", dataFile);
		}
		catch (IOException e) {
			Log.error(e, "Error saving credentials");
		}
		finally {
			if (out != null) try { out.close(); } catch (IOException ignore) {}

        }
	}

	// Decodes hexadecimal string and decrypts the provided value using the build-in key.
	// Throws exception in case of failure.
	String decrypt(String hexcrypted) {
        init();
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
