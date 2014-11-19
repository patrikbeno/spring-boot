package org.springframework.boot.loader.security;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.loader.mvn.MvnLauncherCfg;
import org.springframework.boot.loader.util.SystemPropertyUtils;
import org.springframework.boot.loader.util.UrlSupport;

import static org.springframework.boot.loader.security.Vault.vault;

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
        path = Paths.get(SystemPropertyUtils.resolvePlaceholders("${java.io.tmpdir}/" + UUID.randomUUID()));
        System.setProperty("springboot.vault.systemPath", path.toString()+"/system");
        System.setProperty("springboot.vault.userPath", path.toString()+"/user");
        Vault.initSystemSecureStore();
        Vault.initUserSecureStore();
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
    public void test() {
        Vault vault = Vault.instance();
        String key = "test";
        String in = "value";
        vault.setProperty(key, in);
        String out = vault.getProperty(key);
        Assert.assertEquals(in, out);
    }

    @Test
    public void test2() {
        Vault vault = Vault.instance();
        String value = "value";
        vault.setProperty("secret", value);
        String resolved = vault.resolve("{secure:secret}");
        Assert.assertEquals(value, resolved);
    }

    @Test
    public void test3() {
        Vault vault = Vault.instance();
        vault.setProperty("secret", "value");
        vault.setProperty("test", "secret: {secure:secret}, and again: {secure:secret}!", false);
        String resolved = vault.resolve(vault.getProperty("test"));
        Assert.assertEquals("secret: value, and again: value!", resolved);
    }

    @Test
    public void test4() {
        System.setProperty(MvnLauncherCfg.useSystemVault.getPropertyName(), "true");
        MvnLauncherCfg.configure();
        vault().setProperty("test", "value");
        System.setProperty(MvnLauncherCfg.useSystemVault.getPropertyName(), "false");
        MvnLauncherCfg.configure();
        String s = vault().getProperty("test");
        Assert.assertEquals("value", s);
    }

}
