package org.springframework.boot.launcher.vault;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.launcher.util.Base64Support;
import org.springframework.boot.loader.util.SystemPropertyUtils;
import org.springframework.boot.loader.util.UrlSupport;

import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class VaultTest {

    static {
        UrlSupport.init();
    }

    Path path;

    @Before
    public void before() {
        try {
            String uuid = UUID.randomUUID().toString();
            path = Paths.get(SystemPropertyUtils.resolvePlaceholders("${java.io.tmpdir}"), uuid);
            path.toFile().mkdirs();
            Path data = Paths.get(SystemPropertyUtils.resolvePlaceholders("${java.io.tmpdir}"), uuid, "vault.properties");
            Path crt  = Paths.get(SystemPropertyUtils.resolvePlaceholders("${java.io.tmpdir}"), uuid, "vault.crt");
            Path key = Paths.get(SystemPropertyUtils.resolvePlaceholders("${java.io.tmpdir}"), uuid, "vault.key");
            Files.copy(getClass().getResourceAsStream("/test.crt"), crt);
            Files.copy(getClass().getResourceAsStream("/test.key"), key);
            System.setProperty("springboot.vault.user.dataFile", data.toString());
            System.setProperty("springboot.vault.user.certFile", crt.toString());
            System.setProperty("springboot.vault.user.keyFile", key.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @After
    public void after() throws IOException {
        FileFilter deleter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                if (pathname.isFile()) {
                    pathname.delete();
                } else {
                    pathname.listFiles(this);
                    pathname.delete();
                }
                return false;
            }
        };
        path.toFile().listFiles(deleter);
        path.toFile().delete();
    }

    @Test
    public void single() {
        Vault vault = Vault.instance();
        vault.setProperty("key1", "value1");
        Assert.assertEquals("value1", vault.getProperty("key1"));
    }

    @Test
    public void resolveSingle() {
        Vault vault = Vault.instance();
        vault.setProperty("key1", "value1");
        Assert.assertEquals("key1=value1", vault.resolve("key1=${key1}"));
    }

    @Test
    public void resolveMultiple() {
        Vault vault = Vault.instance();
        vault.setProperty("key1", "value1");
        vault.setProperty("key2", "value2");
        Assert.assertEquals("value1", vault.getProperty("key1"));
        Assert.assertEquals("value2", vault.getProperty("key2"));
        Assert.assertEquals("key1=value1, key2=value2", vault.resolve("key1=${key1}, key2=${key2}"));
    }


    @Test
    public void encrypt() {
        Vault vault = Vault.instance();
        String s = vault.resolve("Encrypted value: ${encrypt:value}.", true);
        Assert.assertTrue(s.matches("Encrypted value: \\$\\{encrypted:[^}]+\\}."));
        Assert.assertTrue(vault.resolve(s).matches("Encrypted value: value."));
    }

    @Test
    public void setEncryptedProperty() {
        Vault vault = Vault.instance();
        vault.setEncryptedProperty("key", "value");
        Assert.assertEquals("value", vault.getProperty("key"));
    }

    @Test
    public void setEncryptedPropertyMultiline() {
        Vault vault = Vault.instance();
        String s = "line1\nline2\nline3";
        vault.setEncryptedProperty("key", s);
        Assert.assertEquals(s, vault.getProperty("key"));
    }

    @Test
    public void decrypt() {
        Vault vault = Vault.instance();
        String s = vault.resolve("Encrypted value: ${encrypted:CfTPBtxs4vZnYF2pW3ouwvB4CzZJ3Av2Lc2a6wp1+sPmSJxcK/HXsjx3ntNXn72VFpsM+f86iwYj+SxJKY/FBK9MZETAioPW4F8CDGoHGzFXtk/JCaCdtvjWJTfYjJRjTiTnM83r7tliCBfKxlgEKjZkPq/hOT135dt6ZKK5avGLQ0iNkb5hF25AlyANr4HvSUPFDEu5QfZeNO206jowg4mALde5s9W0kRZiZKAvptvQtF/H2HuNM3S2E6TS0N3T4NR7zRwlzOxw6CGu0OI5qvQYcqHKjY6tKp0VQjyCHyioy+gyt/HHm1GceLlQgkqq6zIKk5dp+TfohcMxLQ1eJA==}.");
        Assert.assertTrue(s.matches("Encrypted value: value."));
    }


    @Test
    public void untrusted() throws Exception {
        Path path = Paths.get(SystemPropertyUtils.resolvePlaceholders("${springboot.vault.user.certFile}"));
        byte[] src = Files.readAllBytes(path);
        String s = new String(src).replaceFirst("(?m)(?s)---+BEGIN .*---+$(.*)^---+END .*", "$1");
        byte[] bytes = Base64Support.getMimeDecoder().decode(s);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert = cf.generateCertificate(new ByteArrayInputStream(bytes));


        KeyStore truststore = KeyStore.getInstance(KeyStore.getDefaultType());
        truststore.load(
                Files.newInputStream(Paths.get(System.getProperty("java.home"), "lib/security/cacerts")),
                "changeit".toCharArray());

        try {
            verify(cf, cert, truststore);
            Assert.fail();
        } catch (CertPathValidatorException ignore) {
        }

        // enable trust
        truststore.setCertificateEntry("test", cert);

        verify(cf, cert, truststore);
    }

    private void verify(CertificateFactory cf, Certificate cert, KeyStore truststore) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, InvalidAlgorithmParameterException, CertPathValidatorException {
        CertPath cp = cf.generateCertPath(Arrays.asList(cert));
        CertPathValidator cpv = CertPathValidator.getInstance(CertPathValidator.getDefaultType());
        PKIXParameters params = new PKIXParameters(truststore);
        params.setRevocationEnabled(false);
        cpv.validate(cp, params);
        System.out.println(cert);
        System.out.println(cert.getPublicKey());
    }
}
