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
package org.springframework.boot.launcher;

import org.springframework.boot.launcher.mvn.MvnLauncher;
import org.springframework.boot.loader.util.UrlSupport;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Start class for MvnLauncher; {@code spring-boot-loader} is executable.
 * @author Patrik Beno
 */
public class Main {

	/**
	 * Application entry point. Delegates to {@code launch()}
	 * @see #launch(String[])
	 */
	public static void main(String[] args) {
		new Main().launch(args);
	}

	/**
	 * Process arguments and delegate to {@code MvnLauncher}
	 * @param args
	 * @see org.springframework.boot.launcher.mvn.MvnLauncher
	 */
	protected void launch(String[] args) {

        UrlSupport.init();

        args = processArguments(args);

        exportRepositoryDefaults();

        MvnLauncherCfg.configure();

        if (!MvnLauncherCfg.artifact.isDefined()) {
            readme();
            System.exit(-1);
        }

        new MvnLauncher(MvnLauncherCfg.artifact.asMvnArtifact()).launch(args);
	}

    /**
	 * Go through all arguments and provide special handling for {@code MvnLauncher}
	 * configuration properties: {@code --DMvnLauncher.NAME=VALUE} or
	 * {@code --MvnLauncher.NAME=VALUE}. Such properties are propagated to system
	 * properties and removed from command line arguments. To skip/abort this special
	 * processing, use standard double dash {@code --} terminator. Terminator is removed
	 * from command line arguments as well, and remainder of the arguments is copied as
	 * is.
	 *
	 * @param main
	 * @param args
	 * @return
	 */
	protected String[] processArguments(String[] args) {

        Pattern pattern = Pattern.compile("(?:-D|--)((?:MvnLauncher\\.)?\\p{javaJavaIdentifierPart}+)=(.*)");

		List<String> arglist = new LinkedList<String>();

		boolean scan = true;
		String artifact = null;

		// [launcher-options] [artifact] [launcher-options | application-options]
		for (String arg : args) {

			if (scan && arg.equals("--")) {
                // double-dash aborts argument parsing
                scan = false;
				continue;
			}

            if (!scan) {
                // argument parsing has been disabled, copy arg as-is
                arglist.add(arg);
                continue;
            }

            boolean isArtifactDefined = (artifact != null);
			Matcher m = pattern.matcher(arg);
            boolean isOption = m.matches();

            if (!isOption && !isArtifactDefined) {
                // current option is not a MvnLauncher option, and artifact URI has not yet been defined
                // assume this argument is an artifact URI
                artifact = arg;
                System.setProperty(MvnLauncherCfg.artifact.getPropertyName(), artifact);
                continue;
            }

            String name = isOption ? m.group(1) : null;
            String value = isOption ? m.group(2) : null;

            if (isOption && !isArtifactDefined && !name.startsWith("MvnLauncher.")) {
                // expand short option
                name = "MvnLauncher."+name;
            }

			if (isOption) {
				// propagate option to system properties
				System.setProperty(name, value);
			}
			else {
				// otherwise, just copy the argument as is.
				arglist.add(arg);
			}
		}

		return arglist.toArray(new String[arglist.size()]);
	}

    protected void exportRepositoryDefaults() {
        InputStream in = null;
        try {
            Properties system = System.getProperties();
            in = getClass().getResourceAsStream("repo-defaults.properties");
            Properties defaults = new Properties();
            defaults.load(in);
            Enumeration<?> names = defaults.propertyNames();
            while (names.hasMoreElements()) {
                String name = (String) names.nextElement();
                if (!system.containsKey(name)) {
                    system.setProperty(name, defaults.getProperty(name));
                }
            }
        } catch (IOException ignore) {
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignore) {}
        }
    }

    private void readme() {
        InputStream in = null;
        try {
            in = getClass().getResourceAsStream("README.txt");
            Scanner scanner = new Scanner(in);
            scanner.useDelimiter("\\Z");
            String s = scanner.next();
            System.out.println(s);
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignore) {}
        }
    }
}
