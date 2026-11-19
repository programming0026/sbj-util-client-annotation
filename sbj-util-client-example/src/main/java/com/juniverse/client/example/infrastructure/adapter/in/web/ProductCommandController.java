package com.juniverse.client.example.infrastructure.adapter.in.web;

import com.juniverse.client.example.application.port.in.CreateProductUseCase;
import com.juniverse.client.example.application.port.in.DeleteProductUseCase;
import com.juniverse.client.example.application.port.in.UpdateProductUseCase;
import com.juniverse.client.example.infrastructure.adapter.in.web.dto.ProductRequest;
import com.juniverse.client.example.infrastructure.adapter.in.web.dto.ProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Input adapter: operations that modify the catalog.
 *
 * @author Java Universe
 */
@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Query and modification of the product catalog")
public class ProductCommandController {

    private final CreateProductUseCase createProduct;
    private final UpdateProductUseCase updateProduct;
    private final DeleteProductUseCase deleteProduct;

    /**
     * Creates the controller.
     *
     * @param createProduct creation use case
     * @param updateProduct modification use case
     * @param deleteProduct deletion use case
     */
    public ProductCommandController(CreateProductUseCase createProduct, UpdateProductUseCase updateProduct,
                                    DeleteProductUseCase deleteProduct) {
        this.createProduct = createProduct;
        this.updateProduct = updateProduct;
        this.deleteProduct = deleteProduct;
    }

    /**
     * Creates a product.
     *
     * @param request the product data
     * @return the created product
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a product",
            description = "`title` and `price` are required. DummyJSON simulates the creation: it responds with a "
                    + "new id but stores nothing.")
    @ApiResponse(responseCode = "201", description = "The created product")
    @ApiResponse(responseCode = "400", description = "Fields are missing or invalid")
    @ApiResponse(responseCode = "503", description = "DummyJSON is unavailable")
    public ProductResponse create(@RequestBody ProductRequest request) {
        return ProductResponse.from(createProduct.create(request.toNewProduct()));
    }

    /**
     * Partially modifies a product.
     *
     * @param id the identifier
     * @param request the fields to change
     * @return the updated product
     */
    @PatchMapping("/{id}")
    @Operation(summary = "Modify some fields of a product",
            description = "Only the fields that are sent are changed, and at least one is required.")
    @ApiResponse(responseCode = "200", description = "The modified product")
    @ApiResponse(responseCode = "400", description = "There are no fields to change or the values are invalid")
    @ApiResponse(responseCode = "404", description = "The product does not exist")
    @ApiResponse(responseCode = "503", description = "DummyJSON is unavailable")
    public ProductResponse update(@Parameter(description = "Product id", example = "1") @PathVariable long id,
                                  @RequestBody ProductRequest request) {
        return ProductResponse.from(updateProduct.update(id, request.toChanges()));
    }

    /**
     * Deletes a product.
     *
     * @param id the identifier
     * @return the deleted product
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product", description = "DummyJSON simulates the deletion.")
    @ApiResponse(responseCode = "200", description = "The deleted product")
    @ApiResponse(responseCode = "404", description = "The product does not exist")
    @ApiResponse(responseCode = "503", description = "DummyJSON is unavailable")
    public ProductResponse delete(@Parameter(description = "Product id", example = "1") @PathVariable long id) {
        return ProductResponse.from(deleteProduct.delete(id));
    }
}
