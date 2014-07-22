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

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.mvn.MvnLauncherBase;

import java.util.List;

/**
 * Specialized implementation of the {@code Launcher} that intelligently downloads dependencies from configured
 * Maven repository.
 *
 * @see org.springframework.boot.loader.mvn.MvnLauncherBase
 * @see org.springframework.boot.loader.ExecutableArchiveLauncher
 *
 * @author Patrik Beno
 */
public class MvnLauncher extends MvnLauncherBase /*ExecutableArchiveLauncher*/ {

	@Override // overridden for documentation purposes only
	protected String getMainClass() throws Exception {
		return super.getMainClass();
	}

	@Override // overridden for documentation purposes only
	protected List<Archive> getClassPathArchives() throws Exception {
		return super.getClassPathArchives();
	}

	/**
	 * Always returns false: there are no nested archives.
	 */
	@Override
	protected boolean isNestedArchive(Archive.Entry entry) {
		return false; // unsupported / irrelevant
	}

	/**
	 * Entry point used by build plugin (Main-Class manifest attribute). This can be overriden in build by specifying
	 * {@code launcherClass} parameter.
	 * Also, you may want to skip this and use the generic launcher directly.
	 * @param args
	 * @see org.springframework.boot.loader.Main
	 */
	static public void main(String[] args) {
		new MvnLauncher().launch(args);
	}
}
