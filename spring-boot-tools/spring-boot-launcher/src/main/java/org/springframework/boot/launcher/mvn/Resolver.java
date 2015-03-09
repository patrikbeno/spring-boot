package org.springframework.boot.launcher.mvn;

import org.springframework.boot.launcher.MvnLauncherException;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.JarFileArchive;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.jar.Manifest;

import static org.springframework.boot.launcher.util.IOHelper.close;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class Resolver {

    /**
     * Name of the manifest attribute containing the comma-delimited list Maven URIs
     * (runtime dependencies, provided build-time)
     */
    static public final String MF_DEPENDENCIES = "Spring-Boot-Dependencies";

    ResolverContext context;

    MvnArtifact artifact;

    String mainClass;

    List<MvnArtifact> dependencies;

    Future<Resolver> fresolve;
    Future<Resolver> fdownload;

    List<Resolver> fdependencies;

    public Resolver(ResolverContext context, MvnArtifact artifact) {
        this.context = context;
        this.artifact = artifact;
    }

    public MvnArtifact getArtifact() {
        return artifact;
    }

    public MvnArtifact getResolvedArtifact() throws ExecutionException, InterruptedException {
        return download().get().getArtifact();
    }

    synchronized Future<Resolver> resolve() {
        if (fresolve != null) { return fresolve; }
        return fresolve = context.resolvers.submit(new Callable<Resolver>() {
            @Override
            public Resolver call() throws Exception {
                connector().resolve(artifact);
                return Resolver.this;
            }
        });
    }

    synchronized Future<Resolver> download() {
        if (fdownload != null) { return fdownload; }
        return fdownload = context.downloaders.submit(new Callable<Resolver>() {
            @Override
            public Resolver call() throws Exception {
                resolve().get();
                connector().download(artifact);
                return Resolver.this;
            }
        });
    }

    synchronized List<Resolver> dependencies() {
        if (fdependencies != null) { return fdependencies; }
        resolveMainClassAndDependencies();
        List<Resolver> resolvers = new LinkedList<Resolver>();
        for (MvnArtifact ma : dependencies) {
            Resolver r = new Resolver(context, ma);
            resolvers.add(r);
            r.resolve();
        }
        return resolvers;
    }

    SortedSet<Resolver> resolveAll() {
        Comparator<Resolver> byFullArtifactName = new Comparator<Resolver>() {
            @Override
            public int compare(Resolver o1, Resolver o2) {
                return o1.getArtifact().asString().compareTo(o2.getArtifact().asString());
            }
        };
        SortedSet<Resolver> all = new TreeSet<Resolver>(byFullArtifactName);
        resolveMainClassAndDependencies();
        all.add(this);
        all.addAll(dependencies());
        return all;
    }

    private void resolveMainClassAndDependencies() {
        if (this.dependencies != null) {
            return;
        }
        JarFileArchive jar = null;
        try {
            File f = download().get().getArtifact().getFile();
            jar = new JarFileArchive(f);
            this.mainClass = jar.getMainClass();
            this.dependencies = getArtifacts(jar);

            // propagate all to context
            context.artifacts.add(artifact);
            for (MvnArtifact ma : dependencies) {
                ma.setStatus(MvnArtifact.Status.Resolving);
                context.artifacts.add(ma);
            }

        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        } finally {
            close(jar);
        }
    }

    /**
     * Load list of Maven dependencies from manifest of a specified archive
     */
    private List<MvnArtifact> getArtifacts(Archive archive) {
        try {
            Manifest mf = archive.getManifest();
            String mfdeps = mf.getMainAttributes().getValue(MF_DEPENDENCIES);
            if (mfdeps == null) {
                throw new MvnLauncherException(String.format(
                        "%s undefined in MANIFEST. This is not SpringBoot MvnLauncher-enabled artifact: %s",
                        MF_DEPENDENCIES, archive));
            }
            String[] manifestDependencies = mfdeps.split(",");
            List<MvnArtifact> artifacts = toArtifacts(manifestDependencies);
            return artifacts;
        }
        catch (IOException e) {
            throw new MvnLauncherException(e, "Cannot resolve artifacts for archive " + archive);
        }
    }

    // parses Maven URIs and converts them into list of Maven artifacts
    private List<MvnArtifact> toArtifacts(String[] strings) {
        if (strings == null) {
            return Collections.emptyList();
        }
        List<MvnArtifact> result = new ArrayList<MvnArtifact>();
        for (String s : strings) {
            if (s == null || s.trim().isEmpty()) {
                continue;
            }
            result.add(new MvnArtifact(s));
        }
        return result;
    }
    
    MvnRepositoryConnector connector() {
        return context.connector;
    }

}
