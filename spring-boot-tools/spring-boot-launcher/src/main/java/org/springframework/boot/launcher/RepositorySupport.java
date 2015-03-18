package org.springframework.boot.launcher;

import org.springframework.boot.launcher.LauncherException;
import org.springframework.boot.launcher.mvn.Artifact;
import org.springframework.boot.launcher.mvn.Resolver;
import org.springframework.boot.launcher.mvn.ResolverContext;
import org.springframework.boot.launcher.util.IOHelper;

import java.util.concurrent.ExecutionException;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class RepositorySupport {

    static public Artifact resolve(String mvnuri) {
        Artifact ma = new Artifact(mvnuri);
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
            throw new LauncherException(e);
        } catch (InterruptedException e) {
            throw new LauncherException(e);
        } finally {
            IOHelper.close(ctx);
        }
    }

}
