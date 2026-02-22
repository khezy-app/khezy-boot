package io.github.khezyapp.jooqspec;

import org.jooq.SortField;

import java.util.List;

/**
 * A collection of {@link JooqOrder} instances defining the sorting criteria for a jOOQ query.
 *
 * @param orders the list of individual field-level sort definitions
 */
public record JooqSort(List<JooqOrder> orders) {

    /**
     * Maps the internal order definitions to a list of jOOQ-native {@link SortField} objects.
     * @return a list of sort fields ready for use in a jOOQ {@code orderBy} clause
     */
    public List<SortField<Object>> sortFields() {
        return orders.stream()
                .map(JooqOrder::getSortField)
                .toList();
    }
}
