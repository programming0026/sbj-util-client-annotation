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
 * Declarative client of https://dummyjson.com/docs/products.
 *
 * It is enough to declare the interface: the library generates the client, resolves {@code ${dummyjson.base-url}}
 * from {@code application.properties} and applies retries, circuit breaker and time limit. GET requests are
 * retried on transient failures; the create POST only on connection failures (it is not idempotent).
 *
 * @author Java Universe
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
     * Lists products.
     *
     * @param limit page size
     * @param skip elements to skip
     * @param sortBy sort field, or null
     * @param order {@code asc} or {@code desc}, or null
     * @return the page of products
     */
    @GetMapping
    ProductListDto list(@RequestParam("limit") int limit, @RequestParam("skip") int skip,
                        @RequestParam(name = "sortBy", required = false) String sortBy,
                        @RequestParam(name = "order", required = false) String order);

    /**
     * Searches products by text.
     *
     * @param query the text to search
     * @param limit page size
     * @param skip elements to skip
     * @param sortBy sort field, or null
     * @param order {@code asc} or {@code desc}, or null
     * @return the page of products
     */
    @GetMapping("/search")
    ProductListDto search(@RequestParam("q") String query, @RequestParam("limit") int limit,
                          @RequestParam("skip") int skip,
                          @RequestParam(name = "sortBy", required = false) String sortBy,
                          @RequestParam(name = "order", required = false) String order);

    /**
     * Lists the products of a category.
     *
     * @param slug the category
     * @param limit page size
     * @param skip elements to skip
     * @param sortBy sort field, or null
     * @param order {@code asc} or {@code desc}, or null
     * @return the page of products
     */
    @GetMapping("/category/{slug}")
    ProductListDto byCategory(@PathVariable("slug") String slug, @RequestParam("limit") int limit,
                              @RequestParam("skip") int skip,
                              @RequestParam(name = "sortBy", required = false) String sortBy,
                              @RequestParam(name = "order", required = false) String order);

    /**
     * Lists the categories.
     *
     * @return the categories
     */
    @GetMapping("/categories")
    List<CategoryDto> categories();

    /**
     * Gets a product.
     *
     * @param id the identifier
     * @return the product
     */
    @GetMapping("/{id}")
    ProductDto get(@PathVariable("id") long id);

    /**
     * Creates a product (simulated: the service does not store it).
     *
     * @param payload the product data
     * @return the created product
     */
    @PostMapping("/add")
    ProductDto add(@RequestBody ProductPayloadDto payload);

    /**
     * Modifies a product (simulated).
     *
     * @param id the identifier
     * @param payload the fields to change
     * @return the modified product
     */
    @PatchMapping("/{id}")
    ProductDto update(@PathVariable("id") long id, @RequestBody ProductPayloadDto payload);

    /**
     * Deletes a product (simulated).
     *
     * @param id the identifier
     * @return the deleted product
     */
    @DeleteMapping("/{id}")
    ProductDto delete(@PathVariable("id") long id);
}
