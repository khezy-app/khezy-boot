package io.github.khezyapp.jooqspec.data;

import java.util.List;

public record Page<T> (
        List<T> content,
        PageMetadata page
) {
}
