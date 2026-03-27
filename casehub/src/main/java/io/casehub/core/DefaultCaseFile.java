package io.casehub.core;

import io.casehub.coordination.PropagationContext;
import io.casehub.error.ErrorInfo;
import io.casehub.error.StaleVersionException;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * In-memory MVP implementation of the {@link CaseFile} interface. Uses a {@link ConcurrentHashMap}
 * to store key-value entries wrapped in {@link CaseFileItem}, with an {@link AtomicLong} tracking
 * the CaseFile-level version that increments on every write. Supports optimistic concurrency control
 * via {@link #putIfVersion(String, Object, long)}, per-key and any-change listeners, and atomic
 * lifecycle transitions through {@link CaseStatus}. Fully thread-safe.
 *
 * @see CaseFile
 * @see CaseFileItem
 * @see CaseFileItemEvent
 */
public class DefaultCaseFile implements CaseFile {

    private final String caseFileId;
    private final String caseType;
    private final PropagationContext propagationContext;
    private final Instant createdAt;

    private final ConcurrentHashMap<String, CaseFileItem> store = new ConcurrentHashMap<>();
    private final AtomicLong caseFileVersion = new AtomicLong(0);
    private final AtomicReference<CaseStatus> status = new AtomicReference<>(CaseStatus.PENDING);

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<CaseFileItemEvent>>> keyListeners = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<CaseFileItemEvent>> anyChangeListeners = new CopyOnWriteArrayList<>();

    public DefaultCaseFile(String caseFileId, String caseType, Map<String, Object> initialState,
                        PropagationContext propagationContext) {
        this.caseFileId = caseFileId;
        this.caseType = caseType;
        this.propagationContext = propagationContext;
        this.createdAt = Instant.now();

        if (initialState != null) {
            for (Map.Entry<String, Object> entry : initialState.entrySet()) {
                long version = caseFileVersion.incrementAndGet();
                store.put(entry.getKey(), new CaseFileItem(entry.getValue(), version, null));
            }
        }
    }

    @Override
    public String getCaseFileId() {
        return caseFileId;
    }

    public String getCaseType() {
        return caseType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        CaseFileItem vv = store.get(key);
        if (vv == null) {
            return Optional.empty();
        }
        return Optional.of(type.cast(vv.getValue()));
    }

    @Override
    public boolean contains(String key) {
        return store.containsKey(key);
    }

    @Override
    public Set<String> keys() {
        return Collections.unmodifiableSet(store.keySet());
    }

    @Override
    public Map<String, Object> snapshot() {
        Map<String, Object> snap = new HashMap<>();
        for (Map.Entry<String, CaseFileItem> entry : store.entrySet()) {
            snap.put(entry.getKey(), entry.getValue().getValue());
        }
        return Collections.unmodifiableMap(snap);
    }

    @Override
    public void put(String key, Object value) {
        CaseFileItem previous = store.get(key);
        long newVersion = caseFileVersion.incrementAndGet();
        store.put(key, new CaseFileItem(value, newVersion, null));
        fireCaseFileItemEvent(key, value, previous);
    }

    @Override
    public void putIfAbsent(String key, Object value) {
        long newVersion = caseFileVersion.incrementAndGet();
        CaseFileItem newVv = new CaseFileItem(value, newVersion, null);
        CaseFileItem existing = store.putIfAbsent(key, newVv);
        if (existing != null) {
            // Key already existed; the version bump is harmless but no event fires
            return;
        }
        fireCaseFileItemEvent(key, value, null);
    }

    @Override
    public void onChange(String key, Consumer<CaseFileItemEvent> listener) {
        keyListeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public void onAnyChange(Consumer<CaseFileItemEvent> listener) {
        anyChangeListeners.add(listener);
    }

    @Override
    public long getVersion() {
        return caseFileVersion.get();
    }

    @Override
    public void putIfVersion(String key, Object value, long expectedVersion) throws StaleVersionException {
        synchronized (key.intern()) {
            long currentKeyVersion = getKeyVersion(key);
            if (currentKeyVersion != expectedVersion) {
                throw new StaleVersionException(key, expectedVersion, currentKeyVersion);
            }
            CaseFileItem previous = store.get(key);
            long newVersion = caseFileVersion.incrementAndGet();
            store.put(key, new CaseFileItem(value, newVersion, null));
            fireCaseFileItemEvent(key, value, previous);
        }
    }

    @Override
    public long getKeyVersion(String key) {
        CaseFileItem vv = store.get(key);
        return vv != null ? vv.getVersion() : 0;
    }

    @Override
    public PropagationContext getPropagationContext() {
        return propagationContext;
    }

    @Override
    public CaseStatus getStatus() {
        return status.get();
    }

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public void complete() {
        while (true) {
            CaseStatus current = status.get();
            if (current == CaseStatus.COMPLETED) {
                return;
            }
            if (isTerminal(current)) {
                throw new IllegalStateException(
                        "Cannot complete CaseFile in " + current + " state");
            }
            if (status.compareAndSet(current, CaseStatus.COMPLETED)) {
                return;
            }
        }
    }

    @Override
    public void fail(ErrorInfo error) {
        while (true) {
            CaseStatus current = status.get();
            if (current == CaseStatus.FAULTED) {
                return;
            }
            if (isTerminal(current)) {
                throw new IllegalStateException(
                        "Cannot fail CaseFile in " + current + " state");
            }
            if (status.compareAndSet(current, CaseStatus.FAULTED)) {
                return;
            }
        }
    }

    public void setStatus(CaseStatus newStatus) {
        status.set(newStatus);
    }

    private boolean isTerminal(CaseStatus s) {
        return s == CaseStatus.COMPLETED || s == CaseStatus.FAULTED
                || s == CaseStatus.CANCELLED || s == CaseStatus.FAULTED;
    }

    private void fireCaseFileItemEvent(String key, Object value, CaseFileItem previous) {
        Optional<Object> previousValue = previous != null
                ? Optional.of(previous.getValue())
                : Optional.empty();
        CaseFileItemEvent event = new CaseFileItemEvent(caseFileId, key, value, previousValue, Optional.empty());

        List<Consumer<CaseFileItemEvent>> perKey = keyListeners.get(key);
        if (perKey != null) {
            for (Consumer<CaseFileItemEvent> listener : perKey) {
                listener.accept(event);
            }
        }
        for (Consumer<CaseFileItemEvent> listener : anyChangeListeners) {
            listener.accept(event);
        }
    }
}
