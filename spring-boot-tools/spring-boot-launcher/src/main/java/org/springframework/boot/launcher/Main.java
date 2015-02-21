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

import org.springframework.boot.launcher.mvn.MvnArtifact;
import org.springframework.boot.launcher.mvn.MvnLauncher;
import org.springframework.boot.launcher.util.CommandLine;
import org.springframework.boot.loader.util.UrlSupport;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;

import static java.util.Arrays.asList;

/**
 * Start class for MvnLauncher; {@code spring-boot-loader} is executable.
 * @author Patrik Beno
 */
public class Main {

    static {
        UrlSupport.init();
    }

	/**
	 * Application entry point. Delegates to {@code launch()}
	 * @see #launch(String[])
	 */
	public static void main(String[] args) {
        new Main().launch(new LinkedList<String>(asList(args)));
	}

    protected Main() {
        exportRepositoryDefaults();
    }

    /**
	 * Process arguments and delegate to {@code MvnLauncher}
	 * @param args
	 * @see org.springframework.boot.launcher.mvn.MvnLauncher
	 */
	protected void launch(Queue<String> args) {

        CommandLine cmdline = CommandLine.parse(args);
        exportOptions(cmdline.properties());

        MvnLauncherCfg.configure();

        String command = cmdline.remainder().poll();

        if (command == null) {
            readme();
            System.exit(-1);
        }

        cmdline = CommandLine.parse(cmdline.remainder());

        new MvnLauncher(new MvnArtifact(command)).launch(cmdline.remainder());
	}

    void exportOptions(Properties properties) {
        Set<String> valid = MvnLauncherCfg.names();
        Set<String> names = properties.stringPropertyNames();
        for (String name : names) {
            String fqname = (valid.contains(name)) ? MvnLauncherCfg.valueOf(name).getPropertyName() : null;
            String value = properties.getProperty(name);
            System.getProperties().setProperty(
                    fqname != null ? fqname : name,
                    value != null && !value.isEmpty() ? value : "true");
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

}
