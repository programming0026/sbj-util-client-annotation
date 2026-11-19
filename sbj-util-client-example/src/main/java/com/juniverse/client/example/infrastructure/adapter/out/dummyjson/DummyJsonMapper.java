package com.juniverse.client.example.infrastructure.adapter.out.dummyjson;

import com.juniverse.client.example.domain.model.AuthSession;
import com.juniverse.client.example.domain.model.AuthTokens;
import com.juniverse.client.example.domain.model.NewProduct;
import com.juniverse.client.example.domain.model.Product;
import com.juniverse.client.example.domain.model.ProductCategory;
import com.juniverse.client.example.domain.model.ProductChanges;
import com.juniverse.client.example.domain.model.ProductPage;
import com.juniverse.client.example.domain.model.UserProfile;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.CategoryDto;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.LoginResponseDto;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.ProductDto;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.ProductListDto;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.ProductPayloadDto;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.RefreshResponseDto;
import com.juniverse.client.example.infrastructure.adapter.out.dummyjson.dto.UserDto;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts between the DummyJSON DTOs and the domain model.
 *
 * It is the only class that knows both sides: this way the domain does not depend on the shape of the external
 * API and a change in that API only affects this package.
 *
 * @author Java Universe
 */
@Component
public class DummyJsonMapper {

    /**
     * Converts a product.
     *
     * @param dto the DTO
     * @return the domain product
     */
    public Product toDomain(ProductDto dto) {
        return new Product(orZero(dto.id()), dto.title(), dto.description(), dto.category(), dto.price(),
                orZero(dto.discountPercentage()), orZero(dto.rating()), orZero(dto.stock()), dto.tags(),
                dto.brand(), dto.sku(), dto.thumbnail(), dto.images());
    }

    /**
     * Converts a page of products.
     *
     * @param dto the DTO
     * @return the domain page
     */
    public ProductPage toDomain(ProductListDto dto) {
        List<ProductDto> products = dto.products() == null ? List.of() : dto.products();
        return new ProductPage(products.stream().map(this::toDomain).toList(), orZero(dto.total()),
                orZero(dto.skip()), orZero(dto.limit()));
    }

    /**
     * Converts a category.
     *
     * @param dto the DTO
     * @return the domain category
     */
    public ProductCategory toDomain(CategoryDto dto) {
        return new ProductCategory(dto.slug(), dto.name());
    }

    /**
     * Converts a user.
     *
     * @param dto the DTO
     * @return the domain user
     */
    public UserProfile toDomain(UserDto dto) {
        return new UserProfile(orZero(dto.id()), dto.username(), dto.email(), dto.firstName(), dto.lastName(),
                dto.gender(), dto.image());
    }

    /**
     * Converts the login response.
     *
     * @param dto the DTO
     * @return the domain session
     */
    public AuthSession toDomain(LoginResponseDto dto) {
        UserProfile user = new UserProfile(orZero(dto.id()), dto.username(), dto.email(), dto.firstName(),
                dto.lastName(), dto.gender(), dto.image());
        return new AuthSession(user, new AuthTokens(dto.accessToken(), dto.refreshToken()));
    }

    /**
     * Converts the token renewal response.
     *
     * @param dto the DTO
     * @return the domain tokens
     */
    public AuthTokens toDomain(RefreshResponseDto dto) {
        return new AuthTokens(dto.accessToken(), dto.refreshToken());
    }

    /**
     * Converts a new product into the body DummyJSON expects.
     *
     * @param newProduct the domain product
     * @return the request body
     */
    public ProductPayloadDto toPayload(NewProduct newProduct) {
        return new ProductPayloadDto(newProduct.title(), newProduct.description(), newProduct.category(),
                newProduct.price(), newProduct.stock(), newProduct.brand());
    }

    /**
     * Converts changes into the body DummyJSON expects (null fields are not sent).
     *
     * @param changes the domain changes
     * @return the request body
     */
    public ProductPayloadDto toPayload(ProductChanges changes) {
        return new ProductPayloadDto(changes.title(), changes.description(), changes.category(), changes.price(),
                changes.stock(), changes.brand());
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static double orZero(Double value) {
        return value == null ? 0 : value;
    }
}
