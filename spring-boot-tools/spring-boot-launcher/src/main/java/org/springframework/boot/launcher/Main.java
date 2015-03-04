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

import org.springframework.boot.launcher.mvn.Decryptable;
import org.springframework.boot.launcher.mvn.MvnArtifact;
import org.springframework.boot.launcher.mvn.MvnLauncher;
import org.springframework.boot.launcher.mvn.MvnRepository;
import org.springframework.boot.launcher.util.CommandLine;
import org.springframework.boot.launcher.util.Log;
import org.springframework.boot.launcher.vault.Vault;
import org.springframework.boot.loader.Launcher;
import org.springframework.boot.loader.util.UrlSupport;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
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
        try {
            new Main().launch(new LinkedList<String>(asList(args)));
        } catch (MvnLauncherException e) {
            Log.error(e, "Could not launch application!");
            System.exit(-1);
        }
    }


    /**
	 * Process arguments and delegate to {@code MvnLauncher}
	 * @param args
	 * @see org.springframework.boot.launcher.mvn.MvnLauncher
	 */
	protected void launch(Queue<String> args) throws MvnLauncherException {

        CommandLine cmdline = CommandLine.parse(args);
        exportOptions(cmdline.properties());

        MvnLauncherCfg.configure();

        String cmd = cmdline.remainder().peek();

        if (cmd == null) {
            help(cmdline);
        }

        Map<String, Method> commands = new HashMap<String, Method>();
        register("launch", commands);
        register("encrypt", commands);
        register("repository", commands);
        register("help", commands);
        register("version", commands);

        cmd = commands.containsKey(cmd) ? cmd : "launch";

        try {
            commands.get(cmd).invoke(this, cmdline);
        } catch (InvocationTargetException e) {
            throw e.getTargetException() instanceof MvnLauncherException
                    ? (MvnLauncherException) e.getTargetException()
                    : new MvnLauncherException(e.getTargetException());
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    void register(String name, Map<String, Method> index) {
        try {
            Method m = getClass().getDeclaredMethod(name, CommandLine.class);
            index.put(m.getName(), m);
        } catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException(e);
        }
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

    /// @CommandHandler
    void launch(CommandLine cmdline) throws Exception {
        String command = cmdline.remainder().poll();
        new MvnLauncher(new MvnArtifact(command)).launch(cmdline.remainder());
    }

    /// @CommandHandler
    void encrypt(CommandLine cmdline) {
        cmdline.remainder().poll();
        String value = cmdline.remainder().peek();
        String encrypted = Vault.instance().encrypt(value);
        System.out.println(encrypted);
    }

    /// @CommandHandler
    void repository(CommandLine cmdline) {
        cmdline.remainder().poll();
        cmdline = CommandLine.parse(cmdline.remainder());

        String id = option(cmdline, "id", true, "Repository alias");
        String url = option(cmdline, "url", true, "Repository URL");
        String username = option(cmdline, "username", false, "Auth: user name");
        String password = option(cmdline, "password", false, "Auth: password");

        password = Vault.instance().encrypt(password);

        Formatter f = new Formatter(System.out);
        f.format(MvnRepository.P_URL, id).format("=%s%n", url);
        if (username != null) { f.format(MvnRepository.P_USERNAME, id).format("=%s%n", username); }
        if (password != null) { f.format(MvnRepository.P_PASSWORD, id).format("=%s%n", password); }
    }

    /// @CommandHandler
    void version(CommandLine cmdline) {
        String version = Launcher.class.getPackage().getImplementationVersion();
        System.out.printf("SpringBoot %s", (version != null ? version : "(unknown version)"));
    }

    /// @CommandHandler
    void help(CommandLine cmdline) {
        readme();
        System.exit(-1);
    }

    String option(CommandLine cmdline, String property, boolean required, String hint) {
        String value = cmdline.properties().getProperty(property);
        if (value == null && required) {
            throw new MvnLauncherException(String.format("Required: --%s=<%s>", property, hint));
        }
        return value;
    }


}
