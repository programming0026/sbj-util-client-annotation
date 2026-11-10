package io.github.programming0026.juniverse.client.handler;

import io.github.programming0026.juniverse.client.autoconfigure.RestHttpClient;
import io.github.programming0026.juniverse.client.autoconfigure.RetryConfig;
import io.github.programming0026.juniverse.client.exception.DefaultErrorHandler;
import io.github.programming0026.juniverse.client.util.CircuitBreakerUtil;
import org.junit.jupiter.api.Test;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@code @DateTimeFormat} y {@code @NumberFormat} dan formato a los parámetros; sin ellas el texto es el de
 * siempre ({@code String.valueOf}).
 *
 * @author Java Universe
 */
class RestHttpClientParameterFormatTest {

    private static final String BASE = "http://localhost/api";

    /** Cliente con parámetros formateados y sin formatear. */
    @RestHttpClient(url = BASE, retry = @RetryConfig(maxRetries = 1, retryDelay = 1))
    interface FormatClient {

        @GetMapping("/by-date")
        String pattern(@RequestParam("d") @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate date);

        @GetMapping("/by-date")
        String iso(@RequestParam("d") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDateTime date);

        @GetMapping("/by-date")
        String legacy(@RequestParam("d") @DateTimeFormat(pattern = "yyyyMMdd") Date date);

        @GetMapping("/by-date")
        String many(@RequestParam("d") @DateTimeFormat(pattern = "dd-MM-yyyy") List<LocalDate> dates);

        @GetMapping("/by-date")
        String inMap(@RequestParam @DateTimeFormat(pattern = "dd/MM/yyyy") Map<String, LocalDate> dates);

        @GetMapping("/by-date")
        String plain(@RequestParam("d") LocalDate date);

        @GetMapping("/by-number")
        String number(@RequestParam("n") @NumberFormat(pattern = "#,##0.00") BigDecimal value);

        @GetMapping("/by-number")
        String plainNumber(@RequestParam("n") BigDecimal value);

        @GetMapping("/items/{day}")
        String path(@PathVariable("day") @DateTimeFormat(pattern = "yyyy-MM") LocalDate day);

        @GetMapping("/by-header")
        String header(@RequestHeader("X-Day") @DateTimeFormat(pattern = "dd.MM.yyyy") LocalDate day);

        @GetMapping("/by-date")
        String formatOnText(@RequestParam("d") @DateTimeFormat(pattern = "dd/MM/yyyy") String text);
    }

    private static FormatClient proxy(RestTemplate template) {
        RestHttpClientInvocationHandler handler = new RestHttpClientInvocationHandler(template,
                FormatClient.class.getAnnotation(RestHttpClient.class), new DefaultErrorHandler(),
                FormatClient.class, new CircuitBreakerUtil());
        return (FormatClient) Proxy.newProxyInstance(FormatClient.class.getClassLoader(),
                new Class<?>[]{FormatClient.class}, handler);
    }

    private static MockRestServiceServer expectUrl(RestTemplate template, String url) {
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(url)).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
        return server;
    }

    @Test
    void aDateTimeFormatPatternFormatsTheQueryParam() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = expectUrl(template, BASE + "/by-date?d=08%2F11%2F2026");

        assertThat(proxy(template).pattern(LocalDate.of(2026, 11, 8))).isEqualTo("ok");
        server.verify();
    }

    @Test
    void anIsoFormatIsApplied() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = expectUrl(template, BASE + "/by-date?d=2026-11-08");

        proxy(template).iso(LocalDateTime.of(2026, 11, 8, 10, 30));
        server.verify();
    }

    @Test
    void javaUtilDateIsFormattedToo() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = expectUrl(template, BASE + "/by-date?d=20261108");
        Date date = java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 11, 8, 12, 0));

        proxy(template).legacy(date);
        server.verify();
    }

    @Test
    void everyElementOfAListIsFormatted() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = expectUrl(template, BASE + "/by-date?d=08-11-2026&d=09-11-2026");

        proxy(template).many(List.of(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 9)));
        server.verify();
    }

    @Test
    void theValuesOfAMapAreFormatted() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = expectUrl(template, BASE + "/by-date?from=01%2F01%2F2026");

        proxy(template).inMap(Map.of("from", LocalDate.of(2026, 1, 1)));
        server.verify();
    }

    @Test
    void withoutAnnotationsADateStaysIso() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = expectUrl(template, BASE + "/by-date?d=2026-11-08");

        proxy(template).plain(LocalDate.of(2026, 11, 8));
        server.verify();
    }

    @Test
    void aNumberFormatPatternFormatsTheNumber() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = expectUrl(template, BASE + "/by-number?n=1%2C234.50");

        proxy(template).number(new BigDecimal("1234.5"));
        server.verify();
    }

    @Test
    void withoutAnnotationsANumberHasNoSeparators() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = expectUrl(template, BASE + "/by-number?n=1234.5");

        proxy(template).plainNumber(new BigDecimal("1234.5"));
        server.verify();
    }

    @Test
    void aPathVariableIsFormatted() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = expectUrl(template, BASE + "/items/2026-11");

        proxy(template).path(LocalDate.of(2026, 11, 8));
        server.verify();
    }

    @Test
    void aHeaderIsFormatted() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(BASE + "/by-header")).andExpect(header("X-Day", "08.11.2026"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        proxy(template).header(LocalDate.of(2026, 11, 8));
        server.verify();
    }

    @Test
    void aFormatOnAStringLeavesItUntouched() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = expectUrl(template, BASE + "/by-date?d=ya%20formateado");

        proxy(template).formatOnText("ya formateado");
        server.verify();
    }
}
