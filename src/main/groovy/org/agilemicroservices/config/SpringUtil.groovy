package org.agilemicroservices.config

import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class SpringUtil {
    private static final ApplicationContext APPLICATION_CONTEXT = new AnnotationConfigApplicationContext(
            ScriptConfig.class)


    public static ApplicationContext getContext() {
        return APPLICATION_CONTEXT;
    }
}