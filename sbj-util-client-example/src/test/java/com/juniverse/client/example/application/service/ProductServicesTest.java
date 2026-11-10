package com.juniverse.client.example.application.service;

import com.juniverse.client.example.application.port.out.ProductReader;
import com.juniverse.client.example.application.port.out.ProductWriter;
import com.juniverse.client.example.domain.exception.ProductNotFoundException;
import com.juniverse.client.example.domain.model.NewProduct;
import com.juniverse.client.example.domain.model.Product;
import com.juniverse.client.example.domain.model.ProductCategory;
import com.juniverse.client.example.domain.model.ProductChanges;
import com.juniverse.client.example.domain.model.ProductCriteria;
import com.juniverse.client.example.domain.model.ProductPage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los servicios se prueban solo con dobles de los puertos de salida: no hace falta Spring ni red.
 *
 * @author Java Universe
 */
class ProductServicesTest {

    private static final Product PRODUCT =
            new Product(7, "Lamp", null, "home", BigDecimal.TEN, 0, 0, 1, null, null, null, null, null);

    private final ProductReader reader = mock(ProductReader.class);
    private final ProductWriter writer = mock(ProductWriter.class);
    private final ProductQueryService queries = new ProductQueryService(reader);
    private final ProductCommandService commands = new ProductCommandService(writer);

    @Test
    void listDelegatesToTheReaderUsingDefaultCriteriaWhenNull() {
        ProductPage page = new ProductPage(List.of(PRODUCT), 1, 0, 30);
        when(reader.findAll(ProductCriteria.all())).thenReturn(page);

        assertThat(queries.list(null)).isSameAs(page);
        assertThat(queries.list(ProductCriteria.all())).isSameAs(page);
    }

    @Test
    void getByIdReturnsTheProductOrFailsWithNotFound() {
        when(reader.findById(7)).thenReturn(Optional.of(PRODUCT));
        when(reader.findById(8)).thenReturn(Optional.empty());

        assertThat(queries.getById(7)).isSameAs(PRODUCT);
        assertThatThrownBy(() -> queries.getById(8))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product with id 8 not found");
    }

    @Test
    void listCategoriesDelegatesToTheReader() {
        List<ProductCategory> categories = List.of(new ProductCategory("beauty", "Beauty"));
        when(reader.findCategories()).thenReturn(categories);

        assertThat(queries.listCategories()).isEqualTo(categories);
    }

    @Test
    void createDelegatesToTheWriter() {
        NewProduct newProduct = new NewProduct("Lamp", null, null, BigDecimal.TEN, null, null);
        when(writer.create(newProduct)).thenReturn(PRODUCT);

        assertThat(commands.create(newProduct)).isSameAs(PRODUCT);
    }

    @Test
    void updateAndDeleteFailWithNotFoundWhenTheProductDoesNotExist() {
        ProductChanges changes = new ProductChanges("New", null, null, null, null, null);
        when(writer.update(9, changes)).thenReturn(Optional.empty());
        when(writer.delete(9)).thenReturn(Optional.empty());
        when(writer.update(7, changes)).thenReturn(Optional.of(PRODUCT));
        when(writer.delete(7)).thenReturn(Optional.of(PRODUCT));

        assertThat(commands.update(7, changes)).isSameAs(PRODUCT);
        assertThat(commands.delete(7)).isSameAs(PRODUCT);
        assertThatThrownBy(() -> commands.update(9, changes)).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> commands.delete(9)).isInstanceOf(ProductNotFoundException.class);
        verify(writer).delete(9);
    }

    @Test
    void servicesRejectMissingCollaborators() {
        assertThatThrownBy(() -> new ProductQueryService(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProductCommandService(null)).isInstanceOf(NullPointerException.class);
    }
}
