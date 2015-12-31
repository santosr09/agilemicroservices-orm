package org.agilemicroservices.autoconfigure.orm;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;


/**
 * Enables dynamic JPA repository loading, searching the classpath the *.orm.properties files and automatically
 * configuring an ORM for each.
 */
@Target(ElementType.TYPE)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Import(DynamicRepositoryBeanDefinitionRegistrarSupport.class)
public @interface EnableDynamicJpaRepositories {
}