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
package org.springframework.boot.loader.security;

import static org.springframework.boot.loader.security.VaultPermission.READ_PERMISSION;
import static org.springframework.boot.loader.security.VaultPermission.WRITE_PERMISSION;
import static org.springframework.boot.loader.util.SystemPropertyUtils.resolvePlaceholders;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;

import org.springframework.boot.loader.mvn.MvnLauncherCfg;
import org.springframework.boot.loader.util.Log;
import org.springframework.boot.loader.util.SystemPropertyUtils;

/**
 * SpringBoot Vault implements reusable secure data store based on asymmetric encryption.
 *
 * Keys are stored in hexadecimal encoding of the native binary Java format as provided by default implementation.
 * Further attempt to store a generally portable format has been rejected as this creates extra effort and further
 * bloats the code base of this bootstrap library.
 *
 * @see java.security.PrivateKey#getEncoded()
 * @see java.security.PublicKey#getEncoded()
 *
 * @author Patrik Beno
 */
public class Vault {

    static public final String SYSTEM   = "${springboot.vault.useSystem:false}";

    static public final String ALG      = "${springboot.vault.algorithm:RSA}";
    static public final String KEYSIZE  = "${springboot.vault.keysize:2048}";

    static public final String FNAME            = "${springboot.vault.fileName:vault}";
    static public final String FEXT_PRIVATE     = "${springboot.vault.fileExtPrivate:key}";
    static public final String FEXT_PUBLIC      = "${springboot.vault.fileExtPublic:pub}";
    static public final String FEXT_DATA        = "${springboot.vault.fileExtData:properties}";
    static public final String PATH_SYSTEM      = "${springboot.vault.systemPath:/etc/springboot}";
    static public final String PATH_USER        = "${springboot.vault.userPath:${user.home}/.springboot}";

    static private final String SYSTEM_PRIVATE_DFLT
            = String.format("%s/%s.%s", PATH_SYSTEM, FNAME, FEXT_PRIVATE);
    static private final String SYSTEM_PUBLIC_DFLT
            = String.format("%s/%s.%s", PATH_SYSTEM, FNAME, FEXT_PUBLIC);
    static private final String SYSTEM_DATA_DFLT
            = String.format("%s/%s.%s", PATH_SYSTEM, FNAME, FEXT_DATA);

    static private final String USER_PRIVATE_DFLT
            = String.format("%s/%s.%s", PATH_USER, FNAME, FEXT_PRIVATE);
    static private final String USER_PUBLIC_DFLT
            = String.format("%s/%s.%s", PATH_USER, FNAME, FEXT_PUBLIC);
    static private final String USER_DATA_DFLT
            = String.format("%s/%s.%s", PATH_USER, FNAME, FEXT_DATA);

    static public final String SYSTEM_PRIVATE   = String.format("${springboot.vault.systemPrivateKey:%s}", SYSTEM_PRIVATE_DFLT);
    static public final String SYSTEM_PUBLIC    = String.format("${springboot.vault.systemPublicKey:%s}", SYSTEM_PUBLIC_DFLT);
    static public final String SYSTEM_DATA      = String.format("${springboot.vault.systemData:%s}", SYSTEM_DATA_DFLT);

    static public final String USER_PRIVATE     = String.format("${springboot.vault.userPrivateKey:%s}", USER_PRIVATE_DFLT);
    static public final String USER_PUBLIC      = String.format("${springboot.vault.userPublicKey:%s}", USER_PUBLIC_DFLT);
    static public final String USER_DATA        = String.format("${springboot.vault.userData:%s}", USER_DATA_DFLT);

	static private SoftReference<Vault> INSTANCE;

    static public Vault instance() { return vault(); }

    static public void close() {
        INSTANCE.clear();
    }

	static public Vault vault() {
        READ_PERMISSION.check();

        Vault store = INSTANCE != null ? INSTANCE.get() : null;
		if (store == null) {
            boolean system = MvnLauncherCfg.useSystemVault.asBoolean();
            store = (system) ? systemVault() : userVault();
            INSTANCE = new SoftReference<Vault>(store);
        }
        return store;
	}

    static public void initSystemSecureStore() {
        initVault(systemVault());
    }

    static public void initUserSecureStore() {
        initVault(userVault());
    }

    static public void initVault(final Vault vault) {
        READ_PERMISSION.check();
        boolean allow = true;
        for (File f : Arrays.asList(vault.privateKeyFile, vault.publicKeyFile, vault.propertiesFile)) {
            allow &= !f.exists();
            if (f.exists()) {
                Log.warn("Rejected vault initialization: Refusing to overwrite existing file in %s", f);
            }
        }
        if (allow) {
            vault.saveKeyPair(vault.generateKeyPair());
            vault.save();
        }
    }

