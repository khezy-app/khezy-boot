package io.github.khezyapp.jooqspec.util;

import io.github.khezyapp.grammar.ASTSpecs;
import io.github.khezyapp.jooqspec.FilterJooqVisitor;
import io.github.khezyapp.jooqspec.JooqSpecification;
import org.jooq.impl.DSL;

import java.util.Collections;
import java.util.Objects;

/**
 * Utility class for creating {@link JooqSpecification} instances from raw query strings.
 * <p>
 * This class serves as the entry point for the jOOQ-based filtering engine, leveraging
 * the {@link ASTSpecs} parser to transform text into a structured jOOQ specification.
 * </p>
 */
public final class JooqSpecifications {

    private JooqSpecifications() {
    }

    /**
     * Parses a raw filter query string and converts it into a {@link JooqSpecification}.
     * <p>
     * If the input query is empty or null, a specification with empty/neutral
     * conditions and no grouping is returned.
     * </p>
     *
     * @param rawQuery the query string to parse (e.g., "status = 'ACTIVE' AND age > 18")
     * @return a fully constructed jOOQ specification
     */
    public static JooqSpecification of(final String rawQuery) {
        final var querySpec = ASTSpecs.fromQuery(rawQuery);
        if (Objects.isNull(querySpec)) {
            return new JooqSpecification.Builder()
                    .where(DSL.noCondition())
                    .having(DSL.noCondition())
                    .groupBy(Collections.emptyList())
                    .build();
        }

        return (JooqSpecification) querySpec.accept(new FilterJooqVisitor());
    }
}
