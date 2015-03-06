package org.springframework.boot.launcher;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.springframework.boot.launcher.vault.Vault;
import org.springframework.boot.loader.util.SystemPropertyUtils;
import org.springframework.boot.loader.util.UrlSupport;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public abstract class AbstractTest {

    Path crt, key;

    @Before
    public void before() throws Exception {
        UUID uuid = UUID.randomUUID();
        crt = Paths.get(System.getProperty("java.io.tmpdir"), uuid.toString()+".crt");
        key = Paths.get(System.getProperty("java.io.tmpdir"), uuid.toString() + ".key");
        Files.copy(AbstractTest.class.getResourceAsStream("/test.crt"), crt, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(AbstractTest.class.getResourceAsStream("/test.key"), key, StandardCopyOption.REPLACE_EXISTING);
        System.setProperty("MvnLauncher.defaults", "classpath:MvnLauncherCfg.properties");
        MvnLauncherCfg.configure();

        System.setProperty("springboot.vault.user.certFile", crt.toString());
        System.setProperty("springboot.vault.user.keyFile", key.toString());
        Vault.instance();
    }

    @After
    public void after() throws Exception {
        Vault.close();
        Files.deleteIfExists(crt);
        Files.deleteIfExists(key);
    }
}