    static private Vault systemVault() {
        File privateKey = new File(resolvePlaceholders(SYSTEM_PRIVATE));
        File publicKey = new File(resolvePlaceholders(SYSTEM_PUBLIC));
        File props = new File(resolvePlaceholders(SYSTEM_DATA));
        return new Vault(privateKey, publicKey, props, null);
    }

    static private Vault userVault() {
        File privateKey = new File(resolvePlaceholders(USER_PRIVATE));
        File publicKey = new File(resolvePlaceholders(USER_PUBLIC));
        File props = new File(resolvePlaceholders(USER_DATA));
        return new Vault(privateKey, publicKey, props, systemVault());
    }

    // private key
    private File privateKeyFile;
    private PrivateKey privatekey;

    // public key
    private File publicKeyFile;
    private PublicKey publicKey;

    // data
    private File propertiesFile;
    private Properties properties;

    // link to parent vault, usually system one
    private Vault parent;

    private final Charset UTF8 = Charset.forName("UTF-8");
    private final String algorithm = SystemPropertyUtils.resolvePlaceholders(ALG);
    private final int keysize = Integer.parseInt(SystemPropertyUtils.resolvePlaceholders(KEYSIZE));


    private Vault(File privateKeyFile, File publicKeyFile, File propertiesFile, Vault parent) {
        this.privateKeyFile = privateKeyFile;
        this.publicKeyFile = publicKeyFile;
        this.propertiesFile = propertiesFile;
        this.parent = parent;
    }

    ///

    public void setProperty(String key, String value) {
        setProperty(key, value, true);
    }

    public void setProperty(String key, String value, boolean encrypt) {
        WRITE_PERMISSION.check();
        loaded();
        String s = (encrypt) ? encrypt(value) : value;
        properties.setProperty(key, s);
        save();
    }

    public String getProperty(String key) {
        READ_PERMISSION.check();
        loaded();
        String value = decrypt(properties.getProperty(key));
        if (value == null && parent != null) {
            value = parent.getProperty(key);
        }
        return value;
    }

    public boolean containsKey(String key) {
        READ_PERMISSION.check();
        loaded();
        return properties.getProperty(key) != null || parent.containsKey(key);
    }

    public String resolve(String text) {
        return resolve(text, Pattern.compile("\\{secure:([^}]+)\\}"));
    }

