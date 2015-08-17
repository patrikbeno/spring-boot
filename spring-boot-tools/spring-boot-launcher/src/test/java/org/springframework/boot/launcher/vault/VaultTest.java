package org.springframework.boot.launcher.vault;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.launcher.url.UrlSupport;
import org.springframework.boot.launcher.util.Base64Support;
import org.springframework.boot.loader.util.SystemPropertyUtils;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

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
        System.out.println(s);
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
    public void decrypt() {
        Vault vault = Vault.instance();
        String s = vault.resolve(
                "Encrypted value: ${encrypted:6a3898c12e43128a3a4c10e13fdfa746aba9c8309f8e008b8835f766156cd5949397c48bdcf010727847073d331ef39e86c9ceab39c87cfe133f10b0733c4997b3ba5d3c0d1a848d1f284e3cf4ff04d02b1b05ada97b0d0914bb1ecd96f078d3a771ac6db800d9a606ccd35da71b60aff99ad7eff8cb9fb94aab3d68339bcd03af474c159a4ece88f446fed06924672201f78430e56d0b0dbc273e4bdadfddae30291da71d0ede508ec50ddf42657923888104db39976f68c9985077f45ad81dec3b3c971ccf1d9f636d83b011e5bc63136fe4b2bce51bf8a96f864238da1fb0c37d7fd5e21192c7f8d94c7620695c06a737056c3a734c6a1a11f4692c213519}.");
        System.out.println(s);
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

    @Test
    public void springEnvironment() throws Exception {
        Vault vault = Vault.instance();
        vault.setEncryptedProperty("encrypted.vault.property", "protected value");

        MockEnvironment env = VaultConfiguration.initEnvironment(new MockEnvironment());
        env.setProperty("my.property", "${encrypted.vault.property}");

        String s = env.resolvePlaceholders("${my.property}");

        Assert.assertEquals("protected value", s);
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
