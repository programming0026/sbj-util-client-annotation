package reactor.core.publisher;

/**
 * Falso mínimo de {@code Flux}, solo para probar que la librería rechaza los tipos reactivos por el nombre de la
 * clase sin depender de Reactor (que no está en el classpath).
 *
 * @param <T> el tipo de los valores
 * @author Java Universe
 */
public class Flux<T> {
}
