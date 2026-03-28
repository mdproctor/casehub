package io.casehub.core.spi;

import io.casehub.coordination.PropagationContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link PropagationStorageProvider} using
 * {@link ConcurrentHashMap}. Thread-safe for concurrent access. All data
 * is lost on application restart.
 */
@ApplicationScoped
public class InMemoryPropagationStorage implements PropagationStorageProvider {

    private final Map<String, PropagationContext> storage = new ConcurrentHashMap<>();

    @Override
    public void storeContext(String spanId, PropagationContext context) {
        storage.put(spanId, context);
    }

    @Override
    public Optional<PropagationContext> retrieveContext(String spanId) {
        return Optional.ofNullable(storage.get(spanId));
    }

    @Override
    public List<PropagationContext> findByTraceId(String traceId) {
        return storage.values().stream()
                .filter(ctx -> ctx.getTraceId().equals(traceId))
                .collect(Collectors.toList());
    }

    @Override
    public List<PropagationContext> findByParentSpanId(String parentSpanId) {
        return storage.values().stream()
                .filter(ctx -> ctx.getParentSpanId().map(p -> p.equals(parentSpanId)).orElse(false))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByTraceId(String traceId) {
        storage.entrySet().removeIf(entry -> entry.getValue().getTraceId().equals(traceId));
    }
}
