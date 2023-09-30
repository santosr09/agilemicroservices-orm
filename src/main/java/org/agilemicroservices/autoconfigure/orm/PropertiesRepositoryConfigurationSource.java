package org.agilemicroservices.autoconfigure.orm;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.repository.config.RepositoryConfigurationSourceSupport;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.util.Streamable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class PropertiesRepositoryConfigurationSource extends RepositoryConfigurationSourceSupport {
    public static final String DRIVER_CLASS_NAME = "datasource.driver_class";
    public static final String URL = "datasource.url";
    public static final String USERNAME = "datasource.username";
    public static final String PASSWORD = "datasource.password";
    public static final String BASE_PACKAGES = "repository.base_packages";

    private Map<String, String> properties;


    public PropertiesRepositoryConfigurationSource(Map<String, String> properties, Environment environment, ResourceLoader resourceLoader, BeanDefinitionRegistry registry) {
        super(environment, resourceLoader.getClassLoader(), registry);
        this.properties = properties;
    }

    @Override
    public Object getSource() {
        return properties;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public Streamable<String> getBasePackages() {
        List<String> basePackages = new ArrayList<>(10);
        String str = properties.get(BASE_PACKAGES);
        if (str != null) {
            for (String o : str.trim().split(",")) {
                String packageName = o.trim();
                if (!packageName.isEmpty()) {
                    basePackages.add(o);
                }
            }
        }
        return (Streamable<String>) basePackages;
    }

    @Override
    public Optional<Object> getQueryLookupStrategyKey() {
        return Optional.of(QueryLookupStrategy.Key.CREATE_IF_NOT_FOUND);
    }

    @Override
    public Optional<String> getRepositoryImplementationPostfix() {
        return Optional.of("Impl");
    }

    @Override
    public Optional<String> getNamedQueryLocation() {
        return Optional.of("");
    }


    @Override
    public Optional<String> getRepositoryBaseClassName() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getRepositoryFactoryBeanClassName() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getAttribute(String name) {
        return Optional.ofNullable(properties.get(name));
    }

    @Override
    public boolean usesExplicitFilters() {
        return false;
    }
}
