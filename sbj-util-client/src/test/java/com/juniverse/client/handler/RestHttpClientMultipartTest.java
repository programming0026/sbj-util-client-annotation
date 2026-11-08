package com.juniverse.client.handler;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.autoconfigure.RetryConfig;
import com.juniverse.client.enums.ClientType;
import com.juniverse.client.exception.ClientException;
import com.juniverse.client.util.CircuitBreakerUtil;
import com.juniverse.client.util.RestClientUtil;
import com.juniverse.client.util.SSLUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Subida de archivos y formularios {@code multipart/form-data} con {@code @RequestPart}. */
class RestHttpClientMultipartTest {

    /** Objeto que viaja como una parte JSON. */
    record Meta(String title, int pages) {
    }

    @RestHttpClient(url = "${multipart.url}", retry = @RetryConfig(maxRetries = 1))
    interface UploadClient {

        // Sin consumes: la librería fija multipart/form-data porque hay @RequestPart.
        @PostMapping("/upload")
        String upload(@RequestPart("file") MultipartFile file, @RequestPart("description") String description);

        @PostMapping("/resource")
        String resource(@RequestPart("doc") Resource doc, @RequestPart(name = "note", required = false) String note);

        @PostMapping("/files")
        String files(@RequestPart("files") List<MultipartFile> files);

        @PostMapping("/mixed")
        String mixed(@RequestPart("meta") Meta meta, @RequestPart("raw") byte[] raw, @RequestPart("path") Path path,
                     @RequestPart("file") File file, @RequestPart("count") int count);

        @PostMapping(path = "/explicit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        String explicit(@RequestPart("a") String a);

        @PostMapping("/combined")
        String combined(@RequestParam("folder") String folder, @RequestHeader("X-Trace") String trace,
                        @RequestPart("file") MultipartFile file);

        @PostMapping(path = "/bodymap", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        String bodyMap(@RequestBody MultiValueMap<String, Object> parts);
    }

    @RestHttpClient(url = "${multipart.url}", type = ClientType.REST_TEMPLATE, retry = @RetryConfig(maxRetries = 1))
    interface TemplateUploadClient {

        @PostMapping("/upload")
        String upload(@RequestPart("file") MultipartFile file, @RequestPart("description") String description);
    }

    @RestHttpClient(url = "${multipart.url}")
    interface BodyAndPartClient {

        @PostMapping("/x")
        String upload(@RequestBody String body, @RequestPart("f") String part);
    }

    @RestHttpClient(url = "${multipart.url}")
    interface JsonConsumesClient {

        @PostMapping(path = "/x", consumes = MediaType.APPLICATION_JSON_VALUE)
        String upload(@RequestPart("f") String part);
    }

    /** Lo que recibió el servidor. */
    private record Received(String path, String query, String contentType, String trace, String body) {
    }

    private HttpServer server;

    private final AtomicReference<Received> received = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            received.set(new Received(exchange.getRequestURI().getPath(), exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("X-Trace"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1)));
            byte[] ok = "uploaded".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.close();
        });
        server.start();
        System.setProperty("multipart.url", "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        System.clearProperty("multipart.url");
        server.stop(0);
    }

    private static <T> T client(Class<T> type) throws Exception {
        return new RestHttpClientFactoryBean<>(type, new CircuitBreakerUtil(), new RestClientUtil(new SSLUtil()))
                .getObject();
    }

    @Test
    void uploadsAMultipartFileWithItsNameAndContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("ignored", "report.csv", "text/csv",
                "a,b\n1,2".getBytes(StandardCharsets.UTF_8));

        assertThat(client(UploadClient.class).upload(file, "monthly report")).isEqualTo("uploaded");

