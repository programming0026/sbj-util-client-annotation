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
@Tag(name = "Productos", description = "Consulta y modificación del catálogo de productos")
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
    @Operation(summary = "Crear un producto",
            description = "`title` y `price` son obligatorios. DummyJSON simula la creación: responde con un "
                    + "id nuevo pero no guarda nada.")
    @ApiResponse(responseCode = "201", description = "El producto creado")
    @ApiResponse(responseCode = "400", description = "Faltan campos o son inválidos")
    @ApiResponse(responseCode = "503", description = "DummyJSON no está disponible")
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
    @Operation(summary = "Modificar algunos campos de un producto",
            description = "Solo se cambian los campos que se envían, y se requiere al menos uno.")
    @ApiResponse(responseCode = "200", description = "El producto modificado")
    @ApiResponse(responseCode = "400", description = "No hay campos que cambiar o los valores son inválidos")
    @ApiResponse(responseCode = "404", description = "El producto no existe")
    @ApiResponse(responseCode = "503", description = "DummyJSON no está disponible")
    public ProductResponse update(@Parameter(description = "Id del producto", example = "1") @PathVariable long id,
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
    @Operation(summary = "Eliminar un producto", description = "DummyJSON simula la eliminación.")
    @ApiResponse(responseCode = "200", description = "El producto eliminado")
    @ApiResponse(responseCode = "404", description = "El producto no existe")
    @ApiResponse(responseCode = "503", description = "DummyJSON no está disponible")
    public ProductResponse delete(@Parameter(description = "Id del producto", example = "1") @PathVariable long id) {
        return ProductResponse.from(deleteProduct.delete(id));
    }
}
