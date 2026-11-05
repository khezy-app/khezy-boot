package io.github.khezyapp.a2a.store.jpa;

import io.github.khezyapp.a2a.store.jpa.crypto.A2ACipher;
import io.github.khezyapp.a2a.store.jpa.crypto.A2ACipherContexts;
import io.github.khezyapp.a2a.store.jpa.crypto.StringEncryptorA2ACipher;
import io.github.khezyapp.crypto.contract.CipherContext;
import io.github.khezyapp.crypto.contract.StringEncryptor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the source-compatibility adapter delegates to {@code khezy-crypto} and preserves
 * the row AAD.
 */
class A2ACipherAdapterTest {

    @Test
    void delegatesAndPreservesAadForTasks() {
        final var recorder = new RecordingStringEncryptor();
        final A2ACipher cipher = StringEncryptorA2ACipher.tasks(recorder);

        final var encrypted = cipher.encrypt("payload", "a2a:task:task-1");
        final var decrypted = cipher.decrypt(encrypted, "a2a:task:task-1");

        assertThat(decrypted).isEqualTo("payload");
        assertThat(recorder.contexts).hasSize(2);
        assertThat(recorder.contexts.get(0).purpose())
                .isEqualTo(A2ACipherContexts.TASK_PURPOSE);
        assertThat(recorder.contexts.get(0).aad()).isEqualTo("a2a:task:task-1");
        assertThat(recorder.contexts.get(1)).isEqualTo(recorder.contexts.get(0));
    }

    @Test
    void delegatesAndPreservesAadForPushConfigs() {
        final var recorder = new RecordingStringEncryptor();
        final A2ACipher cipher = StringEncryptorA2ACipher.pushConfigs(recorder);

        final var encrypted = cipher.encrypt("payload", "a2a:push:task-1:cfg-1");
        cipher.decrypt(encrypted, "a2a:push:task-1:cfg-1");

        assertThat(recorder.contexts.get(0).purpose())
                .isEqualTo(A2ACipherContexts.PUSH_CONFIG_PURPOSE);
        assertThat(recorder.contexts.get(0).aad())
                .isEqualTo("a2a:push:task-1:cfg-1");
    }

    @Test
    void usesDefaultPurposeWhenNotSpecialized() {
        final var recorder = new RecordingStringEncryptor();
        final A2ACipher cipher = new StringEncryptorA2ACipher(recorder);

        cipher.encrypt("payload", "row-1");

        assertThat(recorder.contexts.get(0).purpose())
                .isEqualTo(StringEncryptorA2ACipher.DEFAULT_PURPOSE);
    }

    private static final class RecordingStringEncryptor implements StringEncryptor {

        private final List<CipherContext> contexts = new ArrayList<>();

        @Override
        public String encrypt(final String plaintext, final CipherContext context) {
            contexts.add(context);
            return "enc:" + plaintext;
        }

        @Override
        public String decrypt(final String ciphertext, final CipherContext context) {
            contexts.add(context);
            return ciphertext.substring("enc:".length());
        }
    }
}
