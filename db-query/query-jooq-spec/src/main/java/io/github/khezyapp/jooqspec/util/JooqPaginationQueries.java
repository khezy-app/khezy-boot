package io.github.khezyapp.jooqspec.util;

import io.github.khezyapp.grammar.ast.QuerySpec;
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

    /**
     * Static factory method that creates a {@link JooqPaginationQuery} by combining
     * a {@link QuerySpec} and a {@link JooqPageRequest}.
     * <p>
     * This method internally transforms the {@code querySpec} into a {@link JooqSpecifications}
     * instance before initializing the pagination query object.
     * </p>
     *
     * @param querySpec the specification containing filtering and search criteria
     * @param pageable  the pagination and sorting parameters
     * @return a new instance of {@link JooqPaginationQuery} configured with the derived specification
     */
    public static JooqPaginationQuery of(final QuerySpec querySpec,
                                         final JooqPageRequest pageable) {
        final var specification = JooqSpecifications.of(querySpec);
        return new JooqPaginationQuery(specification, pageable);
    }
}
