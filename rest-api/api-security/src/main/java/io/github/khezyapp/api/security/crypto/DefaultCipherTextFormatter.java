package io.github.khezyapp.api.security.crypto;

/**
 * Default {@link CipherTextFormatter} that produces strings in the format
 * {@code formatterId.version.cipherText}. This is the fallback formatter
 * registered under the ID {@code "default"}.
 */
public class DefaultCipherTextFormatter implements CipherTextFormatter {

    @Override
    public String getFormatId() {
        return "default";
    }

    @Override
    public String format(final CipherEnvelope envelope) {
        final var encryptData = envelope.encryptorData();
        final var cipherText = envelope.cipherText();
        return String.format(
                "%s.%s.%s",
                encryptData.formatterId(),
                encryptData.version(),
                cipherText
        );
    }

    @Override
    public CipherEnvelope parse(final String cipherText) {
        final var parts = cipherText.split("\\.", 3);
        if (parts.length != 3) {
            throw new IllegalStateException("Invalid cipher text");
        }
        return CipherEnvelope.builder()
                .encryptorData(
                        EncryptorData.builder()
                                .formatterId(parts[0])
                                .version(parts[1])
                                .build()
                )
                .cipherText(parts[2])
                .build();
    }
}
