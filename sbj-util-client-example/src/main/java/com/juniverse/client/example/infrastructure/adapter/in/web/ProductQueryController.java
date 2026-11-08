package com.juniverse.client.example.infrastructure.adapter.in.web;

import com.juniverse.client.example.application.port.in.GetProductUseCase;
import com.juniverse.client.example.application.port.in.ListCategoriesUseCase;
import com.juniverse.client.example.application.port.in.ListProductsUseCase;
import com.juniverse.client.example.domain.model.PageRequest;
import com.juniverse.client.example.domain.model.ProductCriteria;
import com.juniverse.client.example.domain.model.SortOrder;
import com.juniverse.client.example.infrastructure.adapter.in.web.dto.CategoryResponse;
import com.juniverse.client.example.infrastructure.adapter.in.web.dto.ProductPageResponse;
import com.juniverse.client.example.infrastructure.adapter.in.web.dto.ProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Adaptador de entrada: consultas del catálogo. Solo conoce los puertos de entrada (casos de uso), nunca
 * los clientes de DummyJSON.
 */
@RestController
@RequestMapping("/api/products")
@Tag(name = "Productos", description = "Consulta y modificación del catálogo de productos")
public class ProductQueryController {

    private final ListProductsUseCase listProducts;
    private final GetProductUseCase getProduct;
    private final ListCategoriesUseCase listCategories;

    /**
     * Crea el controlador.
     *
     * @param listProducts caso de uso de listado
     * @param getProduct caso de uso de consulta por identificador
     * @param listCategories caso de uso de categorías
     */
    public ProductQueryController(ListProductsUseCase listProducts, GetProductUseCase getProduct,
                                  ListCategoriesUseCase listCategories) {
        this.listProducts = listProducts;
        this.getProduct = getProduct;
        this.listCategories = listCategories;
    }

    /**
     * Lista productos, con búsqueda por texto o filtro por categoría (no ambos), paginación y orden.
     *
     * @param limit tamaño de página
     * @param skip elementos a omitir
     * @param q texto a buscar
     * @param category categoría (slug)
     * @param sortBy campo de orden
     * @param order {@code asc} o {@code desc}
     * @return la página de productos
     */
    @GetMapping
    @Operation(summary = "Listar, buscar o filtrar productos",
            description = "Devuelve una página de productos. Usa `q` para buscar por texto o `category` para filtrar por "
                    + "categoría (no se pueden combinar), y `sortBy`/`order` para ordenar.")
    @ApiResponse(responseCode = "200", description = "Una página de productos")
    @ApiResponse(responseCode = "400", description = "Paginación, orden o combinación de filtros inválidos")
    @ApiResponse(responseCode = "503", description = "DummyJSON no está disponible")
    public ProductPageResponse list(
            @Parameter(description = "Tamaño de página (de 1 a 100)", example = "30")
            @RequestParam(defaultValue = "30") int limit,
            @Parameter(description = "Elementos a omitir antes de la página", example = "0")
            @RequestParam(defaultValue = "0") int skip,
            @Parameter(description = "Texto a buscar", example = "phone")
            @RequestParam(required = false) String q,
            @Parameter(description = "Slug de la categoría; ver GET /api/products/categories", example = "laptops")
            @RequestParam(required = false) String category,
            @Parameter(description = "Campo de orden: id, title, price, rating, stock, brand, category o "
                    + "discountPercentage", example = "price")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sentido del orden: asc (por defecto) o desc", example = "desc")
            @RequestParam(required = false) String order) {
        ProductCriteria criteria = new ProductCriteria(new PageRequest(limit, skip), q, category, sortBy,
                SortOrder.from(order));
        return ProductPageResponse.from(listProducts.list(criteria));
    }

    /**
     * Lista las categorías.
     *
     * @return las categorías
     */
    @GetMapping("/categories")
    @Operation(summary = "Listar las categorías de productos")
    @ApiResponse(responseCode = "200", description = "Todas las categorías")
    @ApiResponse(responseCode = "503", description = "DummyJSON no está disponible")
    public List<CategoryResponse> categories() {
        return listCategories.listCategories().stream().map(CategoryResponse::from).toList();
    }

    /**
     * Consulta un producto.
     *
     * @param id el identificador
     * @return el producto
     */
    @GetMapping("/{id}")
    @Operation(summary = "Consultar un producto por su id")
    @ApiResponse(responseCode = "200", description = "El producto")
    @ApiResponse(responseCode = "400", description = "El id no es un número")
    @ApiResponse(responseCode = "404", description = "El producto no existe")
    @ApiResponse(responseCode = "503", description = "DummyJSON no está disponible")
    public ProductResponse get(@Parameter(description = "Id del producto", example = "1") @PathVariable long id) {
        return ProductResponse.from(getProduct.getById(id));
    }
}
