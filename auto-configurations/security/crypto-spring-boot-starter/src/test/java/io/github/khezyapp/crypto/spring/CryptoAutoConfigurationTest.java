package io.github.khezyapp.crypto.spring;

import io.github.khezyapp.crypto.contract.CipherContext;
import io.github.khezyapp.crypto.contract.KeyCreateRequest;
import io.github.khezyapp.crypto.contract.KeyManager;
import io.github.khezyapp.crypto.contract.StringEncryptor;
import io.github.khezyapp.crypto.defaults.InMemoryKeyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CryptoAutoConfigurationTest {

    private static final String KEY = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(new byte[32]);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CryptoAutoConfiguration.class))
            .withPropertyValues(
                    "io.github.khezyapp.crypto.active-key-id=k1",
                    "io.github.khezyapp.crypto.keys.k1=" + KEY);

    @Test
    void shouldCreateBeans() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean(StringEncryptor.class));
            assertNotNull(context.getBean(KeyManager.class));
            assertNotNull(context.getBean(io.github.khezyapp.crypto.rotation.KeyRotationService.class));
        });
    }

    @Test
    void shouldEncryptAndDecrypt() {
        runner.run(context -> {
            final var encryptor = context.getBean(StringEncryptor.class);
            final var value = encryptor.encrypt("secret", CipherContext.of("test"));
            assertEquals("secret", encryptor.decrypt(value, CipherContext.of("test")));
        });
    }

    @Test
    void shouldFailWithoutActiveKey() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CryptoAutoConfiguration.class))
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void shouldAllowCustomKeyManager() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CryptoAutoConfiguration.class))
                .withPropertyValues("io.github.khezyapp.crypto.key-provider=custom")
                .withBean(KeyManager.class, CryptoAutoConfigurationTest::customManager)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals("custom",
                            context.getBean(KeyManager.class).activeKey().metadata().id());
                });
    }

    @Test
    void shouldRejectAutoRotationWithReadOnlyProvider() {
        runner.withPropertyValues("io.github.khezyapp.crypto.rotation.auto=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    private static KeyManager customManager() {
        final var provider = new InMemoryKeyProvider();
        provider.create(KeyCreateRequest.builder().id("custom").activateNow(true).build());
        return provider;
    }
}
