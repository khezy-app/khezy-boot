package io.github.khezyapp.crypto.spring;

import io.github.khezyapp.crypto.contract.CipherTextCodec;
import io.github.khezyapp.crypto.contract.CryptoBackend;
import io.github.khezyapp.crypto.contract.EncryptionCounter;
import io.github.khezyapp.crypto.contract.Encryptor;
import io.github.khezyapp.crypto.contract.KeyManager;
import io.github.khezyapp.crypto.contract.StringEncryptor;
import io.github.khezyapp.crypto.defaults.AesGcmBackend;
import io.github.khezyapp.crypto.defaults.DefaultEncryptor;
import io.github.khezyapp.crypto.defaults.DefaultStringEncryptor;
import io.github.khezyapp.crypto.defaults.InMemoryEncryptionCounter;
import io.github.khezyapp.crypto.defaults.InMemoryKeyProvider;
import io.github.khezyapp.crypto.defaults.KhezyCipherTextCodec;
import io.github.khezyapp.crypto.defaults.PropertiesKeyProvider;
import io.github.khezyapp.crypto.defaults.ReadOnlyKeyManager;
import io.github.khezyapp.crypto.exception.CryptoConfigurationException;
import io.github.khezyapp.crypto.rotation.AuditListener;
import io.github.khezyapp.crypto.rotation.CompositeRotationPolicy;
import io.github.khezyapp.crypto.rotation.DefaultKeyRotationService;
import io.github.khezyapp.crypto.rotation.DefaultReEncryptor;
import io.github.khezyapp.crypto.rotation.KeyRotationService;
import io.github.khezyapp.crypto.rotation.NoopRotationLock;
import io.github.khezyapp.crypto.rotation.ReEncryptor;
import io.github.khezyapp.crypto.rotation.RotationLock;
import io.github.khezyapp.crypto.rotation.RotationPolicy;
import io.github.khezyapp.crypto.rotation.TimeBasedRotationPolicy;
import io.github.khezyapp.crypto.rotation.UsageBasedRotationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.List;

/**
 * Spring Boot auto-configuration for Khezy Crypto.
 */
@AutoConfiguration
@EnableConfigurationProperties(CryptoProperties.class)
@ConditionalOnProperty(prefix = "io.github.khezyapp.crypto", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CryptoAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(CryptoAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public Clock cryptoClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public KeyManager cryptoKeyManager(final CryptoProperties properties, final Environment environment) {
        properties.validate();
        warnAboutInsecureSource(properties, environment);
        final KeyManager manager = switch (properties.getKeyProvider()) {
            case IN_MEMORY -> new InMemoryKeyProvider();
            case PROPERTIES -> new ReadOnlyKeyManager(new PropertiesKeyProvider(
                    properties.getActiveKeyId(), properties.getKeys()));
            case CUSTOM -> throw new CryptoConfigurationException(
                    "Define a KeyManager bean for key-provider=custom");
        };
        if (properties.getRotation().isAuto() && manager instanceof ReadOnlyKeyManager) {
            throw new CryptoConfigurationException(
                    "Automatic rotation requires a writable KeyManager");
        }
        return manager;
    }

    @Bean
    @ConditionalOnMissingBean
    public CryptoBackend cryptoBackend() {
        return new AesGcmBackend();
    }

    @Bean
    @ConditionalOnMissingBean
    public EncryptionCounter cryptoEncryptionCounter() {
        return new InMemoryEncryptionCounter();
    }

    @Bean
    @ConditionalOnMissingBean
    public Encryptor cryptoEncryptor(final KeyManager keyManager,
                                     final CryptoBackend backend,
                                     final EncryptionCounter counter) {
        return new DefaultEncryptor(keyManager, backend, counter);
    }

    @Bean
    @ConditionalOnMissingBean
    public CipherTextCodec cryptoCipherTextCodec() {
        return new KhezyCipherTextCodec();
    }

    @Bean
    @ConditionalOnMissingBean
    public StringEncryptor cryptoStringEncryptor(final Encryptor encryptor,
                                                 final CipherTextCodec codec) {
        return new DefaultStringEncryptor(encryptor, codec);
    }

    @Bean
    @ConditionalOnMissingBean
    public RotationPolicy cryptoRotationPolicy(final CryptoProperties properties) {
        return new CompositeRotationPolicy(List.of(
                new TimeBasedRotationPolicy(properties.getRotation().getCryptoperiod()),
                new UsageBasedRotationPolicy(properties.getRotation().getUsageThreshold())));
    }

    @Bean
    @ConditionalOnMissingBean
    public RotationLock cryptoRotationLock() {
        return new NoopRotationLock();
    }

    @Bean
    @ConditionalOnMissingBean
    public CryptoAuditEventPublisher cryptoAuditEventPublisher(
            final ApplicationEventPublisher publisher) {
        return new CryptoAuditEventPublisher(publisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public KeyRotationService cryptoKeyRotationService(final KeyManager keyManager,
                                                       final EncryptionCounter counter,
                                                       final Encryptor encryptor,
                                                       final CipherTextCodec codec,
                                                       final Clock clock,
                                                       final ObjectProvider<AuditListener> listeners) {
        return new DefaultKeyRotationService(keyManager, counter, encryptor, codec, clock,
                listeners.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public ReEncryptor cryptoReEncryptor(final Encryptor encryptor,
                                         final CipherTextCodec codec) {
        return new DefaultReEncryptor(encryptor, codec);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HealthIndicator.class)
    static class CryptoHealthConfiguration {

        @Bean
        @ConditionalOnMissingBean
        CryptoHealthIndicator cryptoHealthIndicator(final KeyManager keyManager,
                                                    final EncryptionCounter counter,
                                                    final CryptoProperties properties,
                                                    final Clock clock) {
            return new CryptoHealthIndicator(keyManager, counter, properties, clock);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "io.github.khezyapp.crypto.rotation", name = "auto",
            havingValue = "true")
    @EnableScheduling
    static class CryptoRotationSchedulingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        CryptoRotationScheduler cryptoRotationScheduler(final KeyManager keyManager,
                                                        final EncryptionCounter counter,
                                                        final RotationPolicy policy,
                                                        final KeyRotationService rotationService,
                                                        final RotationLock lock,
                                                        final CryptoProperties properties,
                                                        final Clock clock) {
            return new CryptoRotationScheduler(keyManager, counter, policy, rotationService,
                    lock, properties, clock);
        }
    }

    private static void warnAboutInsecureSource(final CryptoProperties properties,
                                                final Environment environment) {
        if (properties.getKeyProvider() != CryptoProperties.KeyProviderType.PROPERTIES) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            return;
        }
        final var message = "Property-based crypto keys are not recommended outside dev/test; "
                + "use a KMS, Vault, or HSM";
        if (properties.isFailOnInsecureKeySource()) {
            throw new CryptoConfigurationException(message);
        }
        LOGGER.warn(message);
    }
}
