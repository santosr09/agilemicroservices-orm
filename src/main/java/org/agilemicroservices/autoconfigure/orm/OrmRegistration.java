package org.agilemicroservices.autoconfigure.orm;

import org.springframework.transaction.PlatformTransactionManager;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;


/**
 * Defines an instance of the ORM framework including the cooperating data source, JPA entity manager factory and
 * transaction manager.
 */
public class OrmRegistration {
    private DataSource dataSource;
    private EntityManagerFactory entityManagerFactory;
    private PlatformTransactionManager platformTransactionManager;
    private PropertiesRepositoryConfigurationSource source;


    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }

    public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public PlatformTransactionManager getPlatformTransactionManager() {
        return platformTransactionManager;
    }

    public void setPlatformTransactionManager(PlatformTransactionManager platformTransactionManager) {
        this.platformTransactionManager = platformTransactionManager;
    }

    public PropertiesRepositoryConfigurationSource getSource() {
        return source;
    }

    public void setSource(PropertiesRepositoryConfigurationSource source) {
        this.source = source;
    }
}