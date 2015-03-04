package org.springframework.boot.launcher.util;

import org.springframework.boot.launcher.mvn.MvnArtifact;
import org.springframework.boot.launcher.mvn.Resolver;
import org.springframework.boot.launcher.mvn.ResolverContext;

import java.util.concurrent.ExecutionException;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class RepositorySupport {

    static public MvnArtifact resolve(String mvnuri) {
        MvnArtifact ma = new MvnArtifact(mvnuri);
        ResolverContext ctx = null;
        try {
            ctx = new ResolverContext(ma);
            try {
                ctx.startProgressMonitor();
                Resolver r = new Resolver(ctx, ma);
                return r.getResolvedArtifact();
            } finally {
                ctx.stopProgressMonitor();
            }
        } catch (ExecutionException e) {
            throw new UnsupportedOperationException(e);
        } catch (InterruptedException e) {
            throw new UnsupportedOperationException(e);
        } finally {
            IOHelper.close(ctx);
        }
    }

}
