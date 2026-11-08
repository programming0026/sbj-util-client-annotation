package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.enums.ClientType;
import com.juniverse.client.exception.ClientException;
import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.util.RestClientUtil;
import com.juniverse.client.util.SSLUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Los tipos de retorno que la librería no soporta se rechazan al crear el cliente, con un mensaje claro. */
class RestHttpClientReturnTypeValidationTest {

    private static final String URL = "http://localhost:8080";

    @RestHttpClient(url = URL)
    interface CompletableFutureClient {

        @GetMapping("/x")
        CompletableFuture<String> find();
    }

    @RestHttpClient(url = URL)
    interface FutureClient {

        @GetMapping("/x")
        Future<String> find();
    }

    @RestHttpClient(url = URL)
    interface CompletionStageClient {

        @GetMapping("/x")
        CompletionStage<String> find();
    }

    @RestHttpClient(url = URL)
    interface MonoClient {

        @GetMapping("/x")
        Mono<String> find();
    }

    @RestHttpClient(url = URL)
    interface FluxClient {

        @GetMapping("/x")
        Flux<String> findAll();
    }

    @RestHttpClient(url = URL)
    interface StreamClient {

        @GetMapping("/x")
        Stream<String> findAll();
    }

    @RestHttpClient(url = URL)
    interface InputStreamSubclassClient {

        @GetMapping("/x")
        ByteArrayInputStream download();
    }

    @RestHttpClient(url = URL)
    interface WrappedFutureClient {

        @GetMapping("/x")
        ResponseEntity<CompletableFuture<String>> find();
    }

    @RestHttpClient(url = URL, type = ClientType.REST_TEMPLATE)
    interface RestTemplateStreamingClient {

        @GetMapping("/x")
        InputStream download();
    }

    @RestHttpClient(url = URL)
    interface SupportedReturnTypesClient {

        @GetMapping("/a")
        InputStream download();

        @GetMapping("/b")
        ResponseEntity<InputStream> downloadWithHeaders();

        @GetMapping("/c")
        ResponseEntity<byte[]> bytes();

        @GetMapping("/d")
        List<String> list();

        @GetMapping("/e")
        void nothing();
    }

    private static void create(Class<?> type) throws Exception {
        new RestHttpClientFactoryBean<>(type, new CircuitBreakerUtil(), new RestClientUtil(new SSLUtil()))
                .getObject();
    }

    @Test
    void asynchronousTypesAreRejectedWithAnExplanation() {
        assertThatThrownBy(() -> create(CompletableFutureClient.class))
                .isInstanceOf(ClientException.class)
                .hasMessage("Method CompletableFutureClient.find returns "
                        + "java.util.concurrent.CompletableFuture<java.lang.String>, which is not supported: the "
                        + "clients are synchronous. Return the value directly (or ResponseEntity<T>) and call the "
                        + "method from your own executor if you need it to run asynchronously");
        assertThatThrownBy(() -> create(FutureClient.class)).isInstanceOf(ClientException.class)
                .hasMessageContaining("Method FutureClient.find returns java.util.concurrent.Future<java.lang.String>")
                .hasMessageContaining("the clients are synchronous");
        assertThatThrownBy(() -> create(CompletionStageClient.class)).isInstanceOf(ClientException.class)
                .hasMessageContaining("the clients are synchronous");
    }

    @Test
    void reactiveTypesAreRejectedBecauseTheLibraryIsImperative() {
        assertThatThrownBy(() -> create(MonoClient.class))
                .isInstanceOf(ClientException.class)
                .hasMessage("Method MonoClient.find returns reactor.core.publisher.Mono<java.lang.String>, which is "
                        + "not supported: it is a reactive type and this library is imperative "
                        + "(RestClient/RestTemplate). Return the value directly (or ResponseEntity<T>)");
        assertThatThrownBy(() -> create(FluxClient.class)).isInstanceOf(ClientException.class)
                .hasMessageContaining("it is a reactive type and this library is imperative");
    }

    @Test
    void javaStreamsAreRejectedAndASuggestionIsGiven() {
        assertThatThrownBy(() -> create(StreamClient.class))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Method StreamClient.findAll returns java.util.stream.Stream<java.lang.String>")
                .hasMessageContaining("Return a List, or an InputStream to read a download progressively");
    }

    @Test
    void onlyInputStreamItselfIsSupported() {
        assertThatThrownBy(() -> create(InputStreamSubclassClient.class))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("only InputStream itself is supported, not its subclasses");
    }

    @Test
    void theBodyTypeOfAResponseEntityIsValidatedToo() {
        assertThatThrownBy(() -> create(WrappedFutureClient.class))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Method WrappedFutureClient.find returns")
                .hasMessageContaining("the clients are synchronous");
    }

    @Test
    void streamingNeedsRestClientNotRestTemplate() {
        assertThatThrownBy(() -> create(RestTemplateStreamingClient.class))
                .isInstanceOf(ClientException.class)
                .hasMessage("Method RestTemplateStreamingClient.download returns InputStream, which needs "
                        + "type = ClientType.REST_CLIENT: RestTemplate closes the response before the method "
                        + "returns, so the body cannot be streamed");
    }

    @Test
    void supportedReturnTypesAreAccepted() {
        assertThatCode(() -> create(SupportedReturnTypesClient.class)).doesNotThrowAnyException();
        assertThat(SupportedReturnTypesClient.class.getMethods()).hasSize(5);
    }
}
