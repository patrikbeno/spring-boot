package org.springframework.boot.loader.classpath;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * classpath:// URL handler
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class Handler extends URLStreamHandler {

	static private Pattern PATTERN = Pattern.compile("(.*)!/(.*)");

	/**
	 *
	 * @param u
	 * @return
	 * @throws IOException
	 */
	@Override
	protected URLConnection openConnection(URL u) throws IOException {
		String sbase = u.toExternalForm().replaceFirst("classpath:/*", "");
		String subpath = null;
		Matcher m = PATTERN.matcher(sbase);
		if (m.matches()) {
			sbase = m.group(1);
			subpath = m.group(2);
		}
		URL result = Thread.currentThread().getContextClassLoader().getResource(sbase);
		if (subpath != null && result != null && !result.getProtocol().equals("jar")) {
			result = new URL("jar:" + result.toExternalForm() + "!/" + subpath);
		}
		if (result == null) {
			throw new FileNotFoundException(u.toExternalForm());
		}
		return result.openConnection();

	}
}
