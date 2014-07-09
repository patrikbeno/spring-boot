package org.springframework.boot.loader.classpath;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class Handler extends URLStreamHandler {
	@Override
	protected URLConnection openConnection(URL u) throws IOException {
		String sbase = u.toExternalForm().replaceFirst("classpath:/*", "");
		String subpath = null;
		if (sbase.contains("!/")) {
			subpath = sbase.replaceFirst(".*!/", "!/");
			sbase = sbase.replaceFirst("!/.*", "");
		}
		URL result = Thread.currentThread().getContextClassLoader().getResource(sbase);
		if (subpath != null && !result.getProtocol().equals("jar")) {
			result = new URL("jar:"+result.toExternalForm()+subpath);
		}
		return result.openConnection();

	}
}
