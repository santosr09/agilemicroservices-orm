package org.agilemicroservices.autoconfigure.orm;

import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.config.RepositoryConfigurationSourceSupport;
import org.springframework.data.repository.query.QueryLookupStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class PropertiesRepositoryConfigurationSource extends RepositoryConfigurationSourceSupport {
    public static final String DRIVER_CLASS_NAME = "datasource.driver_class";
    public static final String URL = "datasource.url";
    public static final String USERNAME = "datasource.username";
    public static final String PASSWORD = "datasource.password";
    public static final String BASE_PACKAGES = "repository.base_packages";

    private Map<String, String> properties;


    public PropertiesRepositoryConfigurationSource(Map<String, String> properties, Environment environment) {
        super(environment);
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
    public Iterable<String> getBasePackages() {
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
        return basePackages;
    }

    @Override
    public Object getQueryLookupStrategyKey() {
        return QueryLookupStrategy.Key.CREATE_IF_NOT_FOUND;
    }

    @Override
    public String getRepositoryImplementationPostfix() {
        return "Impl";
    }

    @Override
    public String getNamedQueryLocation() {
        return "";
    }

    @Override
    public String getRepositoryFactoryBeanName() {
        return JpaRepositoryFactoryBean.class.getName();
    }

    @Override
    public String getRepositoryBaseClassName() {
        return null;
    }

    @Override
    public String getAttribute(String name) {
        return properties.get(name);
    }

    @Override
    public boolean usesExplicitFilters() {
        return false;
    }
}