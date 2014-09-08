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
package org.springframework.boot.loader;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Start class for MvnLauncher; {@code spring-boot-loader} is executable.
 * @author Patrik Beno
 */
public class Main {

	/**
	 * Entry point. To override, just extend this class and define custom {@code main()}
	 * implementation delegating to {@code new MyMain().launch(args);}
	 */
	public static void main(String[] args) {
		new Main().launch(args);
	}

	/**
	 * Process arguments and delegate to {@code MvnLauncher}
	 * @param args
	 * @see MvnLauncher
	 */
	protected void launch(String[] args) {
		args = processArguments(args);
		new MvnLauncher().launch(args);
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
		Pattern pattern = Pattern
				.compile("(?:-D|--)(MvnLauncher\\.\\p{javaJavaIdentifierPart}+)=(.*)");
		List<String> arglist = new LinkedList<String>();

		boolean scan = true;
		String artifact = null;

		// {launcher options} {artifact} {options}
		for (String arg : args) {
			// this means abort special argument processing and use the remaining args `as
			// is`.
			if (scan && arg.equals("--")) {
				scan = false;
				continue;
			}
			Matcher m = pattern.matcher(arg);
			if (scan && m.matches()) {
				// if scan mode is on and the argument is a MvnLauncher option, propagate
				// it to system properties
				System.setProperty(m.group(1), m.group(2));

			}
			else if (scan && artifact == null) {
				// if scan is enabled and current option is not a MvnLauncher option,
				// assume it's an artifact URI
				artifact = arg;

			}
			else {
				// otherwise, just copy the argument as is.
				arglist.add(arg);
			}
		}

		// artifact is mandatory
		if (artifact == null) {
			System.err
					.printf("Usage: %s <groupId>:<artifactId>:<version>[:<packaging>[:<classifier>]] ...%n",
                            getClass().getName());
			System.exit(-1);
		}

		// and save it in MvnLauncher.artifact property
		// don't use MvnLauncherCfg.artifact enum reference here, we don't want to trigger
		// the configuration sequence yet
		System.setProperty("MvnLauncher.artifact", artifact);

		return arglist.toArray(new String[arglist.size()]);
	}

}
