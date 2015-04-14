package org.springframework.boot.launcher.url;

import java.net.URL;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class UrlSupport {

	private static final String PROTOCOL_HANDLER = "java.protocol.handler.pkgs";
	private static final String[] PACKAGES = {
			"org.springframework.boot.loader",
			"org.springframework.boot.launcher.url",
	};

	static {
		registerUrlProtocolHandlers();
	}

	static public void init() {} // make sure static{} is called

	/**
	 * Register a {@literal 'java.protocol.handler.pkgs'} property so that a
	 * {@link java.net.URLStreamHandler} will be located to deal with jar URLs.
	 */
	private static void registerUrlProtocolHandlers() {
		StringBuilder sb = new StringBuilder(System.getProperty(PROTOCOL_HANDLER, ""));
		for (String s : PACKAGES) {
			if (sb.length() > 0) { sb.append('|'); }
			sb.append(s);
		}
		System.setProperty(PROTOCOL_HANDLER, sb.toString());
		resetCachedUrlHandlers();
	}

	/**
	 * Reset any cached handers just in case a jar protocol has already been used. We
	 * reset the handler by trying to set a null {@link java.net.URLStreamHandlerFactory} which
	 * should have no effect other than clearing the handlers cache.
	 */
	private static void resetCachedUrlHandlers() {
		try {
			URL.setURLStreamHandlerFactory(null);
		} catch (Error ignore) {}
	}
}
