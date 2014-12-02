package org.springframework.boot.launcher;

import org.springframework.boot.loader.util.UrlSupport;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public abstract class AbstractTest {
	static {
		UrlSupport.init();
		System.setProperty("MvnLauncher.defaults", "classpath:MvnLauncherCfg.properties");
		MvnLauncherCfg.configure();
	}
}