        Received request = received.get();
        assertThat(request.contentType()).startsWith("multipart/form-data;boundary=");
        assertThat(request.body())
                .contains("Content-Disposition: form-data; name=\"file\"; filename=\"report.csv\"")
                .contains("Content-Type: text/csv")
                .contains("a,b\n1,2")
                .contains("Content-Disposition: form-data; name=\"description\"")
                .contains("monthly report");
    }

    @Test
    void worksWithRestTemplateToo() throws Exception {
        MockMultipartFile file = new MockMultipartFile("f", "photo.png", "image/png", new byte[]{1, 2, 3});

        assertThat(client(TemplateUploadClient.class).upload(file, "avatar")).isEqualTo("uploaded");

        assertThat(received.get().contentType()).startsWith("multipart/form-data;boundary=");
        assertThat(received.get().body()).contains("name=\"file\"; filename=\"photo.png\"")
                .contains("Content-Type: image/png").contains("name=\"description\"");
    }

    @Test
    void uploadsAResourceAndOmitsAnOptionalPartThatIsNull() throws Exception {
        Resource doc = new ByteArrayResource("PDFDATA".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "doc.pdf";
            }
        };

        client(UploadClient.class).resource(doc, null);

        assertThat(received.get().body()).contains("name=\"doc\"; filename=\"doc.pdf\"")
                .contains("Content-Type: application/pdf").contains("PDFDATA").doesNotContain("name=\"note\"");
    }

    @Test
    void aListOfFilesBecomesOnePartPerFileWithTheSameName() throws Exception {
        List<MultipartFile> files = List.of(
                new MockMultipartFile("x", "one.txt", "text/plain", "FIRST".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("x", "two.txt", "text/plain", "SECOND".getBytes(StandardCharsets.UTF_8)));

        client(UploadClient.class).files(files);

        String body = received.get().body();
        assertThat(body).contains("name=\"files\"; filename=\"one.txt\"").contains("FIRST")
                .contains("name=\"files\"; filename=\"two.txt\"").contains("SECOND");
        assertThat(body.split("name=\"files\"", -1)).hasSize(3);
    }

    @Test
    void sendsObjectsAsJsonAndBytesPathsAndFilesAsFiles(@TempDir Path tempDir) throws Exception {
        Path path = Files.writeString(tempDir.resolve("notes.txt"), "PATHDATA");
        File file = Files.writeString(tempDir.resolve("data.bin"), "FILEDATA").toFile();

        client(UploadClient.class).mixed(new Meta("Q3", 12), "RAWBYTES".getBytes(StandardCharsets.UTF_8), path,
                file, 7);

        assertThat(received.get().body())
                .contains("name=\"meta\"").contains("Content-Type: application/json")
                .contains("\"title\":\"Q3\"").contains("\"pages\":12")
                .contains("name=\"raw\"").contains("RAWBYTES")
                .contains("name=\"path\"; filename=\"notes.txt\"").contains("PATHDATA")
                .contains("name=\"file\"; filename=\"data.bin\"").contains("FILEDATA")
                .contains("name=\"count\"").contains("\r\n7\r\n");
    }

    @Test
    void anExplicitMultipartConsumesIsAccepted() throws Exception {
        client(UploadClient.class).explicit("value");

        assertThat(received.get().contentType()).startsWith("multipart/form-data;boundary=");
        assertThat(received.get().body()).contains("name=\"a\"").contains("value");
    }

    @Test
    void partsCanBeCombinedWithQueryParamsAndHeaders() throws Exception {
        MockMultipartFile file = new MockMultipartFile("f", "a.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8));

        client(UploadClient.class).combined("my docs", "trace-1", file);

        Received request = received.get();
        assertThat(request.path()).isEqualTo("/combined");
        assertThat(request.query()).isEqualTo("folder=my%20docs");
        assertThat(request.trace()).isEqualTo("trace-1");
        assertThat(request.body()).contains("filename=\"a.txt\"");
    }

    @Test
    void aRequiredPartThatIsNullOrAnEmptyListIsRejected() throws Exception {
        UploadClient client = client(UploadClient.class);

        assertThatThrownBy(() -> client.upload(null, "d"))
                .isInstanceOf(ClientException.class)
                .hasMessage("Required request part 'file' is null or empty in UploadClient.upload");
        assertThatThrownBy(() -> client.files(List.of()))
                .isInstanceOf(ClientException.class)
                .hasMessage("Required request part 'files' is null or empty in UploadClient.files");
        assertThat(received.get()).isNull();
    }

    @Test
    void theManualMultiValueMapBodyStillWorks() throws Exception {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("description", "manual");

        client(UploadClient.class).bodyMap(parts);

        assertThat(received.get().body()).contains("name=\"description\"").contains("manual");
    }

    @Test
    void invalidDeclarationsFailWhenTheClientIsCreated() {
        assertThatThrownBy(() -> client(BodyAndPartClient.class))
                .isInstanceOf(ClientException.class)
                .hasMessage("Method BodyAndPartClient.upload cannot combine @RequestBody with @RequestPart");
        assertThatThrownBy(() -> client(JsonConsumesClient.class))
                .isInstanceOf(ClientException.class)
                .hasMessage("Method JsonConsumesClient.upload has @RequestPart parameters, so it must consume "
                        + "multipart/form-data, but declares 'application/json'");
    }
}
