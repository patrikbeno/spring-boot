
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

import org.springframework.boot.launcher.url.UrlSupport;
import org.springframework.boot.launcher.util.CommandLine;
import org.springframework.boot.launcher.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
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
	 * @see #launch(Queue)
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

        Tools tools = new Tools();
        Map<String, Method> commands = tools.getCommands();

        cmd = cmd == null ? "help"
            : commands.containsKey(cmd) ? cmd
			: cmdline.properties().contains("help") ? "help"
			: "launch";

        try {
            commands.get(cmd).invoke(tools, cmdline);
        } catch (InvocationTargetException e) {
            throw e.getTargetException() instanceof LauncherException
                    ? (LauncherException) e.getTargetException()
                    : new LauncherException(e.getTargetException());
        } catch (IllegalAccessException e) {
            throw new LauncherException(e);
        }
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

}
