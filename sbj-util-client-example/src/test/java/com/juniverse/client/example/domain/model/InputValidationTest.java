package com.juniverse.client.example.domain.model;

import com.juniverse.client.example.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputValidationTest {

    @Test
    void newProductRequiresTitleAndNonNegativePrice() {
        assertThatThrownBy(() -> new NewProduct(" ", null, null, BigDecimal.ONE, null, null))
                .isInstanceOf(InvalidRequestException.class).hasMessage("title is required");
        assertThatThrownBy(() -> new NewProduct("Lamp", null, null, null, null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("price is required and must not be negative");
        assertThatThrownBy(() -> new NewProduct("Lamp", null, null, new BigDecimal("-1"), null, null))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> new NewProduct("Lamp", null, null, BigDecimal.TEN, -3, null))
                .isInstanceOf(InvalidRequestException.class).hasMessage("stock must not be negative");
        assertThat(new NewProduct("  Lamp ", null, null, BigDecimal.ZERO, 0, null).title()).isEqualTo("Lamp");
    }

    @Test
    void productChangesNeedAtLeastOneValidField() {
        assertThatThrownBy(() -> new ProductChanges(null, null, null, null, null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("at least one field to change is required");
        assertThatThrownBy(() -> new ProductChanges(" ", null, null, null, null, null))
                .isInstanceOf(InvalidRequestException.class).hasMessage("title must not be blank");
        assertThatThrownBy(() -> new ProductChanges(null, null, null, new BigDecimal("-0.01"), null, null))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(new ProductChanges(null, null, null, null, 5, null).stock()).isEqualTo(5);
    }

    @Test
    void credentialsRequireBothValuesAndHidePasswordInToString() {
        assertThatThrownBy(() -> new Credentials(" ", "secret"))
                .isInstanceOf(InvalidRequestException.class).hasMessage("username is required");
        assertThatThrownBy(() -> new Credentials("emilys", ""))
                .isInstanceOf(InvalidRequestException.class).hasMessage("password is required");

        Credentials credentials = new Credentials(" emilys ", "secret");

        assertThat(credentials.username()).isEqualTo("emilys");
        assertThat(credentials.toString()).doesNotContain("secret").contains("emilys");
    }

    @Test
    void tokensAreNeverPrinted() {
        assertThat(new AuthTokens("access-123", "refresh-456").toString())
                .doesNotContain("access-123").doesNotContain("refresh-456");
    }

    @Test
    void pageKnowsWhetherThereAreMoreItems() {
        Product product = new Product(1, "A", null, null, BigDecimal.ONE, 0, 0, 0, null, null, null, null, null);

        assertThat(new ProductPage(List.of(product), 5, 0, 1).hasNext()).isTrue();
        assertThat(new ProductPage(List.of(product), 1, 0, 1).hasNext()).isFalse();
        assertThat(new ProductPage(List.of(product), 5, 4, 1).hasNext()).isFalse();
        assertThat(product.tags()).isEmpty();
    }
}
