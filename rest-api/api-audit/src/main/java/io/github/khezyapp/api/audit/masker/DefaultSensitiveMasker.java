package io.github.khezyapp.api.audit.masker;

import io.github.khezyapp.api.audit.api.SensitiveMasker;
import io.github.khezyapp.api.audit.api.SensitiveMaskerStrategy;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultSensitiveMasker implements SensitiveMasker {
    private final SensitiveMaskerStrategy masker;

    @Override
    public Object mask(final Object payload) {
        final var context = new DefaultMaskerContext(masker);
        return context.processMask(payload);
    }
}
