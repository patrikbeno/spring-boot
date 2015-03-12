package org.springframework.boot.launcher.mvn;

import org.springframework.boot.launcher.MvnLauncherCfg;
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

    File cache = MvnLauncherCfg.cache.asFile();

    MvnArtifact main;

    List<MvnArtifact> artifacts = new LinkedList<MvnArtifact>();

    MvnRepositoryConnector connector;

    ThreadGroup group = new ThreadGroup(getClass().getSimpleName());

    ExecutorService resolvers = Executors.newFixedThreadPool(MvnLauncherCfg.resolvers.asInt(), new ThreadFactory() {
        int counter;
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(group, r, "MvnLauncher:Resolver#" + (++counter));
        }
    });
    ExecutorService downloaders = Executors.newFixedThreadPool(MvnLauncherCfg.downloaders.asInt(), new ThreadFactory() {
        int counter;
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(group, r, "MvnLauncher:Downloader#" + (++counter));
        }
    });

    ScheduledExecutorService progress = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(group, r, "MvnLauncher:ProgressMonitor");
        }
    });

    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    XPathFactory xpf = XPathFactory.newInstance();

    public ResolverContext(MvnArtifact main) {
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

    MvnRepositoryConnector buildMvnRepositoryConnector() {
        List<String> ids = MvnLauncherCfg.repositories.asList();
        Collections.reverse(ids);
        MvnRepositoryConnector connector = null;
        for (String id : ids) {
            MvnRepository repo = MvnRepository.forRepositoryId(id);
            connector = new MvnRepositoryConnector(repo, this, connector);
        }
        Log.debug("Using repositories:");
        for (MvnRepositoryConnector c = connector; c != null; c = c.parent) {
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
                    for (MvnArtifact ma : artifacts) {
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
                            MvnLauncherCfg.debug.asBoolean() ? "" : "\033[0m(Use --debug to see more)");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        };
    }

	boolean isDownloadAllowed(MvnArtifact artifact) {
		return !MvnLauncherCfg.skipDownload.asBoolean() || artifact.equals(this.main);
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
