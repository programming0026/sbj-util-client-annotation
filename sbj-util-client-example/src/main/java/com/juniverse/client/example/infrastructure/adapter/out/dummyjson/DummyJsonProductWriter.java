package com.juniverse.client.example.infrastructure.adapter.out.dummyjson;

import com.juniverse.client.example.application.port.out.ProductWriter;
import com.juniverse.client.example.domain.model.NewProduct;
import com.juniverse.client.example.domain.model.Product;
import com.juniverse.client.example.domain.model.ProductChanges;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.api.ProductsApi;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Optional;

/**
 * Adaptador de salida: implementa la escritura del catálogo con el cliente declarativo {@link ProductsApi}.
 *
 * DummyJSON simula estas operaciones (no persiste nada), pero responde igual que lo haría un servicio real.
 */
@Component
public class DummyJsonProductWriter implements ProductWriter {

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
    public DummyJsonProductWriter(ProductsApi productsApi, DummyJsonMapper mapper,
                                  DummyJsonErrorTranslator errorTranslator) {
        this.productsApi = productsApi;
        this.mapper = mapper;
        this.errorTranslator = errorTranslator;
    }

    @Override
    public Product create(NewProduct newProduct) {
        try {
            return mapper.toDomain(productsApi.add(mapper.toPayload(newProduct)));
        } catch (RuntimeException e) {
            throw errorTranslator.translate("create a product", e);
        }
    }

    @Override
    public Optional<Product> update(long id, ProductChanges changes) {
        try {
            return Optional.of(mapper.toDomain(productsApi.update(id, mapper.toPayload(changes))));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw errorTranslator.translate("update product " + id, e);
        }
    }

    @Override
    public Optional<Product> delete(long id) {
        try {
            return Optional.of(mapper.toDomain(productsApi.delete(id)));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw errorTranslator.translate("delete product " + id, e);
        }
    }
}
