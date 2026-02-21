package io.github.khezyapp.jpaspec;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

public class JpaPaginationQuery<T> {
    private final FilterSpecification<T> spec;
    private final Pageable pageable;

    public JpaPaginationQuery(final String filterQuery,
                              final Pageable pageable) {
        this.spec = new FilterSpecification<>(filterQuery);
        this.pageable = pageable;
    }

    public Specification<T> getSpecification() {
        return spec;
    }

    public Pageable getPageable() {
        return pageable;
    }

    public Sort getSort() {
        return pageable.getSort();
    }

    public int getPageNumber() {
        return pageable.getPageNumber();
    }

    public int getPageSize() {
        return pageable.getPageSize();
    }
}
