package com.juniverse.client.testing;

import com.juniverse.client.autoconfigure.RestHttpClient;
import com.juniverse.client.config.RestHttpClientCustomizer;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Para probar aplicaciones con <b>varios</b> clientes de la librería: crea un {@link MockRestServiceServer}
 * por cada cliente y los entrega por su nombre. Con el {@code MockServerRestClientCustomizer} de Spring Boot,
 * en cambio, {@code getServer()} falla en cuanto hay más de un cliente y {@code getServers()} no dice a qué
 * cliente pertenece cada servidor.
 *
 * Se declara como bean (un método que devuelva {@code new MockRestHttpClients()}) en la configuración de la
 * prueba y funciona con clientes {@code REST_CLIENT} y {@code REST_TEMPLATE}. Después, en cada prueba:
 *
 * <pre>{@code
 * mocks.server(UsersClient.class).expect(requestTo("https://users/1")).andRespond(...);
 * mocks.server(PaymentsClient.class).expect(requestTo("https://payments/charge")).andRespond(...);
 * // ... ejecutar el código bajo prueba ...
 * mocks.verify();
 * }</pre>
 *
 * No forma parte del jar de la librería (para no depender de {@code spring-test} en producción): vive en los tests
 * de la librería y se copia a los de tu aplicación; el README la muestra completa.
 */
public class MockRestHttpClients implements RestHttpClientCustomizer {

    /** Los servidores por nombre de cliente. */
    private final Map<String, MockRestServiceServer> servers = new ConcurrentHashMap<>();

    /** Si los servidores guardan el cuerpo de las respuestas para poder leerlo más de una vez. */
    private boolean bufferContent;

    /** Si los servidores aceptan las peticiones en cualquier orden. */
    private boolean ignoreExpectOrder;

    /**
     * Hace que los servidores guarden el cuerpo de las peticiones y respuestas en memoria para poder leerlo
     * más de una vez (por ejemplo, con interceptores que lo registran). Debe llamarse antes de que se creen
     * los clientes.
     *
     * @return este objeto
     */
    public MockRestHttpClients bufferContent() {
        this.bufferContent = true;
        return this;
    }

    /**
     * Hace que los servidores acepten las peticiones esperadas en cualquier orden. Debe llamarse antes de que
     * se creen los clientes.
     *
     * @return este objeto
     */
    public MockRestHttpClients ignoreExpectOrder() {
        this.ignoreExpectOrder = true;
        return this;
    }

    /**
     * Enlaza un servidor al builder de un cliente {@code REST_CLIENT}.
     *
     * @param clientName el nombre del cliente
     * @param builder el builder del cliente
     */
    @Override
    public void customize(String clientName, RestClient.Builder builder) {
        register(clientName, MockRestServiceServer.bindTo(builder));
    }

    /**
     * Enlaza un servidor al RestTemplate de un cliente {@code REST_TEMPLATE}.
     *
     * @param clientName el nombre del cliente
     * @param template el RestTemplate del cliente
     */
    @Override
    public void customize(String clientName, RestTemplate template) {
        register(clientName, MockRestServiceServer.bindTo(template));
    }

    private void register(String clientName, MockRestServiceServer.MockRestServiceServerBuilder builder) {
        if (bufferContent) {
            builder.bufferContent();
        }
        builder.ignoreExpectOrder(ignoreExpectOrder);
        servers.put(clientName, builder.build());
    }

    /**
     * Obtiene el servidor de un cliente.
     *
     * @param clientName el nombre del cliente: el {@code name()} de {@code @RestHttpClient} o, si está vacío, el
     *                   nombre simple de la interfaz
     * @return el servidor, para declarar las peticiones esperadas
     * @throws IllegalStateException si no hay un cliente con ese nombre (el mensaje lista los que hay)
     */
    public MockRestServiceServer server(String clientName) {
        MockRestServiceServer server = servers.get(clientName);
        if (server == null) {
            throw new IllegalStateException("There is no mocked client named '" + clientName + "'. Known clients: "
                    + clientNames());
        }
        return server;
    }

    /**
     * Obtiene el servidor del cliente declarado por una interfaz. Usa el {@code name()} de {@code @RestHttpClient}
     * tal cual está escrito o, si está vacío, el nombre simple de la interfaz; con un {@code name()} que lleve
     * placeholders usa {@link #server(String)} con el nombre ya resuelto.
     *
     * @param clientInterface la interfaz del cliente
     * @return el servidor, para declarar las peticiones esperadas
     * @throws IllegalStateException si ese cliente no existe
     */
    public MockRestServiceServer server(Class<?> clientInterface) {
        RestHttpClient annotation = clientInterface.getAnnotation(RestHttpClient.class);
        String name = annotation == null || annotation.name().isEmpty()
                ? clientInterface.getSimpleName()
                : annotation.name();
        return server(name);
    }

    /**
     * Obtiene los nombres de los clientes con servidor, ordenados.
     *
     * @return los nombres
     */
    public Set<String> clientNames() {
        return new TreeSet<>(servers.keySet());
    }

    /**
     * Comprueba que todos los clientes hicieron las peticiones esperadas. Si faltan en varios, el error nombra
     * a cada cliente.
     *
     * @throws AssertionError si algún cliente no cumplió sus expectativas
     */
    public void verify() {
        Map<String, AssertionError> failures = new TreeMap<>();
        servers.forEach((name, server) -> {
            try {
                server.verify();
            } catch (AssertionError e) {
                failures.put(name, e);
            }
        });
        if (!failures.isEmpty()) {
            StringBuilder message = new StringBuilder("Unmet expectations in " + failures.size() + " client(s):");
            failures.forEach((name, error) -> message.append("\n  [").append(name).append("] ")
                    .append(error.getMessage()));
            throw new AssertionError(message.toString());
        }
    }

    /** Borra las expectativas y las peticiones registradas de todos los clientes, para reutilizar el contexto. */
    public void reset() {
        servers.values().forEach(MockRestServiceServer::reset);
    }
}
