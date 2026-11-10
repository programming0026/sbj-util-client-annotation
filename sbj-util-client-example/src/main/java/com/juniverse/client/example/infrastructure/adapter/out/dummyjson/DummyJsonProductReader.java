package com.juniverse.client.example.infrastructure.adapter.out.dummyjson;

import com.juniverse.client.example.application.port.out.ProductReader;
import com.juniverse.client.example.domain.model.PageRequest;
import com.juniverse.client.example.domain.model.Product;
import com.juniverse.client.example.domain.model.ProductCategory;
import com.juniverse.client.example.domain.model.ProductCriteria;
import com.juniverse.client.example.domain.model.ProductPage;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.api.ProductsApi;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.ProductListDto;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador de salida: implementa la lectura del catálogo con el cliente declarativo {@link ProductsApi}.
 *
 * @author Java Universe
 */
@Component
public class DummyJsonProductReader implements ProductReader {

    private final ProductsApi productsApi;
    private final DummyJsonMapper mapper;
    private final DummyJsonErrorTranslator errorTranslator;

    /**
     * Crea el adaptador.
     *
     * @param productsApi el cliente declarativo generado por la librería
     * @param mapper el conversor entre DTOs y dominio
     * @param errorTranslator el traductor de errores técnicos
     */
    public DummyJsonProductReader(ProductsApi productsApi, DummyJsonMapper mapper,
                                  DummyJsonErrorTranslator errorTranslator) {
        this.productsApi = productsApi;
        this.mapper = mapper;
        this.errorTranslator = errorTranslator;
    }

    @Override
    public ProductPage findAll(ProductCriteria criteria) {
        try {
            PageRequest page = criteria.page();
            String sortBy = criteria.sortBy();
            String order = sortBy == null ? null : criteria.order().lowerCase();
            ProductListDto result;
            if (criteria.isSearch()) {
                result = productsApi.search(criteria.searchText(), page.limit(), page.skip(), sortBy, order);
            } else if (criteria.isByCategory()) {
                result = productsApi.byCategory(criteria.category(), page.limit(), page.skip(), sortBy, order);
            } else {
                result = productsApi.list(page.limit(), page.skip(), sortBy, order);
            }
            return mapper.toDomain(result);
        } catch (RuntimeException e) {
            throw errorTranslator.translate("list products", e);
        }
    }

    @Override
    public Optional<Product> findById(long id) {
        try {
            return Optional.of(mapper.toDomain(productsApi.get(id)));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw errorTranslator.translate("get product " + id, e);
        }
    }

    @Override
    public List<ProductCategory> findCategories() {
        try {
            return productsApi.categories().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw errorTranslator.translate("list categories", e);
        }
    }
}
