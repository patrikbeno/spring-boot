package org.springframework.boot.launcher;

import org.springframework.boot.launcher.mvn.Artifact;
import org.springframework.boot.launcher.mvn.Launcher;
import org.springframework.boot.launcher.mvn.Repository;
import org.springframework.boot.launcher.util.CommandLine;
import org.springframework.boot.launcher.util.IOHelper;
import org.springframework.boot.launcher.vault.Vault;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class Tools {

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	static @interface Command {}

	Map<String, Method> getCommands() {
		Map<String, Method> commands = new HashMap<String, Method>();
		for (Method m : getClass().getDeclaredMethods()) {
			if (m.getAnnotation(Command.class) != null) { commands.put(m.getName(), m); }
		}
		return commands;
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
	void decrypt(CommandLine cmdline) throws Exception {
		cmdline.remainder().poll();
		cmdline = CommandLine.parse(cmdline.remainder());
		String key = option(cmdline, "key", true, "Key");
		String value = Vault.instance().getProperty(key);
		System.out.println(value);
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

		if (cmdline.properties().isEmpty()) {
			System.out.println("repository --id=<alias> --url=<URL> --username=<username>");
			return;
		}

		String id = option(cmdline, "id", true, "Repository alias");
		String url = option(cmdline, "url", true, "Repository URL");
		String username = option(cmdline, "username", false, "Auth: user name");
		String password = option(cmdline, "password", false, "Auth: password");

		while (username != null && password == null) {
			char[] chars = System.console().readPassword("Enter password: ");
			char[] repeat = System.console().readPassword("Repeat: ");
			if (Arrays.equals(chars, repeat)) {
				password = new String(chars);
			} else {
				System.out.println("Value mismatch! Try again!");
			}
		}

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

	///

	String option(CommandLine cmdline, String property, boolean required, String hint) {
		String value = cmdline.properties().getProperty(property);
		if (value == null && required) {
			throw new LauncherException(String.format("Required: --%s=<%s>", property, hint));
		}
		return value;
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


}
