package org.springframework.boot.launcher.mvn;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.launcher.AbstractTest;

import java.io.File;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class RepositoryConnectorTest extends AbstractTest {

	@Test
	public void test() {
		Artifact a = new Artifact("org.springframework.boot:spring-boot-loader:1.2.0.BUILD-SNAPSHOT");
        ResolverContext context = new ResolverContext(a);
        try {
            RepositoryConnector c = new RepositoryConnector(Repository.forRepositoryId("default"), context, null);
            c.resolveSnapshotVersion(a);
            File f = c.resolve(a);
            Assert.assertNotNull(f);
        } finally {
            context.close();
        }
    }

}
