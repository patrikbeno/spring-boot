package org.springframework.boot.loader.util;

import java.net.URL;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class UrlSupport {

    private static final String PROTOCOL_HANDLER = "java.protocol.handler.pkgs";
    private static final String HANDLERS_PACKAGE = "org.springframework.boot.loader";

    static {
        registerUrlProtocolHandlers();
    }

    static public void init() {}

    /**
     * Register a {@literal 'java.protocol.handler.pkgs'} property so that a
     * {@link java.net.URLStreamHandler} will be located to deal with jar URLs.
     */
    private static void registerUrlProtocolHandlers() {
        String handlers = System.getProperty(PROTOCOL_HANDLER);
        System.setProperty(
                PROTOCOL_HANDLER,
                ("".equals(handlers) ? HANDLERS_PACKAGE : handlers + "|" + HANDLERS_PACKAGE));
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
