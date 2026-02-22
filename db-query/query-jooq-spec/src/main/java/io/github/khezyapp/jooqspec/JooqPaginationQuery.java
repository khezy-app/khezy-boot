package io.github.khezyapp.jooqspec;

/**
 * Container record that aggregates a jOOQ specification and pagination details into a single query object.
 * <p>
 * This class serves as the primary input for data access layers, providing all necessary
 * components to build a filtered, paged, and sorted jOOQ query.
 * </p>
 *
 * @param specification the filtering, grouping, and having logic
 * @param pageable      the pagination and sorting parameters
 */
public record JooqPaginationQuery(
        JooqSpecification specification,
        JooqPageRequest pageable
) {

    /**
     * Gets the zero-based page index from the underlying page request.
     * @return the page number
     */
    public int getPageNumber() {
        return pageable.pageNumber();
    }

    /**
     * Gets the number of records per page.
     * @return the page size
     */
    public int getPageSize() {
        return pageable.pageSize();
    }

    /**
     * Calculates the SQL offset based on the current page number and page size.
     * @return the calculated record offset
     */
    public int getOffset() {
        return getPageNumber() * getPageSize();
    }

    /**
     * Gets the sorting configuration from the underlying page request.
     * @return the sort definition
     */
    public JooqSort getSort() {
        return pageable.sort();
    }
}
