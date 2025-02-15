/*
 * Copyright 2014 the original author or authors.
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
package org.agilemicroservices.autoconfigure.orm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.repository.config.RepositoryBeanNameGenerator;
import org.springframework.data.repository.config.RepositoryConfiguration;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;
import org.springframework.data.repository.config.RepositoryConfigurationSource;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Delegate for configuration integration to reuse the general way of detecting repositories. Customization is done by
 * providing a configuration format specific {@link RepositoryConfigurationSource} (currently either XML or annotations
 * are supported). The actual registration can then be triggered for different {@link RepositoryConfigurationExtension}
 * s.
 * <p>
 * Modified to eliminate casts of RepositoryConfigurationSource to concrete types and respective logic.
 *
 * @author Oliver Gierke
 */
public class RepositoryConfigurationDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryConfigurationDelegate.class);

    private static final String REPOSITORY_REGISTRATION = "Spring Data {} - Registering repository: {} - Interface: {} - Factory: {}";
    private static final String MULTIPLE_MODULES = "Multiple Spring Data modules found, entering strict repository configuration mode!";
    private static final String MODULE_DETECTION_PACKAGE = "org.springframework.data.**.repository.support";

    private final RepositoryConfigurationSource configurationSource;
    private final ResourceLoader resourceLoader;
    private final Environment environment;
    private final BeanNameGenerator beanNameGenerator;
    private final boolean inMultiStoreMode;

    /**
     * Creates a new {@link RepositoryConfigurationDelegate} for the given {@link RepositoryConfigurationSource} and
     * {@link ResourceLoader} and {@link Environment}.
     *
     * @param configurationSource must not be {@literal null}.
     * @param resourceLoader      must not be {@literal null}.
     * @param environment         must not be {@literal null}.
     */
    public RepositoryConfigurationDelegate(RepositoryConfigurationSource configurationSource,
                                           ResourceLoader resourceLoader, Environment environment) {
        Assert.notNull(resourceLoader);

        RepositoryBeanNameGenerator generator = new RepositoryBeanNameGenerator(resourceLoader.getClassLoader());

        this.beanNameGenerator = (BeanNameGenerator) generator;
        this.configurationSource = configurationSource;
        this.resourceLoader = resourceLoader;
        this.environment = defaultEnvironment(environment, resourceLoader);
        this.inMultiStoreMode = multipleStoresDetected();
    }

    /**
     * Defaults the environment in case the given one is null. Used as fallback, in case the legacy constructor was
     * invoked.
     *
     * @param environment    can be {@literal null}.
     * @param resourceLoader can be {@literal null}.
     * @return
     */
    private static Environment defaultEnvironment(Environment environment, ResourceLoader resourceLoader) {

        if (environment != null) {
            return environment;
        }

        return resourceLoader instanceof EnvironmentCapable ? ((EnvironmentCapable) resourceLoader).getEnvironment()
                : new StandardEnvironment();
    }

    /**
     * Registers the found repositories in the given {@link BeanDefinitionRegistry}.
     *
     * @param registry
     * @param extension
     * @return {@link BeanComponentDefinition}s for all repository bean definitions found.
     */
    public List<BeanComponentDefinition> registerRepositoriesIn(BeanDefinitionRegistry registry,
                                                                RepositoryConfigurationExtension extension) {

        extension.registerBeansForRoot(registry, configurationSource);

        RepositoryBeanDefinitionBuilder builder = new RepositoryBeanDefinitionBuilder(registry, extension, resourceLoader,
                environment);
        List<BeanComponentDefinition> definitions = new ArrayList<BeanComponentDefinition>();

        for (RepositoryConfiguration<? extends RepositoryConfigurationSource> configuration : extension
                .getRepositoryConfigurations(configurationSource, resourceLoader, inMultiStoreMode)) {

            BeanDefinitionBuilder definitionBuilder = builder.build(configuration);

            extension.postProcess(definitionBuilder, configurationSource);

            AbstractBeanDefinition beanDefinition = definitionBuilder.getBeanDefinition();
            String beanName = beanNameGenerator.generateBeanName(beanDefinition, registry);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(REPOSITORY_REGISTRATION, extension.getModuleName(), beanName,
                        configuration.getRepositoryInterface(), extension.getRepositoryFactoryBeanClassName());
            }

            registry.registerBeanDefinition(beanName, beanDefinition);
            definitions.add(new BeanComponentDefinition(beanDefinition, beanName));
        }

        return definitions;
    }

    /**
     * Scans {@code repository.support} packages for implementations of {@link RepositoryFactorySupport}. Finding more
     * than a single type is considered a multi-store configuration scenario which will trigger stricter repository
     * scanning.
     *
     * @return
     */
    private boolean multipleStoresDetected() {

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.setEnvironment(environment);
        scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter(new LenientAssignableTypeFilter(RepositoryFactorySupport.class));

        if (scanner.findCandidateComponents(MODULE_DETECTION_PACKAGE).size() > 1) {

            LOGGER.debug(MULTIPLE_MODULES);
            return true;
        }

        return false;
    }

    /**
     * Special {@link AssignableTypeFilter} that generally considers exceptions during type matching indicating a
     * non-match. TODO: Remove after upgrade to Spring 4.0.7.
     *
     * @author Oliver Gierke
     * @see https://jira.spring.io/browse/SPR-12042
     */
    private static class LenientAssignableTypeFilter extends AssignableTypeFilter {

        /**
         * Creates a new {@link LenientAssignableTypeFilter} for the given target type.
         *
         * @param targetType must not be {@literal null}.
         */
        public LenientAssignableTypeFilter(Class<?> targetType) {
            super(targetType);
        }

        /*
         * (non-Javadoc)
         * @see org.springframework.core.type.filter.AbstractTypeHierarchyTraversingFilter#match(org.springframework.core.type.classreading.MetadataReader, org.springframework.core.type.classreading.MetadataReaderFactory)
         */
        @Override
        public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {

            try {
                return super.match(metadataReader, metadataReaderFactory);
            } catch (Exception o_O) {
                return false;
            }
        }
    }
}
