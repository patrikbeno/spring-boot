package org.springframework.boot.loader.mvn;

import org.springframework.boot.loader.MvnLauncher;
import org.springframework.boot.loader.jar.JarFile;

import org.junit.Assert;
import org.junit.Test;


/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class MvnLauncherTest extends AbstractTest {

	@Test
	public void test() {
		MvnLauncher launcher = new MvnLauncher();
		ClassLoader cl = launcher.resolve(
				MvnArtifact.parse("org.springframework.boot:spring-boot-loader:1.2.0.BUILD-SNAPSHOT"),
				Thread.currentThread().getContextClassLoader());
		Assert.assertNotNull(cl);

		// warning: sadly, class loader keeps using all the artifacts, and cannot be explicitly released (gc'ed)
		// the artifact spring-boot-loader artifact cannot be fully used for further testing (read-only mode, no updates)
	}
}
