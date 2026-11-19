package com.juniverse.client.example.infrastructure.adapter.out.dummyjson;

import com.juniverse.client.example.domain.exception.DomainException;
import com.juniverse.client.example.domain.exception.ExternalServiceException;
import com.juniverse.client.example.domain.exception.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Translates the technical errors of the DummyJSON clients into domain exceptions.
 *
 * The adapters first handle the cases with business meaning (a 404 is "does not exist", a 400 when
 * logging in is "invalid credentials") and delegate the rest here, so that no exception from
 * Spring, the library or Resilience4j leaves the adapter.
 *
 * @author Java Universe
 */
@Component
public class DummyJsonErrorTranslator {

    private static final Logger LOG = LoggerFactory.getLogger(DummyJsonErrorTranslator.class);

    /**
     * Translates a technical error.
     *
     * <ul>
     *   <li>A 4xx that the external service rejected: {@link InvalidRequestException}.</li>
     *   <li>Any other (5xx, network, timeout, open circuit): {@link ExternalServiceException}.</li>
     * </ul>
     *
     * @param operation what was being attempted, for the message (for example {@code list products})
     * @param error the technical error
     * @return the domain exception
     */
    public DomainException translate(String operation, RuntimeException error) {
        if (error instanceof DomainException domainException) {
            return domainException;
        }
        if (error instanceof HttpClientErrorException clientError) {
            LOG.warn("DummyJSON rejected '{}' with status {}", operation, clientError.getStatusCode().value());
            return new InvalidRequestException("DummyJSON rejected the request to " + operation + " (status "
                    + clientError.getStatusCode().value() + ")");
        }
        LOG.warn("DummyJSON is unavailable while trying to {}: {}", operation, error.toString());
        return new ExternalServiceException("DummyJSON is unavailable while trying to " + operation, error);
    }
}
