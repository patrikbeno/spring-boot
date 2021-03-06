package org.springframework.boot.launcher.mvn;

import org.springframework.boot.launcher.LauncherCfg;
import org.springframework.boot.launcher.LauncherException;
import org.springframework.boot.launcher.util.Log;
import org.springframework.boot.launcher.util.StatusLine;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class ResolverContext implements AutoCloseable {

    long created = System.currentTimeMillis();

    File cache = LauncherCfg.cache.asFile();

    Artifact main;

    List<Artifact> artifacts = new LinkedList<Artifact>();

    RepositoryConnector connector;

    ThreadGroup group = new ThreadGroup(getClass().getSimpleName());

    ExecutorService resolvers = Executors.newFixedThreadPool(LauncherCfg.resolvers.asInt(), new ThreadFactory() {
        int counter;
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(group, r, "SpringBoot:Launcher:Resolver#" + (++counter));
        }
    });
    ExecutorService downloaders = Executors.newFixedThreadPool(LauncherCfg.downloaders.asInt(), new ThreadFactory() {
        int counter;
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(group, r, "SpringBoot:Launcher:Downloader#" + (++counter));
        }
    });

    ScheduledExecutorService progress = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(group, r, "SpringBoot:Launcher:ProgressMonitor");
        }
    });

    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    XPathFactory xpf = XPathFactory.newInstance();

    public ResolverContext(Artifact main) {
        this.main = main;
        this.connector = buildMvnRepositoryConnector();

    }

    public void startProgressMonitor() {
        StatusLine.push("Resolving dependencies");
        progress.scheduleAtFixedRate(createProgressMonitor(), 0, 500, TimeUnit.MILLISECONDS);
    }

    public void stopProgressMonitor() {
        progress.shutdownNow();
        StatusLine.pop();
    }

    RepositoryConnector buildMvnRepositoryConnector() {
        List<String> ids = LauncherCfg.repositories.asList();
        Collections.reverse(ids);
        RepositoryConnector connector = null;
        for (String id : ids) {
            Repository repo = Repository.forRepositoryId(id);
            if (repo == null) {
                Log.error(null, "Cannot resolve repository: `%s`. Ignoring.", id);
                continue;
            }
            connector = new RepositoryConnector(repo, this, connector);
        }
        if (connector == null) {
            throw new LauncherException("No valid repositories configured");
        }
        Log.debug("Using repositories:");
        for (RepositoryConnector c = connector; c != null; c = c.parent) {
            String credentials = c.repository.hasPassword() ? c.repository.getUserName() : "<anonymous>";
            Log.debug("- %12s : %s (%s)", c.repository.getId(), c.repository.getURL(), credentials);
        }
        return connector;
    }

    private Runnable createProgressMonitor() {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    int completed = 0;
                    long downloaded = 0;
                    long size = 0;
                    for (Artifact ma : artifacts) {
                        switch (ma.getStatus()) {
                            case Resolving:
                                break;
                            case Resolved:
                            case Downloadable:
                            case Downloading:
                                downloaded += ma.getFile().length();
                                size += ma.size;
                                break;
                            default:
                                downloaded += ma.downloaded;
                                size += ma.size;
                                completed++;
                        }
                    }
                    StatusLine.update(
                            "Resolving dependencies %d/%d (%d KB / %d%%) %s",
                            completed, artifacts.size(), downloaded / 1024, size > 0 ? downloaded * 100 / size : 100,
                            LauncherCfg.debug.asBoolean() ? "" : "\033[0m(Use --debug to see more)");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        };
    }

	boolean isDownloadAllowed(Artifact artifact) {
		return !LauncherCfg.skipDownload.asBoolean() || artifact.equals(this.main);
	}

    /**
     * Close & release executor
     */
    public void close() {
        resolvers.shutdownNow();
        downloaders.shutdownNow();
        progress.shutdownNow();
    }

}
