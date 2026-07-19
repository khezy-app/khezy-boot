package io.github.khezyapp.api.security.autoconfigure;

import io.github.khezyapp.api.security.aop.RowLevelSecurityMethodInterceptor;
import io.github.khezyapp.api.security.api.RowLevelSecurityRule;
import io.github.khezyapp.api.security.expression.KhezyMethodSecurityExpressionHandler;
import io.github.khezyapp.api.security.registry.RowLevelSecurityRuleRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Role;

import java.util.ArrayList;

/**
 * Auto-configuration class for Row-Level Security (RLS) infrastructure.
 * Sets up the AOP Advisor and Interceptor responsible for managing Hibernate filters.
 */
@AutoConfiguration
@AutoConfigureAfter(
        name = "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
)
public class KhezyRowLevelSecurityAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(EntityManagerFactory.class)
    public static class RowLevelSecurityInterceptorConfiguration {
        /**
         * Defines the AOP Advisor.
         * Uses a specific order (600) to ensure predictable execution relative to other security interceptors.
         */
        @Bean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        public static Advisor rowLevelSecurityAdvisor(
                @Lazy final RowLevelSecurityMethodInterceptor rowLevelSecurityMethodInterceptor
        ) {
            final var advisor = new DefaultPointcutAdvisor(rowLevelSecurityMethodInterceptor);
            advisor.setOrder(600);
            return advisor;
        }

        @Bean
        public RowLevelSecurityMethodInterceptor rowLevelSecurityMethodInterceptor(
                final ObjectProvider<EntityManager> entityManagers,
                final ObjectProvider<KhezyMethodSecurityExpressionHandler> methodSecurityExpressionHandlers,
                final ObjectProvider<RowLevelSecurityRule> rowLevelSecurityRules
        ) {
            final var rules = new ArrayList<RowLevelSecurityRule>();
            rowLevelSecurityRules.stream().forEach(rules::add);
            return new RowLevelSecurityMethodInterceptor(
                    entityManagers,
                    methodSecurityExpressionHandlers.getObject(),
                    new RowLevelSecurityRuleRegistry(rules)
            );
        }
    }
}
