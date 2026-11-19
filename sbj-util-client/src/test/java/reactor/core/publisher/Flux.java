package reactor.core.publisher;

/**
 * Minimal fake of {@code Flux}, only to test that the library rejects reactive types by the name of the
 * class without depending on Reactor (which is not on the classpath).
 *
 * @param <T> the type of the values
 * @author Java Universe
 */
public class Flux<T> {
}