    private String resolve(String text, Pattern pattern) {
        StringBuilder sb = new StringBuilder();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String key = m.group(1);
            String resolved = getProperty(key);
            sb.append(text.substring(m.regionStart(), m.start()));
            sb.append(resolved != null ? resolved : key);
            m = m.region(m.end(), m.regionEnd());
        }
        sb.append(text.substring(m.regionStart(), m.regionEnd()));
        return sb.toString();
    }

    ///

    /**
     *
     * @return
     * @see java.security.spec.PKCS8EncodedKeySpec
     */
    private PrivateKey loadPrivateKey() {
        READ_PERMISSION.check();
        if (!privateKeyFile.exists()) {
            return null;
        }
        return (PrivateKey) loadKey(privateKeyFile);
    }

    /**
     *
     * @return
     * @see java.security.spec.X509EncodedKeySpec
     */
    private PublicKey loadPublicKey() {
        if (!publicKeyFile.exists()) {
            return null;
        }
        return (PublicKey) loadKey(publicKeyFile);
    }

    /**
     * Makes sure all keys and data is loaded, if available. Missing key or data files are ignored.
     */
    private void loaded() {
        if (properties == null) {
            properties = loadProperties();
        }
        if (privatekey == null) {
            privatekey = loadPrivateKey();
        }
        if (publicKey == null) {
            publicKey = loadPublicKey();
        }
    }

    /**
     * Loads properties from specified data file. Returns empty properties if the source file is missing.
     * @return
     * @see #propertiesFile
     */
    private Properties loadProperties() {
        if (!propertiesFile.exists()) {
            return new Properties();
        }
        InputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(propertiesFile));
            Properties props = new Properties();
            props.load(in);
            return props;
        } catch (IOException e) {
            throw new VaultException(e, "Error loading data: " + propertiesFile);
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignore) {}
        }
    }

    /**
     * Generate new key pair according to specified algorithm and key size.
     * @return
     * @throws org.springframework.boot.loader.security.VaultException when key generation fails.
     */
    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(algorithm);
            keyPairGenerator.initialize(keysize);
            KeyPair keyPair = keyPairGenerator.genKeyPair();
            return keyPair;
        }
        catch (GeneralSecurityException e) {
            throw new VaultException(e, "Error generating key");
        }
    }

    /**
     * Saves both private and public keys for a specified key pair.
     * @param keyPair
     */
	private void saveKeyPair(KeyPair keyPair) {
        saveKey(privateKeyFile, keyPair.getPrivate());
        saveKey(publicKeyFile, keyPair.getPublic());
	}

    /**
     * Saves the {@code key} in a given {@code file}, raising {@code VaultException} if anything fails.
     *
     * @param file
     * @param data
     */
	private void saveKey(File file, Key key) throws VaultException {
        WRITE_PERMISSION.check();
        FileOutputStream out = null;
        try {
            file.getParentFile().mkdirs();
            out = new FileOutputStream(file);
            boolean isPrivateKey = key instanceof PrivateKey;
            String data = toHexString(key.getEncoded());
            Properties props = new Properties();
            props.setProperty("algorithm", key.getAlgorithm());
            props.setProperty("format", key.getFormat());
            props.setProperty("type", isPrivateKey ? "private" : "public");
            props.setProperty("data", data);
            props.store(out, String.format("SpringBoot Vault %s Key", isPrivateKey ? "Private" : "Public"));
        } catch (IOException e) {
            throw new VaultException(e, "Error saving key: " + file);
        } finally {
            if (out != null) try { out.close(); } catch (IOException ignore) {}
        }
    }

    private Key loadKey(File f) {
        InputStream in = null;
        try {
            in = new FileInputStream(f);
            Properties props = new Properties();
            props.load(in);
            String algorithm = props.getProperty("algorithm");
            boolean isPrivateKey = props.getProperty("type").equals("private");
            byte[] data = fromHexString(props.getProperty("data"));
            KeyFactory factory = KeyFactory.getInstance(algorithm);
            Key key = isPrivateKey
                    ? factory.generatePrivate(new PKCS8EncodedKeySpec(data)) 
                    : factory.generatePublic(new X509EncodedKeySpec(data));
            return key;
        } catch (Exception e) {
            throw new VaultException(e, "Error loading file: " + f);
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignore) {}
        }
    }

    /**
     * Saves the {@code properties} in associated {@code propertiesFile}.
     * @see #properties
     * @see #propertiesFile
     */
    public synchronized void save() {
        saveData();
    }

    /**
     * @see #save()
     */
    private synchronized void saveData() {
        WRITE_PERMISSION.check();
        OutputStream out = null;
        try {
            propertiesFile.getParentFile().mkdirs();
            out = new FileOutputStream(propertiesFile);
            if (properties == null) { properties = new Properties(); }
            properties.store(out, "SpringBoot Vault Data");
		}
		catch (IOException e) {
			Log.error(e, "Error saving vault.");
		}
		finally {
			if (out != null) try { out.close(); } catch (IOException ignore) {}
        }
	}

    /**
     * Encrypts given value using user public key.
     * Encrypted binary data is hexadecimally encoded (to be suitable for text representation) and wrapped in
     * <tt>{encrypted:$hexstring}</tt> envelope so that this operation is transparent to idempotent {@code decrypt()}
     * @param value
     * @return
     * @see #decrypt(String)
     */
	public String encrypt(String value) {
        WRITE_PERMISSION.check();
		if (value == null) {
			return null;
		}
        if (publicKey == null) {
            publicKey = loadPublicKey();
        }
        try {
			Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] buf = value.getBytes(UTF8);
            byte[] encrypted = cipher.doFinal(buf);
			String hexcrypted = toHexString(encrypted);
            return String.format("{encrypted:%s}", hexcrypted);
		}
		catch (GeneralSecurityException e) {
			throw new VaultException(e, "Error encrypting value...");
		}
	}

    // Decodes hexadecimal string and decrypts the provided value using the build-in key.
    // Throws exception in case of failure.

    /**
     * Decrypts given string using the user's private key.
     * Encrypted value is expected to be wrapped in <tt>{encrypted:$data}</tt> envelope. If not, value is
     * considered unencrypted, and returned as is.
     * This implementation provides for idempotent decryption
     *
     * @param hexcrypted
     * @return
     */
    private String decrypt(String value) {
        READ_PERMISSION.check();
        if (value == null) {
            return null;
        }
        loaded();

        // unwrap, if needed
        Pattern p = Pattern.compile("\\{encrypted:([^\\}]+)\\}");
        Matcher m = p.matcher(value );
        if (!m.matches()) {
            return value; // nope, not encrypted
        }

        // yes, encrypted

        if (privatekey == null) {
            throw new VaultException("No private key. Cannot decrypt value.");
        }

        String hexcrypted = m.group(1);
        try {
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.DECRYPT_MODE, privatekey);
            byte[] encrypted = fromHexString(hexcrypted);
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, UTF8);
        }
        catch (GeneralSecurityException e) {
            throw new VaultException(e, "Error decrypting encoded value...");
        }
    }

    private byte[] fromHexString(String hexcrypted) {
        return new BigInteger(hexcrypted, 16).toByteArray();
	}

	private String toHexString(byte[] bytes) {
        return new BigInteger(bytes).toString(16);
    }

}
