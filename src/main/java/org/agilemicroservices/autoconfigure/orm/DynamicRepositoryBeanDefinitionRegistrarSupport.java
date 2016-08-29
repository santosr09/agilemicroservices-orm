package org.agilemicroservices.autoconfigure.orm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.jpa.repository.config.JpaRepositoryConfigExtension;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;
import org.springframework.data.repository.config.RepositoryConfigurationUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.io.IOException;
import java.util.*;

import static org.agilemicroservices.autoconfigure.orm.PropertiesRepositoryConfigurationSource.*;


public class DynamicRepositoryBeanDefinitionRegistrarSupport
        implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {
    private static final Logger logger = LoggerFactory.getLogger(DynamicRepositoryBeanDefinitionRegistrarSupport.class);
    public static final String FILENAME_SUFFIX = ".orm.properties";

    private Environment environment;
    private ResourceLoader resourceLoader;


    /**
     * Searches the classpath for <code>*.orm.properties</code> files and creates a persistence context for each
     * driven by the contained property values.
     *
     * @param importingClassMetadata
     * @param registry
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);

        try {
            logger.debug("Scanning classpath for " + FILENAME_SUFFIX + " files.");
            Resource[] resources = resolver.getResources("classpath*:**/*" + FILENAME_SUFFIX);
            Set<String> unitNames = new HashSet<>();
            for (Resource o : resources) {
                String prefix = unitNameFromFilename(o.getFilename());
                if (unitNames.add(prefix)) {
                    buildDataSourceQuietly(prefix, o, registry);
                }
            }
        } catch (IOException e) {
            logger.error("Failed scanning classpath for " + FILENAME_SUFFIX + " files.", e);
        }
    }

    /**
     * Builds all of the bean definitions required to support a DataSource, including the DataSource,
     * EntityManagerFactory and TransactionManager.
     *
     * @param resource
     * @param registry
     */
    private void buildDataSourceQuietly(String unitName, Resource resource, BeanDefinitionRegistry registry) {
        logger.debug("Registering bean definitions for classpath resource '{}'.", resource.getFilename());

        try {
            Map<String, String> properties = loadProperties(resource);

            logger.info("Configuring persistence unit '{}' with properties: {}.",
                    unitName, maskedProperties(properties));

            String dataSourceName = unitName + "DataSource";
            registerDataSourceDefinitions(dataSourceName, properties, registry);

            PropertiesRepositoryConfigurationSource source =
                    new PropertiesRepositoryConfigurationSource(properties, environment);

            String entityManagerFactoryName = unitName + "EntityManagerFactory";
            registerEntityManagerFactoryDefinitions(entityManagerFactoryName, dataSourceName, unitName, source, registry);

            String transactionManagerName = unitName + "TransactionManager";
            registerTransactionManagerDefinitions(transactionManagerName, entityManagerFactoryName, registry);

            registerOrmRegistrationDefinitions(unitName + "OrmRegistration", dataSourceName, entityManagerFactoryName,
                    transactionManagerName, source, registry);

            // export entity manager factory and transaction manager in repository configuration
            properties.put("entityManagerFactoryRef", entityManagerFactoryName);
            properties.put("transactionManagerRef", transactionManagerName);
            registerRepositoryDefinitions(source, registry);
        } catch (IOException e) {
            // log the error and return without throwing exception to continue processing other data source properties
            logger.error("Failed building data source from classpath resource '" + resource.getFilename() + "'.", e);
        }
    }

    private String unitNameFromFilename(String filename) {
        return filename.substring(0, filename.length() - FILENAME_SUFFIX.length()).toLowerCase();
    }

    private Map<String, String> loadProperties(Resource resource) throws IOException {
        Map<String, String> propertiesMap = new HashMap<>();
        Properties props = new Properties();

        props.load(resource.getInputStream());
        for (String o : props.stringPropertyNames()) {
            propertiesMap.put(o, props.getProperty(o));
        }

        return propertiesMap;
    }

    private Map<String, String> maskedProperties(Map<String, String> properties) {
        Map<String, String> maskedProperties = new HashMap<>(properties);
        if (maskedProperties.remove(USERNAME) != null) {
            maskedProperties.put(USERNAME, "********");
        }
        if (maskedProperties.remove(PASSWORD) != null) {
            maskedProperties.put(PASSWORD, "********");
        }
        return maskedProperties;
    }


    /**
     * Builds a Spring bean definition for a <code>DataSource</code> driven by properties.
     *
     * @param beanName
     * @param properties
     * @param registry
     */
    private void registerDataSourceDefinitions(String beanName, Map<String, String> properties,
                                               BeanDefinitionRegistry registry) {
        logger.debug("Defining DataSource '{}'.", beanName);

        registry.registerBeanDefinition(beanName, BeanDefinitionBuilder
                .rootBeanDefinition(com.mchange.v2.c3p0.ComboPooledDataSource.class.getName())
                .addPropertyValue("driverClass", properties.get(DRIVER_CLASS_NAME))
                .addPropertyValue("jdbcUrl", properties.get(URL))
                .addPropertyValue("user", properties.get(USERNAME))
                .addPropertyValue("password", properties.get(PASSWORD))
                .addPropertyValue("minPoolSize", "0")
                .addPropertyValue("maxPoolSize", "20")
                .getBeanDefinition());
    }


    private void registerEntityManagerFactoryDefinitions(String entityManagerFactoryName, String dataSourceName,
                                                         String persistenceUnitName,
                                                         PropertiesRepositoryConfigurationSource source,
                                                         BeanDefinitionRegistry registry) {
        logger.debug("Defining EntityManagerFactory '{}' for DataSource '{}'.", entityManagerFactoryName,
                dataSourceName);

        registry.registerBeanDefinition(entityManagerFactoryName + "VendorAdapter", BeanDefinitionBuilder
                .rootBeanDefinition(HibernateJpaVendorAdapter.class.getName())
                .getBeanDefinition());

        registry.registerBeanDefinition(entityManagerFactoryName, BeanDefinitionBuilder
                .rootBeanDefinition(LocalContainerEntityManagerFactoryBean.class.getName())
                .addPropertyReference("jpaVendorAdapter", entityManagerFactoryName + "VendorAdapter")
                .addPropertyReference("dataSource", dataSourceName)
                .addPropertyValue("persistenceUnitName", persistenceUnitName)
                .addPropertyValue("packagesToScan", source.getBasePackages())
                .addPropertyValue("jpaProperties", source.getProperties())
                .getBeanDefinition());
    }


    private void registerTransactionManagerDefinitions(String transactionManagerName, String entityManagerFactoryName,
                                                       BeanDefinitionRegistry registry) {
        logger.debug("Defining PlatformTransactionManager '{}' for EntityManagerFactory '{}'.",
                transactionManagerName, entityManagerFactoryName);

        registry.registerBeanDefinition(transactionManagerName, BeanDefinitionBuilder
                .rootBeanDefinition(JpaTransactionManager.class.getName())
                .addPropertyReference("entityManagerFactory", entityManagerFactoryName)
                .getBeanDefinition());
    }


    private void registerOrmRegistrationDefinitions(String ormRegistrationName, String dataSourceName,
                                                    String entityManagerFactoryName, String transactionManagerName,
                                                    PropertiesRepositoryConfigurationSource source,
                                                    BeanDefinitionRegistry registry) {
        logger.debug("Defining OrmRegistration '{}' with DataSource '{}', EntityManagerFactory '{}', PlatformTransactionManager '{}' and PropertiesRepositoryConfigurationSource {}.",
                ormRegistrationName, dataSourceName, entityManagerFactoryName, transactionManagerName, source);

        registry.registerBeanDefinition(ormRegistrationName, BeanDefinitionBuilder
                .rootBeanDefinition(OrmRegistration.class)
                .addPropertyReference("dataSource", dataSourceName)
                .addPropertyReference("entityManagerFactory", entityManagerFactoryName)
                .addPropertyReference("platformTransactionManager", transactionManagerName)
                .addPropertyValue("source", source)
                .getBeanDefinition());
    }


    private void registerRepositoryDefinitions(PropertiesRepositoryConfigurationSource configurationSource,
                                               BeanDefinitionRegistry registry) {

        RepositoryConfigurationExtension extension = new JpaRepositoryConfigExtension();
        RepositoryConfigurationUtils.exposeRegistration(extension, registry, configurationSource);

        RepositoryConfigurationDelegate delegate = new RepositoryConfigurationDelegate(configurationSource,
                resourceLoader, environment);

        delegate.registerRepositoriesIn(registry, extension);
    }


    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}