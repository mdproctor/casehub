package io.casehub.persistence.hibernate;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;
import io.casehub.worker.Task;
import io.casehub.worker.TaskOrigin;
import io.casehub.worker.TaskStatus;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * JPA entity implementing {@link Task}. Context is stored as a JSON blob.
 * RequiredCapabilities stored as a JSON array. PropagationContext reconstructed
 * from flat columns. Owning CaseFile linked via {@code @ManyToOne} to {@link HibernateCaseFile}.
 * Child tasks linked via self-referential {@code @OneToMany}.
 */
@Entity
@Table(name = "tasks")
public class HibernateTask implements Task {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "task_seq")
    @SequenceGenerator(name = "task_seq", sequenceName = "task_seq", allocationSize = 50)
    private Long id;

    @Version
    private Long version;

    @Column(name = "otel_span_id", nullable = false, updatable = false, length = 36)
    private String otelSpanIdStr;

    @Column(name = "task_type", nullable = false)
    private String taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_origin", nullable = false)
    private TaskOrigin taskOrigin = TaskOrigin.BROKER_ALLOCATED;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "assigned_worker_id")
    private String assignedWorkerId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    // Context: task-specific key-value data as JSON
    @Convert(converter = ObjectMapConverter.class)
    @Column(name = "context", columnDefinition = "TEXT")
    private Map<String, Object> context = new HashMap<>();

    // Required capabilities as JSON array
    @Convert(converter = StringSetConverter.class)
    @Column(name = "required_capabilities", columnDefinition = "TEXT")
    private Set<String> requiredCapabilities = new HashSet<>();

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

    // Graph
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owning_case_id")
    private HibernateCaseFile owningCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private HibernateTask parentTask;

    @OneToMany(mappedBy = "parentTask", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<HibernateTask> childTasks = new ArrayList<>();

    protected HibernateTask() {}

    HibernateTask(String taskType, Map<String, Object> context,
                  Set<String> requiredCapabilities, PropagationContext propagationContext,
                  HibernateCaseFile owningCase) {
        this.otelSpanIdStr = UUID.randomUUID().toString();
        this.submittedAt = Instant.now();
        this.taskType = taskType;
        this.context = context != null ? new HashMap<>(context) : new HashMap<>();
        this.requiredCapabilities = requiredCapabilities != null ? new HashSet<>(requiredCapabilities) : new HashSet<>();
        applyPropagationContext(propagationContext);
        this.owningCase = owningCase;
    }

    private void applyPropagationContext(PropagationContext ctx) {
        if (ctx == null) return;
        this.traceId = ctx.getTraceId();
        this.inheritedAttributes = new HashMap<>(ctx.getInheritedAttributes());
        this.deadline = ctx.getDeadline().orElse(null);
        this.remainingBudgetSeconds = ctx.getRemainingBudget().map(Duration::getSeconds).orElse(null);
    }

    // --- Identity ---
    @Override public Long getId()          { return id; }
    @Override public Long getVersion()     { return version; }
    @Override public UUID getOtelSpanId() { return UUID.fromString(otelSpanIdStr); }

    // --- Task data ---
    @Override public String getTaskType()                          { return taskType; }
    @Override public void setTaskType(String t)                    { this.taskType = t; }
    @Override public Map<String, Object> getContext()              { return context; }
    @Override public void setContext(Map<String, Object> c)        { this.context = c != null ? c : new HashMap<>(); }
    @Override public Set<String> getRequiredCapabilities()         { return requiredCapabilities; }
    @Override public void setRequiredCapabilities(Set<String> c)   { this.requiredCapabilities = c != null ? c : new HashSet<>(); }

    // --- Lifecycle ---
    @Override public TaskStatus getStatus()      { return status; }
    @Override public void setStatus(TaskStatus s){ this.status = s; }
    @Override public Instant getSubmittedAt()    { return submittedAt; }

    @Override public Optional<String> getAssignedWorkerId() {
        return Optional.ofNullable(assignedWorkerId);
    }

    @Override public void setAssignedWorkerId(String workerId) {
        this.assignedWorkerId = workerId;
        this.assignedAt = workerId != null ? Instant.now() : null;
    }

    @Override public Optional<Instant> getAssignedAt() {
        return Optional.ofNullable(assignedAt);
    }

    @Override public TaskOrigin getTaskOrigin()        { return taskOrigin; }
    @Override public void setTaskOrigin(TaskOrigin o)  { this.taskOrigin = o; }

    // --- Context propagation ---
    @Override public PropagationContext getPropagationContext() {
        if (traceId == null) return null;
        Duration budget = remainingBudgetSeconds != null
                ? Duration.ofSeconds(remainingBudgetSeconds) : null;
        return PropagationContext.fromStorage(traceId, inheritedAttributes, deadline, budget);
    }

    @Override public void setPropagationContext(PropagationContext ctx) {
        applyPropagationContext(ctx);
    }

    // --- Graph ---
    @Override public Optional<CaseFile> getOwningCase() { return Optional.ofNullable(owningCase); }

    @Override public void setOwningCase(CaseFile caseFile) {
        if (caseFile instanceof HibernateCaseFile hcf) {
            this.owningCase = hcf;
        } else if (caseFile == null) {
            this.owningCase = null;
        } else {
            throw new IllegalArgumentException(
                    "HibernateTask.setOwningCase requires a HibernateCaseFile, got: "
                            + caseFile.getClass().getName());
        }
    }

    @Override public List<Task> getChildTasks() { return Collections.unmodifiableList(childTasks); }

    @Override public void addChildTask(Task task) {
        if (task instanceof HibernateTask ht) {
            ht.parentTask = this;
            childTasks.add(ht);
        } else {
            throw new IllegalArgumentException(
                    "HibernateTask.addChildTask requires a HibernateTask, got: "
                            + task.getClass().getName());
        }
    }

    // Package-private wiring
    void setOwningCaseDirect(HibernateCaseFile owningCase) { this.owningCase = owningCase; }
}
