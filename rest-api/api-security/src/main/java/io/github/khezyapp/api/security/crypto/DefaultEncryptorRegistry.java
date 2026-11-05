package io.github.khezyapp.api.security.crypto;

import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Standard implementation of {@link EncryptorRegistry} that builds
 * {@link TextEncryptor} instances via {@code Encryptors.delux} and caches
 * them in a thread-safe map. Encryptor data is indexed by version label.
 */
public class DefaultEncryptorRegistry implements EncryptorRegistry {
    private final Map<String, EncryptorData> encryptorDataMap;
    private final Map<String, TextEncryptor> textEncryptorMap;

    /**
     * Creates a registry from the supplied encryptor data entries.
     * Entries are indexed by their {@link EncryptorData#version()};
     * duplicate versions overwrite earlier entries.
     *
     * @param encryptorData the list of versioned encryptor configurations,
     *                      may be {@code null}
     */
    public DefaultEncryptorRegistry(final List<EncryptorData> encryptorData) {
        this.encryptorDataMap = Optional.ofNullable(encryptorData)
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(EncryptorData::version, Function.identity()));
        this.textEncryptorMap = new ConcurrentHashMap<>();
    }

    @Override
    public TextEncryptor getTextEncryptor(final String version) {
        return textEncryptorMap.computeIfAbsent(version, key -> {
            final var data = getEncryptorData(key);
            return Encryptors.delux(data.password(), data.salt());
        });
    }

    @Override
    public EncryptorData getEncryptorData(final String version) {
        final var data = encryptorDataMap.get(version);
        if (Objects.isNull(data)) {
            throw new IllegalArgumentException("No data found for version " + version);
        }
        return data;
    }
}
