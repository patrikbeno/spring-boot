/*
 * Copyright 2012-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.loader.classpath;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link java.net.URLStreamHandler} for Spring Boot loader {@link org.springframework.boot.loader.jar.JarFile}s.
 *
 * @author Patrik Beno
 * @see org.springframework.boot.loader.util.UrlSupport#registerUrlProtocolHandlers()
 */
public class Handler extends URLStreamHandler {

    static private Pattern PROTO = Pattern.compile("classpath:/*");
    static private Pattern PATTERN = Pattern.compile("(.*)!/(.*)");

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        String sbase = PROTO.matcher(u.toExternalForm()).replaceFirst("");
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
