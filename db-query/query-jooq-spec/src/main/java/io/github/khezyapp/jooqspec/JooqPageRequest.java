package io.github.khezyapp.jooqspec;

import java.util.ArrayList;
import java.util.List;

/**
 * A pagination request record for jOOQ queries containing offset, limit, and sorting metadata.
 *
 * @param pageNumber the zero-based page index
 * @param pageSize   the number of records per page
 * @param sort       the sorting configuration
 */
public record JooqPageRequest(
        int pageNumber,
        int pageSize,
        JooqSort sort
) {

    /**
     * Compact constructor to validate pagination boundaries.
     * @throws IllegalArgumentException if pageNumber is negative or pageSize is less than one
     */
    public JooqPageRequest {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("Page index must not be less than zero");
        }

        if (pageSize < 1) {
            throw new IllegalArgumentException("Page size must not be less than one");
        }
    }

    /**
     * Fluent builder for constructing {@link JooqPageRequest} instances with complex sorting requirements.
     */
    public static class Builder {
        private int pageNumber;
        private int pageSize;
        private List<String> sortFields;
        private List<String> sortDirections;

        /**
         * Sets the zero-based page index.
         *
         * @param pageNumber the page number
         * @return the builder instance
         */
        public Builder pageNumber(final int pageNumber) {
            this.pageNumber = pageNumber;
            return this;
        }

        /**
         * Sets the number of items per page.
         *
         * @param pageSize the page size
         * @return the builder instance
         */
        public Builder pageSize(final int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        /**
         * Sets the field names to sort by.
         *
         * @param sortFields a list of field identifiers
         * @return the builder instance
         */
        public Builder sortFields(final List<String> sortFields) {
            this.sortFields = sortFields;
            return this;
        }

        /**
         * Sets the directions corresponding to the sort fields.
         *
         * @param sortDirections a list of direction strings (e.g., "ASC", "DESC")
         * @return the builder instance
         */
        public Builder sortDirections(final List<String> sortDirections) {
            this.sortDirections = sortDirections;
            return this;
        }

        /**
         * Builds a new {@link JooqPageRequest} and initializes a {@link JooqSort} from the provided fields
         * and directions.
         *
         * @return the constructed pagination request
         */
        public JooqPageRequest build() {
            return new JooqPageRequest(pageNumber, pageSize, new JooqSort(buildOrders()));
        }

        /**
         * Maps sort fields and directions into a list of {@link JooqOrder} instances, defaulting missing
         * directions to ASC.
         *
         * @return the list of order definitions
         */
        private List<JooqOrder> buildOrders() {
            final var directions = new ArrayList<String>(sortFields.size());
            // if missing, we assume sort by ASC order
            for (var i = 0; i < sortFields.size(); i++) {
                if (i >= sortDirections.size()) {
                    directions.add("ASC");
                } else {
                    directions.add(sortDirections.get(i));
                }
            }

            final var orders = new ArrayList<JooqOrder>(directions.size());
            for (var i = 0; i < directions.size(); i++) {
                orders.add(
                        new JooqOrder(
                                sortFields.get(i),
                                JooqOrder.Direction.of(directions.get(i))
                        )
                );
            }
            return orders;
        }
    }
}
