package io.github.khezyapp.api.security.autoconfigure;

import io.github.khezyapp.api.security.aop.RowLevelSecurityMethodInterceptor;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

class KhezyRowLevelSecurityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class,
                    KhezySecurityAutoConfiguration.class,
                    KhezyRowLevelSecurityAutoConfiguration.class))
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password="
            );

    @Test
    @DisplayName("Should register RowLevelSecurityMethodInterceptor when Hibernate is on classpath")
    void shouldRegisterInterceptor() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EntityManagerFactory.class);

            final var factory = context.getBean(EntityManagerFactory.class);
            final var entityManager = factory.createEntityManager();

            assertThat(entityManager).isNotNull();
            assertThat(context).hasSingleBean(RowLevelSecurityMethodInterceptor.class);
        });
    }

    @Test
    @DisplayName("Should register AOP Advisor with order 600")
    void shouldRegisterAdvisorWithOrder600() {
        this.contextRunner.run(context -> {
            assertThat(context).hasBean("rowLevelSecurityAdvisor");
            final var advisor = (Ordered) context.getBean("rowLevelSecurityAdvisor");
            assertThat(advisor.getOrder()).isEqualTo(600);
        });
    }

    @Test
    @DisplayName("Should not register RLS beans when Hibernate is not on classpath")
    void shouldNotRegisterWhenHibernateAbsent() {
        final var noHibernateRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KhezySecurityAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader("org.hibernate.Session"));

        noHibernateRunner.run(context -> {
            assertThat(context).doesNotHaveBean(RowLevelSecurityMethodInterceptor.class);
            assertThat(context).doesNotHaveBean("rowLevelSecurityAdvisor");
        });
    }
}
