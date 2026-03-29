package io.github.khezyapp.api.audit.config;

import io.github.khezyapp.api.audit.NoopAuditLogService;
import io.github.khezyapp.api.audit.api.AuditLogService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Conditional(OnAuditEnabledCondition.class)
@Import({
        KhezyAuditDataConfiguration.class,
        KhezyAuditLogAutoConfiguration.class,
        KhezyAuditHibernateConfiguration.class,
        ObjectDiffConfiguration.class,
})
public class KhezyAuditLogMainAutoConfiguration {

    /**
     * Provides a fallback {@link AuditLogService} that performs no action.
     * <p>
     * This ensures the application starts even if the user hasn't provided a
     * custom persistence or messaging implementation for the logs.
     * </p>
     *
     * @return a no-op audit log service
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditLogService auditLogService() {
        return new NoopAuditLogService();
    }
}
