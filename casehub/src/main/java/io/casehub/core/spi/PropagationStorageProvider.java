package io.casehub.core.spi;

import io.casehub.coordination.PropagationContext;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting {@link PropagationContext} instances and their lineage data.
 * Supports storage and retrieval by span ID, queries by trace ID or parent span ID,
 * and cleanup of entire trace hierarchies by trace ID. The MVP implementation is
 * {@code InMemoryPropagationStorage}.
 *
 * @see <a href="doc/spec.md#3.4">Spec section 3.4</a>
 * @see <a href="doc/spec.md#5.2">Spec section 5.2</a>
 */
public interface PropagationStorageProvider {
    void storeContext(String spanId, PropagationContext context);
    Optional<PropagationContext> retrieveContext(String spanId);
    List<PropagationContext> findByTraceId(String traceId);
    List<PropagationContext> findByParentSpanId(String parentSpanId);
    void deleteByTraceId(String traceId);
}
