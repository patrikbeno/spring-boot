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

package org.springframework.boot.loader.tools;

import org.springframework.boot.loader.mvn.MvnArtifact;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Utility class that can be used to repackage an archive so that it can be executed using
 * '{@literal java -jar}'.
 *
 * @author Phillip Webb
 * @author Patrik Beno
 */
public class Repackager {

	private static final String MAIN_CLASS_ATTRIBUTE = "Main-Class";

	private static final String START_CLASS_ATTRIBUTE = "Start-Class";

	private static final String BOOT_VERSION_ATTRIBUTE = "Spring-Boot-Version";

	private static final String BOOT_DEPENDENCIES_ATTRIBUTE = "Spring-Boot-Dependencies";

	private static final byte[] ZIP_FILE_HEADER = new byte[] { 'P', 'K', 3, 4 };

	private String mainClass;

	private String launcherClass;

	private boolean backupSource = true;

	private final File source;

	private Layout layout;

	public Repackager(File source) {
		if (source == null || !source.exists() || !source.isFile()) {
			throw new IllegalArgumentException("Source must refer to an existing file");
		}
		this.source = source.getAbsoluteFile();
		this.layout = Layouts.forFile(source);
	}

	/**
	 * Sets the main class that should be run. If not specified the value from the
	 * MANIFEST will be used, or if no manifest entry is found the archive will be
	 * searched for a suitable class.
	 * @param mainClass the main class name
	 */
	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}

	public void setLauncherClass(String launcherClass) {
		this.launcherClass = launcherClass;
	}

	/**
	 * Sets if source files should be backed up when they would be overwritten.
	 * @param backupSource if source files should be backed up
	 */
	public void setBackupSource(boolean backupSource) {
		this.backupSource = backupSource;
	}

	/**
	 * Sets the layout to use for the jar. Defaults to {@link Layouts#forFile(File)}.
	 * @param layout the layout
	 */
	public void setLayout(Layout layout) {
		if (layout == null) {
			throw new IllegalArgumentException("Layout must not be null");
		}
		this.layout = layout;
	}

	/**
	 * Repackage the source file so that it can be run using '{@literal java -jar}'
	 * @param libraries the libraries required to run the archive
	 * @throws IOException
	 */
	public void repackage(Libraries libraries) throws IOException {
		repackage(this.source, libraries, Collections.<MvnArtifact>emptyList());
	}

	// legacy (compatibility)
	public void repackage(File destination, Libraries libraries) throws IOException {
		repackage(destination, libraries, Collections.<MvnArtifact>emptyList());
	}

	/**
	 * Repackage to the given destination so that it can be launched using '
	 * {@literal java -jar}'
	 * @param destination the destination file (may be the same as the source)
	 * @param libraries the libraries required to run the archive
	 * @throws IOException
	 */
	public void repackage(File destination, Libraries libraries, List<MvnArtifact> dependencies) throws IOException {
		if (destination == null || destination.isDirectory()) {
			throw new IllegalArgumentException("Invalid destination");
		}
		if (libraries == null) {
			throw new IllegalArgumentException("Libraries must not be null");
		}

		if (alreadyRepackaged()) {
			return;
		}

		destination = destination.getAbsoluteFile();
		File workingSource = this.source;
		if (this.source.equals(destination)) {
			workingSource = new File(this.source.getParentFile(), this.source.getName()
					+ ".original");
			workingSource.delete();
			renameFile(this.source, workingSource);
		}
		destination.delete();
		try {
			JarFile jarFileSource = new JarFile(workingSource);
			try {
				repackage(jarFileSource, destination, libraries, dependencies);
			}
			finally {
				jarFileSource.close();
			}
		}
		finally {
			if (!this.backupSource && !this.source.equals(workingSource)) {
				deleteFile(workingSource);
			}
		}
	}

	private boolean alreadyRepackaged() throws IOException {
		JarFile jarFile = new JarFile(this.source);
		try {
			Manifest manifest = jarFile.getManifest();
			return (manifest != null && manifest.getMainAttributes().getValue(
					BOOT_VERSION_ATTRIBUTE) != null);
		}
		finally {
			jarFile.close();
		}
	}

	private void repackage(JarFile sourceJar, File destination, Libraries libraries, List<MvnArtifact> dependencies)
			throws IOException {
		final JarWriter writer = new JarWriter(destination);
		try {
			final Set<String> seen = new HashSet<String>();
			writer.writeManifest(buildManifest(sourceJar, dependencies));
			writer.writeEntries(sourceJar);
			libraries.doWithLibraries(new LibraryCallback() {
				@Override
				public void library(Library library) throws IOException {
					File file = library.getFile();
					if (isZip(file)) {
						String destination = Repackager.this.layout
								.getLibraryDestination(library.getName(),
										library.getScope());
						if (destination != null) {
							if (!seen.add(destination + library.getName())) {
								throw new IllegalStateException("Duplicate library "
										+ library.getName());
							}
							writer.writeNestedLibrary(destination, library);
						}
					}
				}
			});

			if (this.layout.isExecutable()) {
				writer.writeLoaderClasses();
			}
		}
		finally {
			try {
				writer.close();
			}
			catch (Exception ex) {
				// Ignore
			}
		}
	}

	private boolean isZip(File file) {
		try {
			FileInputStream fileInputStream = new FileInputStream(file);
			try {
				return isZip(fileInputStream);
			}
			finally {
				fileInputStream.close();
			}
		}
		catch (IOException ex) {
			return false;
		}
	}

	private boolean isZip(InputStream inputStream) throws IOException {
		for (int i = 0; i < ZIP_FILE_HEADER.length; i++) {
			if (inputStream.read() != ZIP_FILE_HEADER[i]) {
				return false;
			}
		}
		return true;
	}

	private Manifest buildManifest(JarFile source, List<MvnArtifact> dependencies) throws IOException {
		Manifest manifest = source.getManifest();
		if (manifest == null) {
			manifest = new Manifest();
			manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
		}
		manifest = new Manifest(manifest);

		// determine start class; priority:
		// 1) plugin config
		// 2) source manifest's main class
		// 3) auto-detected main class (fallback)
		String startClass = this.mainClass;
		if (startClass == null) {
			startClass = manifest.getMainAttributes().getValue(MAIN_CLASS_ATTRIBUTE);
		}
		if (startClass == null) {
			startClass = findMainMethod(source);
		}

		String launcherClassName = null;
		if (layout.isExecutable()) {
			// priority for launcher class:
			// 1) plugin config override,
			// 2) layout default,
			// 3) start class as determined above (fallback)
			launcherClassName
					= (launcherClass != null) ? launcherClass
					: (layout.getLauncherClassName() != null) ? layout.getLauncherClassName()
					: startClass;
		}

		// executable layouts must have both launcher and start class
		// non-executable need must have a start class
		boolean isValid = layout.isExecutable()
				? (launcherClassName != null && startClass != null)
				: (startClass != null);
		if (!isValid) {
			throw new IllegalStateException("Unable to find main class");
		}

		// open issues:
		// 1) main class from source manifest is ignored for non-executable layouts (why would one use such layout
		//    with his own launcher?) Such Main-Class propagates into Start-Class
		// 2) cannot have non-executable layout without Start-Class (which might be useful in some
		//    scenarios like plugins where entry point is framework-specific but plugin classpath relies on
		//    layout-provided metadata like MvnLauncher's Spring-Boot-Dependencies attribute)

		if (launcherClassName != null) {
			manifest.getMainAttributes().putValue(MAIN_CLASS_ATTRIBUTE, launcherClassName);
		}
		if (startClass != null) {
			manifest.getMainAttributes().putValue(START_CLASS_ATTRIBUTE, startClass);
		}
		String bootVersion = getClass().getPackage().getImplementationVersion();
		manifest.getMainAttributes().putValue(BOOT_VERSION_ATTRIBUTE, bootVersion);

		populateDependencies(manifest, dependencies);

		return manifest;
	}

	protected void populateDependencies(Manifest manifest, List<MvnArtifact> mvnuris) {
		final StringBuilder deps = new StringBuilder();
		for (MvnArtifact mvnuri : mvnuris) {
			if (deps.length() > 0) { deps.append(","); }
			deps.append(mvnuri.asString());
		}
		manifest.getMainAttributes().putValue(BOOT_DEPENDENCIES_ATTRIBUTE, deps.toString());
	}

	protected String findMainMethod(JarFile source) throws IOException {
		return MainClassFinder.findSingleMainClass(source,
				this.layout.getClassesLocation());
	}

	private void renameFile(File file, File dest) {
		if (!file.renameTo(dest)) {
			throw new IllegalStateException("Unable to rename '" + file + "' to '" + dest
					+ "'");
		}
	}

	private void deleteFile(File file) {
		if (!file.delete()) {
			throw new IllegalStateException("Unable to delete '" + file + "'");
		}
	}

}
