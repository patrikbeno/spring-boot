package org.springframework.boot.loader.util;

import org.springframework.boot.loader.mvn.MvnLauncherCfg;

import java.io.PrintStream;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class Log {

	static public void debug(String message, Object... args) {
		if (MvnLauncherCfg.debug.asBoolean()) {
            log(System.out, "", message, args);
        }
	}

	static public void info(String message, Object... args) {
        if (MvnLauncherCfg.quiet.asBoolean()) { return; }
        log(System.out, "", message, args);
	}

	static public void warn(String message, Object... args) {
        if (MvnLauncherCfg.quiet.asBoolean()) { return; }
        log(System.out, "[WRN] ", message, args);
	}

	static public void error(Throwable thrown, String message, Object... args) {
        System.out.flush();
        System.err.flush();
        log(System.err, "[ERR] ", message, args);
        for (Throwable t = thrown ; t != null; t = t.getCause()) {
            log(System.err, "", "Caused by: %s", t);
        }
        if (MvnLauncherCfg.debug.asBoolean()) {
            thrown.printStackTrace(System.err);
        }
	}

    static private void log(final PrintStream out, String prefix, String message, Object ... args) {
        synchronized (out) {
            StatusLine.resetLine();
            out.print(prefix);
            out.printf(message, args);
            out.println();
            StatusLine.refresh();
        }
    }

}
