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
package org.springframework.boot.launcher.vault;

import org.springframework.boot.launcher.util.Base64Support;
import org.springframework.boot.launcher.util.Log;

import javax.crypto.Cipher;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.boot.launcher.util.Base64Support.getDecoder;
import static org.springframework.boot.launcher.util.Base64Support.getEncoder;
import static org.springframework.boot.loader.util.SystemPropertyUtils.resolvePlaceholders;

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

    static private final Charset UTF8 = Charset.forName("UTF-8");

    static public final String USER_DATA_FILE   = "${springboot.vault.user.dataFile:${user.home}/.springboot/${vault.fname:vault}.properties}";
    static public final String USER_CERT_FILE   = "${springboot.vault.user.certFile:${user.home}/.springboot/${vault.fname:vault}.crt}";
    static public final String USER_CERT_TYPE   = "${springboot.vault.user.certType:X.509}";
    static public final String USER_KEY_FILE    = "${springboot.vault.user.keyFile:${user.home}/.springboot/${vault.fname:vault}.key}";
    static public final String USER_KEY_TYPE    = "${springboot.vault.user.keyType:RSA}";

    static public final String SYSTEM_DATA_FILE = "${springboot.vault.system.dataFile:/etc/springboot/${vault.fname:vault}.properties}";
    static public final String SYSTEM_CERT_FILE = "${springboot.vault.system.certFile:/etc/springboot/${vault.fname:vault}.crt}";
    static public final String SYSTEM_CERT_TYPE = "${springboot.vault.system.certType:X.509}";
    static public final String SYSTEM_KEY_FILE  = "${springboot.vault.system.keyFile:/etc/springboot/${vault.fname:vault}.key}";
    static public final String SYSTEM_KEY_TYPE  = "${springboot.vault.system.keyType:RSA}";

	static private SoftReference<Vault> INSTANCE;

    static public Vault instance() { return vault(); }

	static public Vault vault() {
        VaultPermission.READ_PERMISSION.check();

        Vault store = INSTANCE != null ? INSTANCE.get() : null;
		if (store == null) {
            INSTANCE = new SoftReference<Vault>(store = userVault());
        }
        return store;
	}

    static private Vault userVault() {
        return new Vault(
                new File(resolvePlaceholders(USER_DATA_FILE)),
                new File(resolvePlaceholders(USER_CERT_FILE)),
                resolvePlaceholders(USER_CERT_TYPE),
                new File(resolvePlaceholders(USER_KEY_FILE)),
                resolvePlaceholders(USER_KEY_TYPE),
                null, systemVault());
    }

    static private Vault systemVault() {
        return new Vault(
                new File(resolvePlaceholders(SYSTEM_DATA_FILE)),
                new File(resolvePlaceholders(SYSTEM_CERT_FILE)),
                resolvePlaceholders(SYSTEM_CERT_TYPE),
                new File(resolvePlaceholders(SYSTEM_KEY_FILE)),
                resolvePlaceholders(SYSTEM_KEY_TYPE),
                System.getProperties(), null);
    }

    // private key
    private File keyFile;
    private String keyType;
    private PrivateKey key;

    // public key
    private File certFile;
    private String certType;
    private Certificate cert;

    // data
    private File dataFile;
    private Properties data;
    private Properties defaults;

    // link to parent vault, usually system-scoped
    private Vault parent;

    private Vault(File dataFile, File certFile, String certType, File keyFile, String keyType, Properties defaults, Vault parent) {
        this.keyFile = keyFile;
        this.keyType = keyType;
        this.certFile = certFile;
        this.certType = certType;
        this.dataFile = dataFile;
        this.parent = parent;
        this.defaults = defaults;
    }

    ///

    public boolean isReadable() {
        return key != null || keyFile.exists() && keyFile.canRead() && dataFile.exists() && dataFile.canRead();
    }

    public boolean isWritable() {
        return cert != null || certFile.exists() && certFile.canRead() && dataFile.exists() && dataFile.canWrite();
    }

    public Set<String> getPropertyNames() {
        VaultPermission.READ_PERMISSION.check();
        loadReadable();
        return data.stringPropertyNames();
    }

    public boolean containsKey(String key) {
        VaultPermission.READ_PERMISSION.check();
        loadReadable();
        return data.getProperty(key) != null || (parent != null && parent.containsKey(key));
    }

    public void setProperty(String key, String value) {
        VaultPermission.WRITE_PERMISSION.check();
        loadWritable();
        String s = resolve(value, true);
        data.setProperty(key, s);
        save();
    }

    public void setEncryptedProperty(String key, String value) {
        setProperty(key, String.format("${encrypt:%s}", value));
    }

    public String getProperty(String key) {
        VaultPermission.READ_PERMISSION.check();
        loadReadable();
        String value = resolve(data.getProperty(key));
        if (value == null && parent != null) {
            value = resolve(parent.getProperty(key), true);
        }
        return value;
    }

    public String getProperty(String key, String dflt) {
        String value = getProperty(key);
        return (value != null) ? value : dflt;
    }


    Pattern REFERENCE = Pattern.compile("\\$\\{([^}]+)\\}");
    Pattern ESCAPE = Pattern.compile("([^:]+):((?s).*)");

    protected String resolve(String text) {
        return resolve(text, false);
    }

    protected String resolve(String text, boolean write) {
        if (text == null) { return null; }

        StringBuilder sb = new StringBuilder();
        Matcher m = REFERENCE.matcher(text);
        while (m.find()) {
            String reference = m.group(1);
            String resolved = write ? write(reference) : read(reference);
            sb.append(text.substring(m.regionStart(), m.start()));
            sb.append(resolved != null ? resolved : reference);
            m = m.region(m.end(), m.regionEnd());
        }
        sb.append(text.substring(m.regionStart(), m.regionEnd()));
        return sb.toString();
    }

    private String read(String reference) {
        Matcher m = ESCAPE.matcher(reference);
        String type = m.matches() ? m.group(1) : null;
        String value = m.matches() ? m.group(2) : reference;
        if ("encrypted".equals(type)) {
            return decrypt(value);
        } else {
            return getProperty(value);
        }
    }

    private String write(String reference) {
        Matcher m = ESCAPE.matcher(reference);
        String type = m.matches() ? m.group(1) : null;
        String value = m.matches() ? m.group(2) : reference;
        if ("encrypt".equals(type)) {
            return encrypt(value);
        } else {
            return String.format("${%s}", reference);
        }
    }

    ///

    private void loadReadable() {
        if (data == null) { data = loadProperties(); }
    }

    private void loadWritable() {
        if (data == null) { data = loadProperties(); }
        if (cert == null) { cert = loadCertificate(); }
    }

    /**
     *
     * @return
     * @see java.security.spec.PKCS8EncodedKeySpec
     */
    private PrivateKey loadPrivateKey() {
        VaultPermission.READ_PERMISSION.check();
        if (!keyFile.exists()) {
            return null;
        }
        try {
            byte[] bytes = loadPEM(keyFile.toPath());
            KeySpec pkspec = new PKCS8EncodedKeySpec(bytes);
            KeyFactory kf = KeyFactory.getInstance(keyType);
            PrivateKey key = kf.generatePrivate(pkspec);
            return key;
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     *
     * @return
     * @see java.security.spec.X509EncodedKeySpec
     */
    private Certificate loadCertificate() {
        if (!certFile.exists()) {
            return null;
        }
        try {
            byte[] bytes = loadPEM(certFile.toPath());
            CertificateFactory cf = CertificateFactory.getInstance(certType);
            Certificate cert = cf.generateCertificate(new ByteArrayInputStream(bytes));
            return cert;
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads properties from specified data file. Returns empty properties if the source file is missing.
     * @return
     * @see #dataFile
     */
    private Properties loadProperties() {
        if (!dataFile.exists()) {
            return new Properties(defaults);
        }
        InputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(dataFile));
            Properties props = new Properties(defaults);
            props.load(in);
            return props;
        } catch (IOException e) {
            throw new VaultException(e, "Error loading data: " + dataFile);
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignore) {}
        }
    }

    private byte[] loadPEM(Path path) {
        try {
            String src = new String(Files.readAllBytes(path), UTF8);
            String base64 = Pattern.compile("(?m)(?s)^-----BEGIN .*-----$(.*)^-----+END .*-----$.*")
                    .matcher(src)
                    .replaceFirst("$1");
            byte[] bytes = Base64Support.getMimeDecoder().decode(base64);
            return bytes;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Saves the {@code properties} in associated {@code dataFile}.
     * @see #data
     * @see #dataFile
     */
    public synchronized void save() {
        saveData();
    }

    /**
     * @see #save()
     */
    private synchronized void saveData() {
        VaultPermission.WRITE_PERMISSION.check();
        OutputStream out = null;
        try {
            dataFile.getParentFile().mkdirs();
            out = new FileOutputStream(dataFile);
            if (data == null) { data = new Properties(); }
            data.store(out, "SpringBoot Vault Data");
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
        VaultPermission.WRITE_PERMISSION.check();
		if (value == null) {
			return null;
		}
        if (cert == null) {
            cert = loadCertificate();
        }
        if (cert == null) {
            throw new VaultException("Cannot encrypt input data. Missing certificate: "+certFile);
        }
        try {
			Cipher cipher = Cipher.getInstance(keyType);
            cipher.init(Cipher.ENCRYPT_MODE, cert.getPublicKey());
            byte[] buf = value.getBytes(UTF8);
            byte[] encrypted = cipher.doFinal(buf);
			String hexcrypted = getEncoder().encodeToString(encrypted);
            return String.format("${encrypted:%s}", hexcrypted);
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
        VaultPermission.READ_PERMISSION.check();
        if (value == null) {
            return null;
        }
        if (key == null) {
            key = loadPrivateKey();
        }
        if (key == null) {
            throw new VaultException("No private key. Cannot decrypt value.");
        }
        try {
            Cipher cipher = Cipher.getInstance(keyType);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] encrypted = getDecoder().decode(value);
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, UTF8);
        }
        catch (GeneralSecurityException e) {
            throw new VaultException(e, "Error decrypting encoded value...");
        }
    }

}
