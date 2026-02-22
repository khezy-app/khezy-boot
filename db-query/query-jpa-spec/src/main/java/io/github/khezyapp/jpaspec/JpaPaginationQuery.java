package io.github.khezyapp.jpaspec;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Data transfer object that encapsulates JPA pagination, sorting, and filtering logic.
 * <p>
 * This class combines a {@link FilterSpecification} derived from a query string with
 * Spring Data {@link Pageable} information to facilitate consistent database querying.
 * </p>
 *
 * @param <T> the type of the entity being queried
 */
public class JpaPaginationQuery<T> {
    private final FilterSpecification<T> spec;
    private final Pageable pageable;

    /**
     * Constructs a pagination query with a filter and paging configuration.
     *
     * @param filterQuery the raw string-based filter query
     * @param pageable the pagination and sorting information
     */
    public JpaPaginationQuery(final String filterQuery,
                              final Pageable pageable) {
        this.spec = new FilterSpecification<>(filterQuery);
        this.pageable = pageable;
    }

    /**
     * Gets the parsed filter specification.
     * @return the specification instance
     */
    public Specification<T> getSpecification() {
        return spec;
    }

    /**
     * Gets the pagination information.
     * @return the pageable instance
     */
    public Pageable getPageable() {
        return pageable;
    }

    /**
     * Gets the sort configuration from the pageable object.
     * @return the sort definition
     */
    public Sort getSort() {
        return pageable.getSort();
    }

    /**
     * Gets the current page number.
     * @return the zero-based page index
     */
    public int getPageNumber() {
        return pageable.getPageNumber();
    }

    /**
     * Gets the requested page size.
     * @return the number of items per page
     */
    public int getPageSize() {
        return pageable.getPageSize();
    }
}
