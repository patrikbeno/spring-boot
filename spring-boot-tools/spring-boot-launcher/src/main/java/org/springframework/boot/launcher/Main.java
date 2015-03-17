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

import org.springframework.boot.launcher.mvn.Artifact;
import org.springframework.boot.launcher.mvn.Launcher;
import org.springframework.boot.launcher.mvn.Repository;
import org.springframework.boot.launcher.url.UrlSupport;
import org.springframework.boot.launcher.util.CommandLine;
import org.springframework.boot.launcher.util.IOHelper;
import org.springframework.boot.launcher.util.Log;
import org.springframework.boot.launcher.vault.Vault;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
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
	
	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	static @interface Command {}

	/**
	 * Application entry point. Delegates to {@code launch()}
	 * @see #launch(String[])
	 */
	public static void main(String[] args) {
        try {
            new Main().launch(new LinkedList<String>(asList(args)));
        } catch (LauncherException e) {
            Log.error(e, "Could not launch application!");
            System.exit(-1);
        }
    }


    /**
	 * Process arguments and delegate to {@code MvnLauncher}
	 * @param args
	 * @see org.springframework.boot.launcher.mvn.Launcher
	 */
	protected void launch(Queue<String> args) throws LauncherException {

        CommandLine cmdline = CommandLine.parse(args);
        exportOptions(cmdline.properties());

        String cmd = cmdline.remainder().peek();

        if (cmd == null) {
            help(cmdline);
        }

		Map<String, Method> commands = getCommands();

        cmd = commands.containsKey(cmd) ? cmd
			: cmdline.properties().contains("help") ? "help"
			: "launch";

        try {
            commands.get(cmd).invoke(this, cmdline);
        } catch (InvocationTargetException e) {
            throw e.getTargetException() instanceof LauncherException
                    ? (LauncherException) e.getTargetException()
                    : new LauncherException(e.getTargetException());
        } catch (IllegalAccessException e) {
            throw new LauncherException(e);
        }
    }

	Map<String, Method> getCommands() {
		Map<String, Method> commands = new HashMap<String, Method>();
		for (Method m : getClass().getDeclaredMethods()) {
			if (m.getAnnotation(Command.class) != null) { commands.put(m.getName(), m); }
		}
		return commands;
	}

    void exportOptions(Properties properties) {
        Set<String> valid = LauncherCfg.names();
        Set<String> names = properties.stringPropertyNames();
        for (String name : names) {
            String fqname = (valid.contains(name)) ? LauncherCfg.valueOf(name).getPropertyName() : null;
            String value = properties.getProperty(name);
            System.getProperties().setProperty(
                    fqname != null ? fqname : name,
                    value != null && !value.isEmpty() ? value : "true");
        }
    }

    void readme() {
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

	String option(CommandLine cmdline, String property, boolean required, String hint) {
		String value = cmdline.properties().getProperty(property);
		if (value == null && required) {
			throw new LauncherException(String.format("Required: --%s=<%s>", property, hint));
		}
		return value;
	}

	@Command
    void launch(CommandLine cmdline) throws Exception {
        String command = cmdline.remainder().poll();
		if (command == null) {
			help(cmdline);
			System.exit(-1);
		} else {
			LauncherCfg.configure();
			new Launcher(new Artifact(command)).launch(cmdline.remainder());
		}
    }

    @Command
    void encrypt(CommandLine cmdline) throws IOException {
		cmdline.remainder().poll();
		cmdline = CommandLine.parse(cmdline.remainder());
		String key = option(cmdline, "key", true, "Key");
		String value = option(cmdline, "value", false, "Value");

		while (value == null) {
			char[] chars = System.console().readPassword("Enter value: ");
			char[] repeat = System.console().readPassword("Repeat: ");
			if (Arrays.equals(chars, repeat)) {
				value = new String(chars);
			} else {
				System.out.println("Value mismatch! Try again!");
			}
		}

		String encrypted = Vault.instance().encrypt(value);
		PrintWriter out = null;
		try {
			System.out.println("### Raw encrypted value:");
			System.out.println(encrypted);

			System.out.println("### Encoded in properties format:");
			Properties props = new Properties();
			props.setProperty(key, encrypted);
			out = new PrintWriter(System.out);
			props.store(out, null);
		}
		finally {
			IOHelper.close(out);
		}
	}

    @Command
    void repository(CommandLine cmdline) {
        cmdline.remainder().poll();
        cmdline = CommandLine.parse(cmdline.remainder());

        String id = option(cmdline, "id", true, "Repository alias");
        String url = option(cmdline, "url", true, "Repository URL");
        String username = option(cmdline, "username", false, "Auth: user name");
        String password = option(cmdline, "password", false, "Auth: password");

        password = Vault.instance().encrypt(password);

        Formatter f = new Formatter(System.out);
        f.format(Repository.P_URL, id).format("=%s%n", url);
        if (username != null) { f.format(Repository.P_USERNAME, id).format("=%s%n", username); }
        if (password != null) { f.format(Repository.P_PASSWORD, id).format("=%s%n", password); }
    }

    @Command
    void version(CommandLine cmdline) {
        String version = org.springframework.boot.loader.Launcher.class.getPackage().getImplementationVersion();
        System.out.printf("SpringBoot %s%n", (version != null ? version : "(unknown version)"));
    }

    @Command
    void help(CommandLine cmdline) {
        readme();
		if (cmdline.properties().contains("help")) { System.exit(-1); }
    }



}
