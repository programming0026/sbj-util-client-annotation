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
 * Input adapter: catalog queries. It only knows the input ports (use cases), never
 * the DummyJSON clients.
 *
 * @author Java Universe
 */
@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Query and modification of the product catalog")
public class ProductQueryController {

    private final ListProductsUseCase listProducts;
    private final GetProductUseCase getProduct;
    private final ListCategoriesUseCase listCategories;

    /**
     * Creates the controller.
     *
     * @param listProducts listing use case
     * @param getProduct query by identifier use case
     * @param listCategories categories use case
     */
    public ProductQueryController(ListProductsUseCase listProducts, GetProductUseCase getProduct,
                                  ListCategoriesUseCase listCategories) {
        this.listProducts = listProducts;
        this.getProduct = getProduct;
        this.listCategories = listCategories;
    }

    /**
     * Lists products, with text search or category filter (not both), pagination and sorting.
     *
     * @param limit page size
     * @param skip elements to skip
     * @param q text to search
     * @param category category (slug)
     * @param sortBy sort field
     * @param order {@code asc} or {@code desc}
     * @return the page of products
     */
    @GetMapping
    @Operation(summary = "List, search or filter products",
            description = "Returns a page of products. Use `q` to search by text or `category` to filter by "
                    + "category (they cannot be combined), and `sortBy`/`order` to sort.")
    @ApiResponse(responseCode = "200", description = "A page of products")
    @ApiResponse(responseCode = "400", description = "Invalid pagination, sorting or filter combination")
    @ApiResponse(responseCode = "503", description = "DummyJSON is unavailable")
    public ProductPageResponse list(
            @Parameter(description = "Page size (1 to 100)", example = "30")
            @RequestParam(defaultValue = "30") int limit,
            @Parameter(description = "Elements to skip before the page", example = "0")
            @RequestParam(defaultValue = "0") int skip,
            @Parameter(description = "Text to search", example = "phone")
            @RequestParam(required = false) String q,
            @Parameter(description = "Category slug; see GET /api/products/categories", example = "laptops")
            @RequestParam(required = false) String category,
            @Parameter(description = "Sort field: id, title, price, rating, stock, brand, category or "
                    + "discountPercentage", example = "price")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction: asc (default) or desc", example = "desc")
            @RequestParam(required = false) String order) {
        ProductCriteria criteria = new ProductCriteria(new PageRequest(limit, skip), q, category, sortBy,
                SortOrder.from(order));
        return ProductPageResponse.from(listProducts.list(criteria));
    }

    /**
     * Lists the categories.
     *
     * @return the categories
     */
    @GetMapping("/categories")
    @Operation(summary = "List the product categories")
    @ApiResponse(responseCode = "200", description = "All the categories")
    @ApiResponse(responseCode = "503", description = "DummyJSON is unavailable")
    public List<CategoryResponse> categories() {
        return listCategories.listCategories().stream().map(CategoryResponse::from).toList();
    }

    /**
     * Gets a product.
     *
     * @param id the identifier
     * @return the product
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a product by its id")
    @ApiResponse(responseCode = "200", description = "The product")
    @ApiResponse(responseCode = "400", description = "The id is not a number")
    @ApiResponse(responseCode = "404", description = "The product does not exist")
    @ApiResponse(responseCode = "503", description = "DummyJSON is unavailable")
    public ProductResponse get(@Parameter(description = "Product id", example = "1") @PathVariable long id) {
        return ProductResponse.from(getProduct.getById(id));
    }
}
