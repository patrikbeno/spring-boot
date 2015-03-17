package org.springframework.boot.launcher.vault;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
@Component
public class VaultBinder implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	@Override
	public void initialize(ConfigurableApplicationContext ctx) {
		ctx.getEnvironment().getPropertySources().addLast(new EnumerablePropertySource<Vault>("$vault", Vault.instance()) {
			@Override
			public String[] getPropertyNames() {
				Set<String> keys = getSource().getPropertyNames();
				return keys.toArray(new String[keys.size()]);
			}
			@Override
			public Object getProperty(String name) {
				return getSource().containsKey(name) ? getSource().getProperty(name) : null;
			}
		});
	}
}
