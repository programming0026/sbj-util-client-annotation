package com.juniverse.client.example.infrastructure.adapter.out.dummyjson.api;

import io.github.programming0026.juniverse.client.autoconfigure.CircuitBreakerConfig;
import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import io.github.programming0026.juniverse.client.autoconfigure.TimeLimiterConfig;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.LoggingInterceptor;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.CategoryDto;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.ProductDto;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.ProductListDto;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.ProductPayloadDto;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Cliente declarativo de https://dummyjson.com/docs/products.
 *
 * Basta declarar la interfaz: la librería genera el cliente, resuelve {@code ${dummyjson.base-url}} desde
 * {@code application.properties} y aplica reintentos, circuit breaker y límite de tiempo. Los GET se
 * reintentan ante fallas transitorias; el POST de crear solo ante fallas de conexión (no es idempotente).
 */
@RestHttpClient(
        name = "dummyJsonProducts",
        url = "${dummyjson.base-url}/products",
        connectTimeout = 3000,
        readTimeout = 10000,
        retry = @RetryConfig(maxRetries = 3, retryDelay = 300),
        circuitBreaker = @CircuitBreakerConfig(enabled = true, slidingWindowSize = 20, minimumNumberOfCalls = 10,
                failureRateThreshold = 50, waitDurationInOpenState = 30000),
        timeLimiter = @TimeLimiterConfig(enabled = true, timeout = 15000),
        interceptors = LoggingInterceptor.class)
public interface ProductsApi {

    /**
     * Lista productos.
     *
     * @param limit tamaño de página
     * @param skip elementos a omitir
     * @param sortBy campo de orden, o null
     * @param order {@code asc} o {@code desc}, o null
     * @return la página de productos
     */
    @GetMapping
    ProductListDto list(@RequestParam("limit") int limit, @RequestParam("skip") int skip,
                        @RequestParam(name = "sortBy", required = false) String sortBy,
                        @RequestParam(name = "order", required = false) String order);

    /**
     * Busca productos por texto.
     *
     * @param query el texto a buscar
     * @param limit tamaño de página
     * @param skip elementos a omitir
     * @param sortBy campo de orden, o null
     * @param order {@code asc} o {@code desc}, o null
     * @return la página de productos
     */
    @GetMapping("/search")
    ProductListDto search(@RequestParam("q") String query, @RequestParam("limit") int limit,
                          @RequestParam("skip") int skip,
                          @RequestParam(name = "sortBy", required = false) String sortBy,
                          @RequestParam(name = "order", required = false) String order);

    /**
     * Lista los productos de una categoría.
     *
     * @param slug la categoría
     * @param limit tamaño de página
     * @param skip elementos a omitir
     * @param sortBy campo de orden, o null
     * @param order {@code asc} o {@code desc}, o null
     * @return la página de productos
     */
    @GetMapping("/category/{slug}")
    ProductListDto byCategory(@PathVariable("slug") String slug, @RequestParam("limit") int limit,
                              @RequestParam("skip") int skip,
                              @RequestParam(name = "sortBy", required = false) String sortBy,
                              @RequestParam(name = "order", required = false) String order);

    /**
     * Lista las categorías.
     *
     * @return las categorías
     */
    @GetMapping("/categories")
    List<CategoryDto> categories();

    /**
     * Obtiene un producto.
     *
     * @param id el identificador
     * @return el producto
     */
    @GetMapping("/{id}")
    ProductDto get(@PathVariable("id") long id);

    /**
     * Crea un producto (simulado: el servicio no lo guarda).
     *
     * @param payload los datos del producto
     * @return el producto creado
     */
    @PostMapping("/add")
    ProductDto add(@RequestBody ProductPayloadDto payload);

    /**
     * Modifica un producto (simulado).
     *
     * @param id el identificador
     * @param payload los campos a cambiar
     * @return el producto modificado
     */
    @PatchMapping("/{id}")
    ProductDto update(@PathVariable("id") long id, @RequestBody ProductPayloadDto payload);

    /**
     * Elimina un producto (simulado).
     *
     * @param id el identificador
     * @return el producto eliminado
     */
    @DeleteMapping("/{id}")
    ProductDto delete(@PathVariable("id") long id);
}
