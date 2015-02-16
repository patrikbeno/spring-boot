package org.springframework.boot.launcher.mvn;

import org.springframework.boot.launcher.MvnLauncherCfg;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static org.springframework.boot.launcher.MvnLauncherCfg.downloads;
import static org.springframework.boot.launcher.MvnLauncherCfg.resolvers;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class MvnRepositoryConnectorContext implements AutoCloseable {

    File cache = MvnLauncherCfg.cache.asFile();

    ThreadGroup group = new ThreadGroup(getClass().getSimpleName());

    ExecutorService resolvers = Executors.newFixedThreadPool(MvnLauncherCfg.resolvers.asInt(), new ThreadFactory() {
        int counter;

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(group, r, "MvnLauncher-resolver-" + (++counter));
        }
    });
    ExecutorService downloads = Executors.newFixedThreadPool(MvnLauncherCfg.downloads.asInt(), new ThreadFactory() {
        int counter;
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(group, r, "MvnLauncher-download-"+(++counter));
        }
    });

    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    XPathFactory xpf = XPathFactory.newInstance();

    /**
     * Close & release executor
     */
    public void close() {
        downloads.shutdownNow();
        downloads = null;
        resolvers.shutdownNow();
        resolvers = null;
    }

}
