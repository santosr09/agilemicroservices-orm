package org.agilemicroservices.config

import org.springframework.context.ApplicationContext

class RepositoryFactory
{

    public ApplicationContext getContext()
    {
        return SpringUtil.getContext();
    }

    public static <T> T get(Class<T> repo)
    {
        if (repo == null) {
            throw new IllegalArgumentException('repo name is null')
        }
        if (!repo.simpleName.endsWith('Repository')) {
            throw new IllegalArgumentException('repository name should end with Repository')
        }

        get(repo.simpleName)
    }

    public static <T> T get(String name)
    {
        SpringUtil.getContext().getBean(name.substring(0, 1).toLowerCase() + name.substring(1))
    }

    public static void init()
    {
    }
}
