package io.github.khezyapp.jooqspec.util;

import io.github.khezyapp.jooqspec.JooqPageRequest;
import io.github.khezyapp.jooqspec.JooqPaginationQuery;

/**
 * Utility class for constructing {@link JooqPaginationQuery} instances.
 * <p>
 * This class orchestrates the creation of a complete pagination query by combining
 * a string-based filter, parsed via {@link JooqSpecifications}, with paging
 * and sorting metadata.
 * </p>
 */
public final class JooqPaginationQueries {

    private JooqPaginationQueries() {
    }

    /**
     * Creates a {@link JooqPaginationQuery} by parsing a raw query string and
     * associating it with pagination details.
     *
     * @param rawQuery the raw query string representing the filter logic
     * @param pageable the pagination and sorting configuration
     * @return a unified query object containing the jOOQ specification and paging data
     */
    public static JooqPaginationQuery of(final String rawQuery,
                                         final JooqPageRequest pageable) {
        final var specification = JooqSpecifications.of(rawQuery);
        return new JooqPaginationQuery(specification, pageable);
    }
}
