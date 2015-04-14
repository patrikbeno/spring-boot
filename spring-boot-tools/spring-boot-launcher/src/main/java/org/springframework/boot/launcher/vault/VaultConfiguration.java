package org.springframework.boot.launcher.vault;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
@Configuration
public class VaultConfiguration {

	static public final String PROPERTY_SOURCE_NAME = "$vault";

	@Bean
	BeanFactoryPostProcessor beanFactoryPostProcessor(ConfigurableApplicationContext ctx) {
		return new VaultReorderer(ctx);
	}

	static public StandardEnvironment initStandardEnvironment() {
		return initEnvironment(new StandardEnvironment());
	}

	static public <T extends AbstractEnvironment> T initEnvironment(T env) {
		try {
			Field f = AbstractEnvironment.class.getDeclaredField("propertyResolver");
			f.setAccessible(true);
			f.set(env, new Resolver(env.getPropertySources()));
			env.getPropertySources().addLast(new VaultPropertySource());
			return env;
		}
		catch (Exception e) {
			throw new AssertionError("Cannot override environment's property resolver", e);
		}
	}

	static class VaultReorderer implements BeanFactoryPostProcessor, Ordered {

		ConfigurableApplicationContext ctx;

		public VaultReorderer(ConfigurableApplicationContext ctx) {
			this.ctx = ctx;
		}

		@Override public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) throws BeansException {
			MutablePropertySources sources = ctx.getEnvironment().getPropertySources();
			sources.addLast(sources.remove(PROPERTY_SOURCE_NAME));
		}

		@Override public int getOrder() {
			return Ordered.LOWEST_PRECEDENCE;
		}
	}

	static class VaultPropertySource extends EnumerablePropertySource<Vault> {
		public VaultPropertySource() {
			super(PROPERTY_SOURCE_NAME, Vault.instance());
		}

		@Override public String[] getPropertyNames() {
			Set<String> keys = getSource().getPropertyNames();
			return keys.toArray(new String[keys.size()]);
		}

		@Override public Object getProperty(String name) {
			return getSource().containsKey(name) ? getSource().getProperty(name) : null;
		}
	}

	static class Resolver extends PropertySourcesPropertyResolver {
		public Resolver(PropertySources propertySources) {
			super(propertySources);
		}

		@Override protected String resolveNestedPlaceholders(String value) {
			return super.resolveNestedPlaceholders(value);
		}

		@Override protected String getPropertyAsRawString(String key) {
			String s;
			if (key.matches("encrypted:[0-9a-f]+")) {
				s = Vault.instance().resolve("${" + key + "}");
			}
			else {
				s = super.getPropertyAsRawString(key);
			}
			return s;
		}
	}

}
