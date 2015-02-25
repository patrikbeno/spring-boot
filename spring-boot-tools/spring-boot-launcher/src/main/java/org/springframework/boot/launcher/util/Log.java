package org.springframework.boot.launcher.util;

import org.springframework.boot.launcher.MvnLauncherCfg;

import java.io.PrintStream;

import static org.springframework.boot.launcher.util.Log.Level.*;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class Log {

    static public enum Level {
        DBG(""), INF("\033[1m"), WRN("\033[33m"), ERR("\033[;31m");

        String ansi;

        Level(String ansi) {
            this.ansi = ansi;
        }
    }

    static public boolean isDebug() {
        return MvnLauncherCfg.isDebugEnabled();
    }

	static public synchronized void log(Level level, String message, Object... args) {
        switch (level) {
            case DBG:
                if (!isDebug()) break;
            default:
                log(out(), level, message, args);
        }
	}

	static public synchronized void debug(String message, Object... args) {
		if (isDebug()) {
            log(out(), DBG, message, args);
        }
	}

	static public synchronized void info(String message, Object... args) {
        if (MvnLauncherCfg.quiet.asBoolean()) { return; }
        log(out(), INF, message, args);
	}

	static public synchronized void warn(String message, Object... args) {
        if (MvnLauncherCfg.quiet.asBoolean()) { return; }
        log(out(), WRN, message, args);
	}

	static public synchronized void error(Throwable thrown, String message, Object... args) {
        out().flush();
        err().flush();
        log(err(), ERR, message, args);
        for (Throwable t = thrown ; t != null; t = t.getCause()) {
            log(err(), ERR, "- Caused by: %s", t.getMessage());
        }
        if (thrown != null && MvnLauncherCfg.debug.asBoolean()) {
            thrown.printStackTrace(err());
        }
    }

    static private synchronized void log(final PrintStream out, Level level, String message, Object ... args) {
        StatusLine.resetLine();
        out.print(level.ansi);
        out.printf("[%s] ", level);
        out.printf(message, args);
        out.print("\033[0m");
        out.println();
        StatusLine.refresh();
    }

    ///

    static PrintStream out() {
        return System.out;
    }

    static PrintStream err() {
        return System.err;
    }


}
