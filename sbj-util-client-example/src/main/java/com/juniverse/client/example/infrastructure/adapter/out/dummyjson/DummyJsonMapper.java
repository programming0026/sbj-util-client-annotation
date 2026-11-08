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
 * Convierte entre los DTOs de DummyJSON y el modelo del dominio.
 *
 * Es la única clase que conoce ambos lados: así el dominio no depende de la forma de la API externa y un
 * cambio en esa API solo afecta a este paquete.
 */
@Component
public class DummyJsonMapper {

    /**
     * Convierte un producto.
     *
     * @param dto el DTO
     * @return el producto del dominio
     */
    public Product toDomain(ProductDto dto) {
        return new Product(orZero(dto.id()), dto.title(), dto.description(), dto.category(), dto.price(),
                orZero(dto.discountPercentage()), orZero(dto.rating()), orZero(dto.stock()), dto.tags(),
                dto.brand(), dto.sku(), dto.thumbnail(), dto.images());
    }

    /**
     * Convierte una página de productos.
     *
     * @param dto el DTO
     * @return la página del dominio
     */
    public ProductPage toDomain(ProductListDto dto) {
        List<ProductDto> products = dto.products() == null ? List.of() : dto.products();
        return new ProductPage(products.stream().map(this::toDomain).toList(), orZero(dto.total()),
                orZero(dto.skip()), orZero(dto.limit()));
    }

    /**
     * Convierte una categoría.
     *
     * @param dto el DTO
     * @return la categoría del dominio
     */
    public ProductCategory toDomain(CategoryDto dto) {
        return new ProductCategory(dto.slug(), dto.name());
    }

    /**
     * Convierte un usuario.
     *
     * @param dto el DTO
     * @return el usuario del dominio
     */
    public UserProfile toDomain(UserDto dto) {
        return new UserProfile(orZero(dto.id()), dto.username(), dto.email(), dto.firstName(), dto.lastName(),
                dto.gender(), dto.image());
    }

    /**
     * Convierte la respuesta de iniciar sesión.
     *
     * @param dto el DTO
     * @return la sesión del dominio
     */
    public AuthSession toDomain(LoginResponseDto dto) {
        UserProfile user = new UserProfile(orZero(dto.id()), dto.username(), dto.email(), dto.firstName(),
                dto.lastName(), dto.gender(), dto.image());
        return new AuthSession(user, new AuthTokens(dto.accessToken(), dto.refreshToken()));
    }

    /**
     * Convierte la respuesta de renovar tokens.
     *
     * @param dto el DTO
     * @return los tokens del dominio
     */
    public AuthTokens toDomain(RefreshResponseDto dto) {
        return new AuthTokens(dto.accessToken(), dto.refreshToken());
    }

    /**
     * Convierte un producto nuevo al cuerpo que espera DummyJSON.
     *
     * @param newProduct el producto del dominio
     * @return el cuerpo de la petición
     */
    public ProductPayloadDto toPayload(NewProduct newProduct) {
        return new ProductPayloadDto(newProduct.title(), newProduct.description(), newProduct.category(),
                newProduct.price(), newProduct.stock(), newProduct.brand());
    }

    /**
     * Convierte unos cambios al cuerpo que espera DummyJSON (los campos nulos no se envían).
     *
     * @param changes los cambios del dominio
     * @return el cuerpo de la petición
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
