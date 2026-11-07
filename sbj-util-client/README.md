# sbj-util-client

Librería para declarar **clientes REST como interfaces** en Spring Boot, con patrones de resiliencia de
Resilience4j (retry, circuit breaker, rate limiter y time limiter) integrados.

Los métodos se describen con las mismas anotaciones de Spring Web que se usan en los controladores
(`@GetMapping`, `@PathVariable`, `@RequestParam`, etc.); la librería genera un proxy que hace la petición
con `RestClient` o `RestTemplate` sobre Apache HttpClient 5.

## Requisitos

- Java 21
- Spring Boot 4.1
- Maven 3.9+

## Instalación

Instala la librería en tu repositorio local:

```bash
mvn clean install
```

Y agrégala como dependencia:

```xml
<dependency>
    <groupId>com.juniverse.client</groupId>
    <artifactId>sbj-util-client</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Uso rápido

1. Activa los clientes en una clase de configuración. Si no indicas `basePackages`, se busca en el paquete
   de esa clase.

```java
@Configuration
@EnableRestHttpClients(basePackages = "com.miempresa.clientes")
public class ClientesConfig {
}
```

2. Declara el cliente:

```java
@RestHttpClient(name = "userClient", url = "https://api.ejemplo.com")
public interface UserClient {

    @GetMapping(value = "/users/{id}", headers = "X-App=demo")
    User getUser(@PathVariable("id") long id);

    @GetMapping("/users")
    List<User> search(@RequestParam(name = "page", defaultValue = "1") int page,
                      @RequestParam(name = "tag", required = false) List<String> tags);

    @PostMapping(path = "/users", consumes = MediaType.APPLICATION_JSON_VALUE)
    User create(@RequestBody User user, @RequestHeader("X-Trace") String traceId);
}
```

3. Inyéctalo como cualquier bean:

```java
@Service
public class UserService {

    private final UserClient userClient;

    public UserService(UserClient userClient) {
        this.userClient = userClient;
    }
}
```

El nombre del bean es `name()` o, si está vacío, el nombre de la interfaz con la primera letra en
minúscula (`userClient`).

## Anotaciones de los métodos

Se admiten `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping` y
`@RequestMapping(method = ...)`.

| Atributo | Cómo se usa |
|---|---|
| `value` / `path` | Ruta relativa a `url()`. Se usa el primer valor. |
| `headers` | Headers fijos con el formato de Spring `"Nombre=valor"`. Las condiciones sin valor (`"Nombre"`, `"!Nombre"`, `"Nombre!=valor"`) se ignoran con un warning. |
| `produces` | Se envía como header `Accept`. |
| `consumes` | El primer valor se envía como header `Content-Type`. |

No se aplican el `@RequestMapping` a nivel de interfaz ni el atributo `params`. Un método sin anotación
de mapping (o con `@RequestMapping` sin `method`) lanza `ClientException` al invocarlo.

## Anotaciones de los parámetros

| Anotación | Se envía como |
|---|---|
| `@PathVariable` | Variable de la ruta (`{id}`). |
| `@RequestParam` | Query param. |
| `@RequestHeader` | Header; reemplaza al del mismo nombre declarado en `headers` del mapping. |
| `@RequestBody` | Cuerpo de la petición. |

- **Nombre:** se usa `name`/`value`; si no se indica, el nombre del parámetro en Java (requiere compilar
  con `-parameters`, que ya activa `spring-boot-starter-parent`).
- **Valores `null` o vacíos:** se usa `defaultValue` si existe; si `required = false` se omite; si es
  requerido se lanza `ClientException`. Una `@PathVariable` sin valor siempre lanza error, y un
  `@RequestBody` `null` solo se permite con `required = false`.
- **Codificación:** cada valor se codifica una sola vez en la URL (`a&b` → `a%26b`, `a b/c` → `a%20b%2Fc`).

### Listas y arreglos

`List`, `Set`, cualquier `Iterable` y los arreglos (incluidos los primitivos como `int[]`) se expanden.
Los elementos `null` se saltan y una lista vacía cuenta como valor faltante.

| Anotación | Ejemplo | Resultado |
|---|---|---|
| `@RequestParam` | `List.of("a", "b")` | `?tag=a&tag=b` |
| `@RequestHeader` | `new String[]{"admin", "user"}` | header con dos valores |
| `@PathVariable` | `List.of(1, 2, 3)` | `/items/1%2C2%2C3` (unidos con coma) |

### Map

Un parámetro declarado como `Map` o `MultiValueMap` aporta una entrada por clave (el nombre de la
anotación se ignora):

```java
@GetMapping("/items")
String search(@RequestParam Map<String, Object> filtros);
// {q: "a&b", tag: [x, y], page: 2}  →  /items?q=a%26b&tag=x&tag=y&page=2

