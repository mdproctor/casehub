package io.casehub.persistence.hibernate;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;
import io.casehub.core.CaseFileItemEvent;
import io.casehub.core.CaseStatus;
import io.casehub.error.ErrorInfo;
import io.casehub.error.StaleVersionException;
import io.casehub.worker.Task;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * JPA entity implementing {@link CaseFile}. Workspace items are stored as a single JSON blob
 * in a TEXT column. Per-key versions are stored as a separate JSON map. PropagationContext
 * is reconstructed from stored columns via {@link PropagationContext#fromStorage}.
 *
 * <p>Graph relationships: parent/children via {@code @ManyToOne}/{@code @OneToMany},
 * tasks via {@code @OneToMany} to {@link HibernateTask}.
 *
 * <p>Listeners are {@code @Transient} — not persisted, reset on entity load.
 */
@Entity
@Table(name = "case_files")
public class HibernateCaseFile implements CaseFile {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "case_file_seq")
    @SequenceGenerator(name = "case_file_seq", sequenceName = "case_file_seq", allocationSize = 50)
    private Long id;

    @Version
    private Long version;

    @Column(name = "otel_trace_id", nullable = false, updatable = false, length = 36)
    private String otelTraceIdStr;

    @Column(name = "case_type", nullable = false, updatable = false)
    private String caseType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status = CaseStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // PropagationContext fields
    @Column(name = "trace_id", nullable = false)
    private String traceId;

    @Convert(converter = StringMapConverter.class)
    @Column(name = "inherited_attributes", columnDefinition = "TEXT")
    private Map<String, String> inheritedAttributes = new HashMap<>();

    @Column(name = "deadline")
    private Instant deadline;

    @Column(name = "remaining_budget_seconds")
    private Long remainingBudgetSeconds;

    // Workspace: entire key-value map as a JSON blob
    @Convert(converter = ObjectMapConverter.class)
    @Column(name = "items", columnDefinition = "TEXT")
    private Map<String, Object> items = new HashMap<>();

    // Per-key versions stored as JSON (key -> version string)
    @Convert(converter = StringMapConverter.class)
    @Column(name = "item_versions", columnDefinition = "TEXT")
    private Map<String, String> itemVersions = new HashMap<>();

    // Graph
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_case_id")
    private HibernateCaseFile parentCase;

    @OneToMany(mappedBy = "parentCase", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<HibernateCaseFile> childCases = new ArrayList<>();

    @OneToMany(mappedBy = "owningCase", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<HibernateTask> tasks = new ArrayList<>();

    // Ephemeral listeners (not persisted)
    @Transient
    private final Map<String, List<Consumer<CaseFileItemEvent>>> keyListeners = new HashMap<>();
    @Transient
    private final List<Consumer<CaseFileItemEvent>> anyChangeListeners = new CopyOnWriteArrayList<>();

    protected HibernateCaseFile() {}

    HibernateCaseFile(String caseType, Map<String, Object> initialState,
                      PropagationContext propagationContext) {
        this.caseType = caseType;
        this.otelTraceIdStr = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.traceId = propagationContext.getTraceId();
        this.inheritedAttributes = new HashMap<>(propagationContext.getInheritedAttributes());
        this.deadline = propagationContext.getDeadline().orElse(null);
        this.remainingBudgetSeconds = propagationContext.getRemainingBudget()
                .map(Duration::getSeconds).orElse(null);
        if (initialState != null) {
            initialState.forEach((k, v) -> {
                items.put(k, v);
                itemVersions.put(k, "1");
            });
        }
    }

    // --- Internal wiring (package-private) ---
    void setParentCase(HibernateCaseFile parent) { this.parentCase = parent; }
    void addChildCase(HibernateCaseFile child)   { this.childCases.add(child); }
    void addTask(HibernateTask task)             { this.tasks.add(task); }

    // --- Identity ---
    @Override public Long getId()          { return id; }
    @Override public Long getVersion()     { return version; }
    @Override public UUID getOtelTraceId() { return UUID.fromString(otelTraceIdStr); }
    @Override public String getCaseType()  { return caseType; }

    // --- Context ---
    @Override public PropagationContext getPropagationContext() {
        Duration budget = remainingBudgetSeconds != null
                ? Duration.ofSeconds(remainingBudgetSeconds) : null;
        return PropagationContext.fromStorage(traceId, inheritedAttributes, deadline, budget);
    }

    // --- Workspace reads ---
    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        Object val = items.get(key);
        if (val == null) return Optional.empty();
        return Optional.of(type.cast(val));
    }

    @Override public boolean contains(String key)       { return items.containsKey(key); }
    @Override public Set<String> keys()                 { return Collections.unmodifiableSet(items.keySet()); }
    @Override public Map<String, Object> snapshot()     { return Collections.unmodifiableMap(new HashMap<>(items)); }

    // --- Workspace writes ---
    @Override public void put(String key, Object value) {
        Object previous = items.get(key);
        items.put(key, value);
        itemVersions.merge(key, "1", (old, inc) -> String.valueOf(Long.parseLong(old) + 1));
        fireEvent(key, value, previous);
    }

    @Override public void putIfAbsent(String key, Object value) {
        if (!items.containsKey(key)) {
            items.put(key, value);
            itemVersions.put(key, "1");
            fireEvent(key, value, null);
        }
    }

    /**
     * Checks the per-key version and conditionally updates.
     * <p>
     * <strong>Must be called within an active transaction.</strong> The per-key
     * version check is performed in-memory and is not independently synchronized —
     * concurrent callers must be serialized by the surrounding transaction.
     * Hibernate's {@code @Version} on the entity provides row-level optimistic
     * locking at flush time, which complements but does not replace this check.
     */
    @Override public void putIfVersion(String key, Object value, long expectedVersion)
            throws StaleVersionException {
        long current = getKeyVersion(key);
        if (current != expectedVersion) throw new StaleVersionException(key, expectedVersion, current);
        put(key, value);
    }

    @Override public long getKeyVersion(String key) {
        String ver = itemVersions.get(key);
        return ver != null ? Long.parseLong(ver) : 0L;
    }

    // --- Listeners ---
    @Override public void onChange(String key, Consumer<CaseFileItemEvent> listener) {
        keyListeners.computeIfAbsent(key, k -> new ArrayList<>()).add(listener);
    }

    @Override public void onAnyChange(Consumer<CaseFileItemEvent> listener) {
        anyChangeListeners.add(listener);
    }

    // --- Graph ---
    @Override public Optional<CaseFile> getParentCase() { return Optional.ofNullable(parentCase); }
    @Override public List<CaseFile> getChildCases()     { return Collections.unmodifiableList(childCases); }
    @Override public List<Task> getTasks()              { return Collections.unmodifiableList(tasks); }

    // --- Lifecycle ---
    @Override public CaseStatus getStatus()       { return status; }
    @Override public void setStatus(CaseStatus s) { this.status = s; }
    @Override public Instant getCreatedAt()       { return createdAt; }

    @Override public void complete() {
        if (status == CaseStatus.COMPLETED) return;
        if (isTerminal(status)) throw new IllegalStateException("Cannot complete from " + status);
        status = CaseStatus.COMPLETED;
    }

    @Override public void fail(ErrorInfo error) {
        if (status == CaseStatus.FAULTED) return;
        if (isTerminal(status)) throw new IllegalStateException("Cannot fail from " + status);
        status = CaseStatus.FAULTED;
    }

    private boolean isTerminal(CaseStatus s) {
        return s == CaseStatus.COMPLETED || s == CaseStatus.FAULTED || s == CaseStatus.CANCELLED;
    }

    private void fireEvent(String key, Object value, Object previous) {
        CaseFileItemEvent event = new CaseFileItemEvent(
                id != null ? id.toString() : "?", key, value,
                Optional.ofNullable(previous), Optional.empty());
        List<Consumer<CaseFileItemEvent>> perKey = keyListeners.get(key);
        if (perKey != null) perKey.forEach(l -> l.accept(event));
        anyChangeListeners.forEach(l -> l.accept(event));
    }
}
