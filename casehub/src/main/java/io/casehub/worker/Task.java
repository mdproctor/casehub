package io.casehub.worker;

import io.casehub.coordination.PropagationContext;

import java.time.Instant;
import java.util.*;

/**
 * A concrete, executable work unit in the request-response model. Created from a
 * {@link TaskRequest} by the {@link TaskBroker}, a Task tracks its lifecycle state,
 * assigned worker, and carries a {@link io.casehub.coordination.PropagationContext}
 * for hierarchical tracing.
 *
 * @see TaskRequest
 * @see TaskStatus
 */
public class Task {
    private String taskId;
    private String taskType;
    private Map<String, Object> context;
    private Set<String> requiredCapabilities;
    private TaskStatus status;
    private Instant submittedAt;
    private Optional<String> assignedWorkerId;
    private Optional<Instant> assignedAt;
    private PropagationContext propagationContext;

    public Task() {
        this.taskId = UUID.randomUUID().toString();
        this.context = new HashMap<>();
        this.requiredCapabilities = new HashSet<>();
        this.status = TaskStatus.PENDING;
        this.submittedAt = Instant.now();
        this.assignedWorkerId = Optional.empty();
        this.assignedAt = Optional.empty();
    }

    public Task(TaskRequest request) {
        this();
        this.taskType = request.getTaskType();
        this.context = new HashMap<>(request.getContext());
        this.requiredCapabilities = new HashSet<>(request.getRequiredCapabilities());
        this.propagationContext = request.getPropagationContext();
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }
    public Set<String> getRequiredCapabilities() { return requiredCapabilities; }
    public void setRequiredCapabilities(Set<String> caps) { this.requiredCapabilities = caps; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Optional<String> getAssignedWorkerId() { return assignedWorkerId; }
    public void setAssignedWorkerId(String workerId) {
        this.assignedWorkerId = Optional.ofNullable(workerId);
        this.assignedAt = Optional.of(Instant.now());
    }
    public Optional<Instant> getAssignedAt() { return assignedAt; }
    public PropagationContext getPropagationContext() { return propagationContext; }
    public void setPropagationContext(PropagationContext ctx) { this.propagationContext = ctx; }
}
