package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductCriteriaTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void pageLimitMustBeBetweenOneAndMax(int limit) {
        assertThatThrownBy(() -> new PageRequest(limit, 0))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("limit must be between 1 and 100");
    }

    @Test
    void pageSkipMustNotBeNegative() {
        assertThatThrownBy(() -> new PageRequest(10, -1))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("skip must not be negative");
    }

    @Test
    void defaultCriteriaUseFirstPageWithoutFilters() {
        ProductCriteria criteria = ProductCriteria.all();

        assertThat(criteria.page()).isEqualTo(PageRequest.first());
        assertThat(criteria.isSearch()).isFalse();
        assertThat(criteria.isByCategory()).isFalse();
        assertThat(criteria.sortBy()).isNull();
        assertThat(criteria.order()).isEqualTo(SortOrder.ASC);
    }

    @Test
    void blankValuesAreTreatedAsAbsent() {
        ProductCriteria criteria = new ProductCriteria(null, "  ", "", " ", null);

        assertThat(criteria.isSearch()).isFalse();
        assertThat(criteria.isByCategory()).isFalse();
        assertThat(criteria.sortBy()).isNull();
    }

    @Test
    void searchAndCategoryCannotBeCombined() {
        assertThatThrownBy(() -> new ProductCriteria(null, "phone", "laptops", null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("searchText and category cannot be combined");
    }

    @Test
    void sortFieldMustBeKnown() {
        assertThatThrownBy(() -> new ProductCriteria(null, null, null, "color", null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("sortBy must be one of")
                .hasMessageContaining("'color'");
        assertThat(new ProductCriteria(null, null, null, "price", SortOrder.DESC).sortBy()).isEqualTo("price");
    }

    @Test
    void sortOrderIsCaseInsensitiveAndDefaultsToAscending() {
        assertThat(SortOrder.from("DESC")).isEqualTo(SortOrder.DESC);
        assertThat(SortOrder.from(" asc ")).isEqualTo(SortOrder.ASC);
        assertThat(SortOrder.from(null)).isEqualTo(SortOrder.ASC);
        assertThat(SortOrder.DESC.lowerCase()).isEqualTo("desc");
        assertThatThrownBy(() -> SortOrder.from("up"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("order must be 'asc' or 'desc' but was 'up'");
    }
}
