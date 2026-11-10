package reactor.core.publisher;

/**
 * Falso mínimo de {@code Mono}, solo para probar que la librería rechaza los tipos reactivos por el nombre de la
 * clase sin depender de Reactor (que no está en el classpath).
 *
 * @param <T> el tipo del valor
 * @author Java Universe
 */
public class Mono<T> {
}
