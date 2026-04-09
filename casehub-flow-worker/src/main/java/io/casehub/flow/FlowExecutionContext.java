package io.casehub.flow;

import io.casehub.coordination.PropagationContext;
import io.casehub.worker.Task;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Context passed to {@link FlowWorkflowDefinition} during execution.
 *
 * <p>Provides access to:
 * <ul>
 *   <li>Input data from the Task context</li>
 *   <li>PropagationContext for lineage tracking</li>
 *   <li>Task metadata (ID, type, status)</li>
 *   <li>Worker metadata</li>
 * </ul>
 *
 * <p>Workflows use this context to:
 * <ul>
 *   <li>Read input parameters</li>
 *   <li>Create child contexts for sub-workflows</li>
 *   <li>Access trace IDs for logging/monitoring</li>
 *   <li>Check deadlines/budgets</li>
 * </ul>
 */
public class FlowExecutionContext {

    private final Task task;
    private final String workerId;
    private final Map<String, Object> inputData;
    private final PropagationContext propagationContext;

    public FlowExecutionContext(Task task, String workerId) {
        this.task = task;
        this.workerId = workerId;
        this.inputData = new HashMap<>(task.getContext());
        this.propagationContext = task.getPropagationContext();
    }

    // ========== Input Data Access ==========

    /**
     * Get input parameter by key.
     *
     * @param key Parameter name
     * @return Value if present
     */
    public Optional<Object> getInput(String key) {
        return Optional.ofNullable(inputData.get(key));
    }

    /**
     * Get input parameter with type cast.
     *
     * @param key Parameter name
     * @param type Expected type
     * @param <T> Type parameter
     * @return Typed value if present and compatible
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getInput(String key, Class<T> type) {
        Object value = inputData.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    /**
     * Get all input data.
     *
     * @return Unmodifiable view of input data
     */
    public Map<String, Object> getAllInputs() {
        return Map.copyOf(inputData);
    }

    // ========== Task Metadata ==========

    /**
     * Get the Task ID.
     *
     * @return Unique task identifier
     */
    public String getTaskId() {
        return task.getId().toString();
    }

    /**
     * Get the task type (workflow ID).
     *
     * @return Task type
     */
    public String getTaskType() {
        return task.getTaskType();
    }

    /**
     * Get the full Task object.
     *
     * @return Task instance
     */
    public Task getTask() {
        return task;
    }

    /**
     * Get the worker ID executing this workflow.
     *
     * @return Worker ID
     */
    public String getWorkerId() {
        return workerId;
    }

    /**
     * Get the case file ID if this task is associated with a case.
     *
     * @return CaseFile ID if present
     */
    public Optional<String> getCaseFileId() {
        return task.getOwningCase().map(c -> c.getId().toString());
    }

    // ========== PropagationContext ==========

    /**
     * Get the PropagationContext for lineage tracking.
     *
     * @return PropagationContext
     */
    public PropagationContext getPropagationContext() {
        return propagationContext;
    }

    /**
     * Get the trace ID for distributed tracing.
     *
     * @return Trace ID
     */
    public String getTraceId() {
        return propagationContext.getTraceId();
    }

    /**
     * Check if budget is exhausted (for timeout/deadline enforcement).
     *
     * @return True if budget exhausted
     */
    public boolean isBudgetExhausted() {
        return propagationContext.isBudgetExhausted();
    }

    /**
     * Get inherited attribute from PropagationContext.
     *
     * @param key Attribute name
     * @return Attribute value if present
     */
    public Optional<String> getAttribute(String key) {
        return propagationContext.getAttribute(key);
    }

    /**
     * Create child context for sub-workflow execution.
     *
     * @param additionalAttributes Attributes to add to child context
     * @return Child PropagationContext
     */
    public PropagationContext createChildContext(Map<String, String> additionalAttributes) {
        return propagationContext.createChild(additionalAttributes);
    }

    @Override
    public String toString() {
        return "FlowExecutionContext{" +
                "taskId=" + task.getId() +
                ", taskType=" + task.getTaskType() +
                ", workerId=" + workerId +
                ", traceId=" + propagationContext.getTraceId() +
                '}';
    }
}