@GetMapping("/orgs/{org}/items/{id}")
String byPath(@PathVariable Map<String, Object> variables);
```

Las entradas con clave `null`, valor `null` o lista vacía se saltan. Solo un mapa `null` cuenta como
faltante; un mapa vacío no envía nada.

## Configuración del cliente

Todo se configura en `@RestHttpClient`:

| Atributo | Por defecto | Descripción |
|---|---|---|
| `url` | `""` | URL base del servicio. |
| `type` | `REST_CLIENT` | `REST_CLIENT` o `REST_TEMPLATE`. |
| `connectTimeout` | `5000` | Tiempo máximo para abrir la conexión TCP y para obtener una conexión libre del pool (ms). |
| `readTimeout` | `30000` | Tiempo máximo de espera de la respuesta una vez conectado (ms). |
| `maxConnections` | `100` | Conexiones totales del pool. |
| `maxConnectionsPerRoute` | `20` | Conexiones por ruta. |
| `connectionTimeToLive` | `-1` | Tiempo de vida de las conexiones (ms); `-1` sin límite. |
| `ssl` | `@SSLConfig` | Keystore y truststore. |
| `retry`, `circuitBreaker`, `rateLimiter`, `timeLimiter` | | Resiliencia (ver abajo). |
| `errorHandler` | `DefaultErrorHandler` | Manejo de respuestas con error. |

> Los valores de las anotaciones se leen tal cual: los placeholders `${...}` **no** se resuelven.

## Resiliencia

Los decoradores se aplican en este orden: **Retry → CircuitBreaker → RateLimiter → TimeLimiter**.

```java
@RestHttpClient(
        url = "https://api.ejemplo.com",
        retry = @RetryConfig(maxRetries = 3, retryDelay = 500, exponentialBackoffMultiplier = 2.0),
        circuitBreaker = @CircuitBreakerConfig(enabled = true, failureRateThreshold = 50),
        rateLimiter = @RateLimiterConfig(enabled = true, limitForPeriod = 100, limitRefreshPeriod = 1000),
        timeLimiter = @TimeLimiterConfig(enabled = true, timeout = 2000))
public interface PagosClient { ... }
```

- **Retry:** siempre activo. `maxRetries` es el número total de intentos (incluido el primero). Los errores
  HTTP se reintentan solo si `ErrorHandler.shouldRetry` lo permite; los demás errores (por ejemplo, de red)
  siempre se reintentan.
- **Circuit breaker, rate limiter y time limiter:** desactivados por defecto (`enabled = false`).
- **Timeout:** si se excede el time limiter se lanza `ClientException` con un `TimeoutException` como causa.

## Manejo de errores

Los errores HTTP llegan a quien llama como `HttpClientErrorException` / `HttpServerErrorException` (no se
envuelven). `ClientException` se reserva para errores de la librería: configuración, registro, valores
requeridos faltantes y timeout.

`DefaultErrorHandler`:

- 4xx y 5xx → lanza `HttpClientErrorException` con el cuerpo de la respuesta.
- Reintenta 502, 503 y 504.
- Cuenta los 5xx como falla del circuit breaker.

Para cambiarlo, implementa `ErrorHandler` (con constructor sin argumentos; se instancia por reflexión, no es
un bean de Spring) y declárala en `errorHandler`:

```java
public class MiErrorHandler implements ErrorHandler {
    @Override
    public void handleError(HttpStatusCode status, String body, Exception e) { ... }

    @Override
    public boolean shouldRetry(HttpStatusCode status) { return status.value() == 429; }

    @Override
    public boolean shouldRecordAsFailure(HttpStatusCode status) { return status.is5xxServerError(); }
}
```

## SSL

```java
@RestHttpClient(url = "https://api.interna.com",
        ssl = @SSLConfig(keystorePath = "classpath:cliente.p12", keystorePassword = "...",
                         truststorePath = "/etc/certs/truststore.jks", truststorePassword = "..."))
```

- Las rutas admiten `classpath:` o una ruta del sistema de archivos.
- `.p12` / `.pfx` se leen como PKCS12; cualquier otra extensión como JKS.
- Sin keystore ni truststore se usa el contexto SSL por defecto de la JVM.
- `@SSLConfig(enabled = false)` desactiva la validación de certificados **solo** si el perfil activo
  (`SPRING_PROFILES_ACTIVE` o la propiedad del sistema `spring.profiles.active`) es `dev`, `local` o
  `development`; en cualquier otro caso falla al arrancar.

## Desarrollo

```bash
mvn clean verify                   # compila, ejecuta tests, cobertura (JaCoCo) y checkstyle
mvn test -Dtest=ClaseTest#metodo   # un solo test
mvn checkstyle:check               # solo checkstyle
```

- La fase `test` falla si la cobertura de complejidad (JaCoCo) es menor a 80 %.
- Checkstyle (`src/main/resources/checkstyle.xml`) falla con advertencias: líneas de máximo 120 caracteres,
  Javadoc obligatorio, sin tabuladores ni imports con `*`.

### Convención de idioma

- **Español:** comentarios, Javadoc y este README.
- **Inglés:** nombres de clases, métodos y variables, mensajes de log y mensajes de excepciones.
