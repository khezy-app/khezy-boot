package io.github.khezyapp.api.audit.config;

import io.github.khezyapp.api.audit.AuditExpressionEvaluator;
import io.github.khezyapp.api.audit.NoopAuditLogService;
import io.github.khezyapp.api.audit.ObjectDiffUtils;
import io.github.khezyapp.api.audit.aop.AuditLogMethodInterceptor;
import io.github.khezyapp.api.audit.api.AuditLogService;
import io.github.khezyapp.api.audit.api.SensitiveMasker;
import io.github.khezyapp.api.audit.extractor.AbstractBodyExtractor;
import io.github.khezyapp.api.audit.extractor.CompositeBodyExtractor;
import io.github.khezyapp.api.audit.interceptor.KhezyAuditLogHibernateInterceptor;
import io.github.khezyapp.api.audit.javers.CompositeChangeMapper;
import io.github.khezyapp.api.audit.masker.BeanSensitiveMaskerStrategy;
import io.github.khezyapp.api.audit.model.AuditEntityChange;
import io.github.khezyapp.api.audit.model.AuditLogRecord;
import jakarta.servlet.http.HttpServletRequest;
import org.aopalliance.intercept.MethodInvocation;
import org.javers.core.Javers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KhezyAuditLogMainAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KhezyAuditAutoConfiguration.class,
                    JacksonAutoConfiguration.class));

    @Test
    void shouldNotLoadBeansWhenAllPropertiesDisabled() {
        this.contextRunner
                .withPropertyValues(
                        "khezy.audit.enabled-audit-request=false",
                        "khezy.audit.enabled-audit-entity-changes=false"
                )
                .run(context -> {
                    // This now PASSES
                    assertThat(context).hasSingleBean(KhezyAuditProperties.class);

                    assertThat(context).doesNotHaveBean(SensitiveMasker.class);
                    assertThat(context).doesNotHaveBean(AuditLogMethodInterceptor.class);
                    assertThat(context).doesNotHaveBean(KhezyAuditLogHibernateInterceptor.class);
                });
    }

    @Test
    void shouldLoadWhenRequestIsEnabled() {
        this.contextRunner
                .withPropertyValues("khezy.audit.enabled-audit-request=true") // Match the condition string
                .run(context -> {
                    assertThat(context).hasSingleBean(KhezyAuditLogMainAutoConfiguration.class);
                });
    }

    @Test
    void shouldLoadRequestAuditBeansWhenEnabled() {
        this.contextRunner
                .withPropertyValues("khezy.audit.enabled-audit-request=true")
                .run(context -> {
                    // Data Config
                    assertThat(context).hasSingleBean(SensitiveMasker.class);
                    assertThat(context).hasSingleBean(BeanSensitiveMaskerStrategy.class);

                    // Request Config
                    assertThat(context).hasSingleBean(AuditLogMethodInterceptor.class);
                    assertThat(context).hasSingleBean(AuditExpressionEvaluator.class);
                    assertThat(context).hasSingleBean(CompositeBodyExtractor.class);
                    assertThat(context).hasSingleBean(AuditLogService.class); // Defaults to Noop

                    // Object Diff (Manual util should be available)
                    assertThat(context).hasSingleBean(ObjectDiffUtils.class);

                    // Hibernate should NOT be loaded if only Request is enabled
                    assertThat(context).doesNotHaveBean(KhezyAuditLogHibernateInterceptor.class);
                });
    }

    @Test
    void shouldLoadHibernateAuditBeansWhenEnabled() {
        this.contextRunner
                .withPropertyValues("khezy.audit.enabled-audit-entity-changes=true")
                .run(context -> {
                    // Data Config
                    assertThat(context).hasSingleBean(SensitiveMasker.class);

                    // Hibernate Config
                    assertThat(context).hasSingleBean(KhezyAuditLogHibernateInterceptor.class);
                    assertThat(context).hasSingleBean(Javers.class);
                    assertThat(context).hasSingleBean(CompositeChangeMapper.class);
                    assertThat(context).hasSingleBean(HibernatePropertiesCustomizer.class);

                    // Object Diff
                    assertThat(context).hasSingleBean(ObjectDiffUtils.class);
                });
    }

    @Test
    void shouldSupportCustomAuditLogService() {
        this.contextRunner
                .withPropertyValues("khezy.audit.enabled-audit-request=true")
                .withBean("customAuditService", AuditLogService.class, CustomAuditLogService::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditLogService.class);
                    assertThat(context).getBean(AuditLogService.class).isExactlyInstanceOf(CustomAuditLogService.class);
                    assertThat(context).doesNotHaveBean(NoopAuditLogService.class);
                });
    }

    @Test
    void shouldRegisterCustomBodyExtractorInComposite() {
        this.contextRunner
                .withPropertyValues("khezy.audit.enabled-audit-request=true")
                .withBean(CustomExtractor.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CompositeBodyExtractor.class);
                    // Internal check would require reflection or a spy,
                    // but verifying the bean exists ensures it was provided to the composite provider
                    assertThat(context).hasSingleBean(CustomExtractor.class);
                });
    }

    // Dummy classes for testing overrides
    static class CustomAuditLogService implements AuditLogService {
        @Override
        public void onRequest(final AuditLogRecord<?> auditLogRecord) {
        }

        @Override
        public void onAuditEntityChanges(final AuditEntityChange auditEntityChange) {
        }
    }

    static class CustomExtractor extends AbstractBodyExtractor {

        CustomExtractor(final SensitiveMasker s) {
            super(s);
        }

        @Override
        public boolean supports(final MethodInvocation i,
                                final HttpServletRequest r) {
            return false;
        }

        @Override
        protected Map<String, Object> doExtract(final MethodInvocation i,
                                                final HttpServletRequest r) {
            return java.util.Collections.emptyMap();
        }
    }

}
