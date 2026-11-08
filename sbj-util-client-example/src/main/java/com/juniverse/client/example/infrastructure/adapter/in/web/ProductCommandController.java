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

/** Adaptador de entrada: operaciones que modifican el catálogo. */
@RestController
@RequestMapping("/api/products")
@Tag(name = "Productos", description = "Consulta y modificación del catálogo de productos")
public class ProductCommandController {

    private final CreateProductUseCase createProduct;
    private final UpdateProductUseCase updateProduct;
    private final DeleteProductUseCase deleteProduct;

    /**
     * Crea el controlador.
     *
     * @param createProduct caso de uso de creación
     * @param updateProduct caso de uso de modificación
     * @param deleteProduct caso de uso de eliminación
     */
    public ProductCommandController(CreateProductUseCase createProduct, UpdateProductUseCase updateProduct,
                                    DeleteProductUseCase deleteProduct) {
        this.createProduct = createProduct;
        this.updateProduct = updateProduct;
        this.deleteProduct = deleteProduct;
    }

    /**
     * Crea un producto.
     *
     * @param request los datos del producto
     * @return el producto creado
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
     * Modifica parcialmente un producto.
     *
     * @param id el identificador
     * @param request los campos a cambiar
     * @return el producto actualizado
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
     * Elimina un producto.
     *
     * @param id el identificador
     * @return el producto eliminado
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
