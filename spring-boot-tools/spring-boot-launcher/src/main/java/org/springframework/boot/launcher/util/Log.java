package org.springframework.boot.launcher.util;

import org.springframework.boot.launcher.MvnLauncherCfg;

import java.io.PrintStream;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class Log {

    static private final String LABEL = "[boot]";
    static private final String L_ERR = "[!ERR]";
    static private final String L_WRN = "[!WRN]";

    static public boolean isDebug() {
        return MvnLauncherCfg.isDebugEnabled();
    }

	static public void debug(String message, Object... args) {
		if (isDebug()) {
            log(System.out, LABEL, message, args);
        }
	}

	static public void info(String message, Object... args) {
        if (MvnLauncherCfg.quiet.asBoolean()) { return; }
        log(System.out, LABEL, message, args);
	}

	static public void warn(String message, Object... args) {
        if (MvnLauncherCfg.quiet.asBoolean()) { return; }
        log(System.out, L_WRN, message, args);
	}

	static public void error(Throwable thrown, String message, Object... args) {
        System.out.flush();
        System.err.flush();
        log(System.err, L_ERR, message, args);
        for (Throwable t = thrown ; t != null; t = t.getCause()) {
            log(System.err, "", "Caused by: %s", t);
        }
        if (thrown != null && MvnLauncherCfg.debug.asBoolean()) {
            thrown.printStackTrace(System.err);
        }
	}

    static private void log(final PrintStream out, String prefix, String message, Object ... args) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (out) {
            StatusLine.resetLine();
            out.print(prefix);
            out.print(" ");
            out.printf(message, args);
            out.println();
            StatusLine.refresh();
        }
    }

}
