package org.springframework.boot.launcher.util;

import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLConnection;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class IOHelper {

    static public void close(Closeable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (IOException ignore) {}
        }
    }

    static public void close(AutoCloseable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (Exception ignore) {}
        }
    }

    static public void close(URLConnection con) {
        if (con != null && con instanceof HttpURLConnection) {
            ((HttpURLConnection) con).disconnect();
        }
    }

}
