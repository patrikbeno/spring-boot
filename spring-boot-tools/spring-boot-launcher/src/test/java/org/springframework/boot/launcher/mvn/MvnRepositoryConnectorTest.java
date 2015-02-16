package org.springframework.boot.launcher.mvn;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.launcher.AbstractTest;

import java.io.File;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class MvnRepositoryConnectorTest extends AbstractTest {

	@Test
	public void test() {
		MvnArtifact a = new MvnArtifact("org.springframework.boot:spring-boot-loader:1.2.0.BUILD-SNAPSHOT");
        MvnRepositoryConnectorContext context = new MvnRepositoryConnectorContext();
        try {
            MvnRepositoryConnector c = new MvnRepositoryConnector(MvnRepository.forRepositoryId("default"), context, null);
            c.resolveSnapshotVersion(a);
            File f = c.resolve(a);
            Assert.assertNotNull(f);
        } finally {
            context.close();
        }
    }

}
