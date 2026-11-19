package reactor.core.publisher;

/**
 * Minimal fake of {@code Mono}, only to test that the library rejects reactive types by the name of the
 * class without depending on Reactor (which is not on the classpath).
 *
 * @param <T> the type of the value
 * @author Java Universe
 */
public class Mono<T> {
}
