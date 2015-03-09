package org.springframework.boot.launcher.mvn;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.launcher.AbstractTest;


/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class MvnLauncherTest extends AbstractTest {

	@Test
	public void test() {
		MvnArtifact artifact = new MvnArtifact("org.springframework.boot:spring-boot-loader:1.2.0.BUILD-SNAPSHOT");
		MvnLauncher launcher = new MvnLauncher(artifact);
		ClassLoader cl = launcher.resolve(artifact, Thread.currentThread().getContextClassLoader());
		Assert.assertNotNull(cl);

		// warning: sadly, class loader keeps using all the artifacts, and cannot be explicitly released (gc'ed)
		// the artifact spring-boot-loader artifact cannot be fully used for further testing (read-only mode, no updates)
	}
}
