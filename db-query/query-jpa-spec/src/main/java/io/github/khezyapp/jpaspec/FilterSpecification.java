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

public class FilterSpecification<T> implements Specification<T> {

    private final QuerySpec astRoot;

    public FilterSpecification(final String filerQuery) {
        this.astRoot = StringUtils.hasText(filerQuery) ?
                ASTSpecs.fromQuery(filerQuery) : null;
    }

    @Override
    public Predicate toPredicate(final Root<T> root,
                                 final CriteriaQuery<?> query,
                                 final CriteriaBuilder cb) {
        // Create the visitor and start the double-dispatch traversal
        final var visitor = new JpaSpecificationVisitor<>(root, query, cb);
        return Objects.nonNull(astRoot) ?
                astRoot.accept(visitor) : cb.conjunction();
    }

    public QuerySpec getAstRoot() {
        return astRoot;
    }
}
