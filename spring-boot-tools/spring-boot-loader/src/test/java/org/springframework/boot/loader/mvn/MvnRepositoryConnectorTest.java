package org.springframework.boot.loader.mvn;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.loader.jar.JarFile;

import java.io.File;
import java.lang.reflect.Constructor;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class MvnRepositoryConnectorTest extends AbstractTest {

	@Test
	public void test() {
		MvnArtifact a = MvnArtifact.parse("org.springframework.boot:spring-boot-loader:1.2.0.BUILD-SNAPSHOT");
		MvnRepositoryConnector c = new MvnRepositoryConnector();
		c.resolveSnapshotVersion(a);
		File f = c.resolve(a);
		Assert.assertNotNull(f);
	}

}
