package org.springframework.boot.loader;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class Log {
	
	static public void debug(String message, Object ... args) {
		if (Boolean.getBoolean("MvnLauncher.debug")) {
			synchronized (System.out) {
				System.out.printf(message, args);
				System.out.println();
			}
		}
	}
	
	static public void info(String message, Object ... args) {
		synchronized (System.out) {
			System.out.printf(message, args);
			System.out.println();
		}
	}
	
	static public void error(String message, Object ... args) {
		synchronized (System.err) {
			System.err.printf(message, args);
			System.err.println();
		}
	}

}
