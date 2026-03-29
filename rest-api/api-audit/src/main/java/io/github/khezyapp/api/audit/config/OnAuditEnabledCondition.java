package io.github.khezyapp.api.audit.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

public class OnAuditEnabledCondition extends AnyNestedCondition {

    public OnAuditEnabledCondition() {
        super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(prefix = "khezy.audit", name = "enabled-audit-request", havingValue = "true")
    static class OnRequestEnabled {
    }

    @ConditionalOnProperty(prefix = "khezy.audit", name = "enabled-audit-entity-changes", havingValue = "true")
    static class OnEntityEnabled {
    }
}
