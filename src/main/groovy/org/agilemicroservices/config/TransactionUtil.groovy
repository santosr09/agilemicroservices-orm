package org.agilemicroservices.config


import org.agilemicroservices.autoconfigure.orm.OrmRegistration
import org.springframework.context.ApplicationContext
import org.springframework.data.repository.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus


class TransactionUtil {
    private static Map<OrmRegistration, ThreadLocal<TransactionStatus>> transactionStatusMap = new HashMap<>()

    private static OrmRegistration registrationFor(Repository<?, ?> repo) {
        Class<?> repositoryInterface = repositoryInterfaceOf(repo)
        if (repositoryInterface == null) {
            throw new IllegalArgumentException("No repository interface found.")
        }

        // retrieve package name, using "" for null package, indicating java's default package
        String repoPackageName = ""
        Package repoPackage = repositoryInterface.package
        if (repoPackage != null) {
            repoPackageName = repoPackage.name
        }

        ApplicationContext context = SpringUtil.context
        Map<String, OrmRegistration> registrations = context.getBeansOfType(OrmRegistration.class)
        OrmRegistration registration;
        outter: for (OrmRegistration o : registrations.values()) {
            for (String j : o.source.basePackages) {
                if (repoPackageName.startsWith(j)) {
                    registration = o
                    break outter
                }
            }
        }

        if (registration == null) {
            throw new IllegalArgumentException("Not a managed repository.")
        }

        return registration;
    }

    private static Class<?> repositoryInterfaceOf(Repository<?, ?> repo) {
        for (Class<?> o : repo.getClass().getInterfaces()) {
            if (o.simpleName.endsWith("Repository")) {
                return o;
            }
        }
        return null;
    }


    public static void begin(Repository<?, ?> repo) {
        OrmRegistration registration = registrationFor(repo)
        beginInternal(registration)
    }

    public static void beginAll() {
        ApplicationContext context = SpringUtil.context
        Map<String, OrmRegistration> registrations = context.getBeansOfType(OrmRegistration.class)
        registrations.values().each {
            beginInternal(it)
        }
    }

    private static void beginInternal(OrmRegistration registration) {
        ThreadLocal<TransactionStatus> threadLocal = transactionStatusMap.get(registration)
        if (threadLocal == null) {
            PlatformTransactionManager transactionManager = registration.platformTransactionManager
            TransactionStatus status = transactionManager.getTransaction(null)
            threadLocal = new ThreadLocal<>()
            threadLocal.set(status)
            transactionStatusMap.put(registration, threadLocal)
        }
    }


    public static void rollback(Repository<?, ?> repo) {
        OrmRegistration registration = registrationFor(repo)
        ThreadLocal<TransactionStatus> threadLocal = transactionStatusMap.remove(registration)
        registration.platformTransactionManager.rollback(threadLocal.get())
    }

    public static void rollbackAll() {
        for (Map.Entry<OrmRegistration, ThreadLocal<TransactionStatus>> o : transactionStatusMap.values()) {
            PlatformTransactionManager transactionManager = o.key.platformTransactionManager
            transactionManager.rollback(o.value.get())
        }
    }


    public static void commit(Repository<?, ?> repo) {
        OrmRegistration registration = registrationFor(repo)
        ThreadLocal<TransactionStatus> threadLocal = transactionStatusMap.remove(registration)
        registration.platformTransactionManager.commit(threadLocal.get())
    }

    public static void commitAll() {
        for (Map.Entry<OrmRegistration, ThreadLocal<TransactionStatus>> o : transactionStatusMap.values()) {
            PlatformTransactionManager transactionManager = o.key.platformTransactionManager
            transactionManager.commit(o.value.get())
        }
    }
}
