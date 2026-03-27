package io.casehub.core;

import io.casehub.coordination.PropagationContext;
import io.casehub.error.ErrorInfo;
import io.casehub.error.StaleVersionException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The shared workspace at the heart of the CaseHub architecture. A CaseFile is a key-value data
 * space where multiple {@link TaskDefinition}s read and write contributions, with the solution
 * emerging incrementally. Supports versioned writes for optimistic concurrency, change listeners,
 * and carries a {@link io.casehub.coordination.PropagationContext} for hierarchical tracing.
 *
 * @see CaseFileItemEvent
 * @see CaseStatus
 */
public interface CaseFile {
    String getCaseFileId();

    // Read shared state
    <T> Optional<T> get(String key, Class<T> type);
    boolean contains(String key);
    Set<String> keys();
    Map<String, Object> snapshot();

    // Write contributions (triggers change events)
    void put(String key, Object value);
    void putIfAbsent(String key, Object value);

    // React to changes
    void onChange(String key, Consumer<CaseFileItemEvent> listener);
    void onAnyChange(Consumer<CaseFileItemEvent> listener);

    // Versioning (for optimistic concurrency)
    long getVersion();
    void putIfVersion(String key, Object value, long expectedVersion) throws StaleVersionException;
    long getKeyVersion(String key);

    // Context propagation
    PropagationContext getPropagationContext();

    // Lifecycle
    CaseStatus getStatus();
    Instant getCreatedAt();
    void complete();
    void fail(ErrorInfo error);
}
