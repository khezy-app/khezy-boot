package io.github.khezyapp.jooqspec.data;

public record PageMetadata(
        int size,
        int number,
        int totalElements,
        int totalPages
) {
}
