package org.springframework.boot.loader.util;

import org.springframework.boot.loader.mvn.MvnLauncherCfg;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class Log {

	static public void debug(String message, Object... args) {
		if (MvnLauncherCfg.debug.asBoolean()) {
            log("", message, args);
        }
	}

	static public void info(String message, Object... args) {
        if (MvnLauncherCfg.quiet.asBoolean()) { return; }
        log("", message, args);
	}

	static public void warn(String message, Object... args) {
        if (MvnLauncherCfg.quiet.asBoolean()) { return; }
        log("[WRN] ", message, args);
	}

	static public void error(String message, Object... args) {
        log("[ERR] ", message, args);
	}

    static private void log(String prefix, String message, Object ... args) {
        synchronized (System.out) {
            StatusLine.resetLine();
            System.out.print(prefix);
            System.out.printf(message, args);
            System.out.println();
            StatusLine.refresh();
        }
    }

}
