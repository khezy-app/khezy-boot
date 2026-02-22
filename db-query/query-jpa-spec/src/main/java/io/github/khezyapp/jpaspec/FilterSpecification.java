package io.github.khezyapp.jpaspec;

import io.github.khezyapp.grammar.ASTSpecs;
import io.github.khezyapp.grammar.ast.QuerySpec;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * A JPA {@link Specification} implementation that bridges a string-based query
 * to the JPA Criteria API using an Abstract Syntax Tree (AST).
 *
 * <p>This class parses a raw query string into a {@link QuerySpec} and uses
 * a {@code JpaSpecificationVisitor} to translate the AST into a JPA {@link Predicate}.</p>
 *
 * @param <T> the type of the root entity
 */
public class FilterSpecification<T> implements Specification<T> {

    private final QuerySpec astRoot;

    /**
     * Constructs a specification by parsing the provided filter query.
     * @param filterQuery the raw query string (e.g., "status = 'ACTIVE'")
     */
    public FilterSpecification(final String filterQuery) {
        this.astRoot = StringUtils.hasText(filterQuery) ?
                ASTSpecs.fromQuery(filterQuery) : null;
    }

    /**
     * Translates the AST root into a JPA Predicate.
     *
     * @param root the root entity
     * @param query the criteria query
     * @param cb the criteria builder
     * @return a JPA Predicate representing the AST logic, or a conjunction (always true) if empty
     */
    @Override
    public Predicate toPredicate(final Root<T> root,
                                 final CriteriaQuery<?> query,
                                 final CriteriaBuilder cb) {
        // Create the visitor and start the double-dispatch traversal
        final var visitor = new JpaSpecificationVisitor<>(root, query, cb);
        return Objects.nonNull(astRoot) ?
                astRoot.accept(visitor) : cb.conjunction();
    }

    /**
     * Returns the underlying AST representation of the query.
     * @return the query specification root
     */
    public QuerySpec getAstRoot() {
        return astRoot;
    }
}
