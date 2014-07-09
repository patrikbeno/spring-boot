package org.springframework.boot.loader;

import org.junit.Test;
import org.springframework.boot.loader.jar.JarFile;

import java.io.File;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class MvnRepositoryConnectorTest {

	static {
		JarFile.registerUrlProtocolHandler();
		System.setProperty("MvnLauncher.defaults", "classpath:MvnLauncherCfg.properties");
	}

	MvnArtifact a = MvnArtifact.parse("org.springframework.boot:spring-boot-loader:1.1.2.BUILD-SNAPSHOT");
	MvnRepositoryConnector c = new MvnRepositoryConnector();

	@Test
	public void test() {
		c.resolveSnapshotVersion(a);
		File f = c.resolve(a);
	}

}
