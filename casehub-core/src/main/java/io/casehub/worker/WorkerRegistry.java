package io.casehub.worker;

import io.casehub.coordination.PropagationContext;
import io.casehub.error.ErrorInfo;
import io.casehub.error.UnauthorizedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Worker lifecycle: registration with API key authentication, heartbeat tracking,
 * task claiming, and result/failure submission. Serves both the CaseFile model (where a
 * TaskDefinition may delegate to Workers) and the Task model.
 *
 * <p>Supports two task creation patterns:
 * <ul>
 *   <li><b>BROKER_ALLOCATED</b>: TaskBroker creates task, Worker claims via {@link #claimTask(String)}</li>
 *   <li><b>AUTONOMOUS</b>: Decentralized worker self-initiates work via {@link #notifyAutonomousWork}</li>
 * </ul>
 *
 * @see Worker
 * @see TaskOrigin
 */
@ApplicationScoped
public class WorkerRegistry {

    @Inject
    TaskRegistry taskRegistry;

    @Inject
    NotificationService notificationService;

    private final Map<String, Worker> workers = new ConcurrentHashMap<>();
    private final Map<String, String> apiKeys = new ConcurrentHashMap<>();

    public String register(String workerName, Set<String> capabilities, String apiKey)
            throws UnauthorizedException {
        // TODO: validate API key against configured keys
        String workerId = UUID.randomUUID().toString();
        Worker worker = new Worker(workerId, workerName, capabilities);
        workers.put(workerId, worker);
        apiKeys.put(workerId, apiKey);
        return workerId;
    }

    public void unregister(String workerId) {
        workers.remove(workerId);
        apiKeys.remove(workerId);
    }

    public void heartbeat(String workerId) {
        Worker worker = workers.get(workerId);
        if (worker != null) {
            worker.setLastHeartbeat(Instant.now());
        }
    }

    public Optional<Task> claimTask(String workerId) {
        validateExecutor(workerId);
        Worker worker = workers.get(workerId);
        Set<String> workerCapabilities = worker.getCapabilities();

        List<Task> pendingTasks = taskRegistry.findByStatus(TaskStatus.PENDING);
        for (Task task : pendingTasks) {
            Set<String> required = task.getRequiredCapabilities();
            if (required.isEmpty() || workerCapabilities.containsAll(required)) {
                task.setAssignedWorkerId(workerId);
                taskRegistry.updateStatus(task.getTaskId(), TaskStatus.ASSIGNED);
                return Optional.of(task);
            }
        }
        return Optional.empty();
    }

    public void submitResult(String workerId, String taskId, TaskResult result)
            throws UnauthorizedException {
        validateExecutor(workerId);
        Task task = taskRegistry.get(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown task: " + taskId));
        TaskStatus previousStatus = task.getStatus();
        result.setWorkerId(workerId);
        taskRegistry.storeResult(taskId, result);
        notificationService.publishTaskLifecycle(
                new NotificationService.TaskLifecycleEvent(taskId, previousStatus, result.getStatus()));
    }

    public void reportFailure(String workerId, String taskId, ErrorInfo error)
            throws UnauthorizedException {
        validateExecutor(workerId);
        Task task = taskRegistry.get(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown task: " + taskId));
        TaskStatus previousStatus = task.getStatus();
        TaskResult failureResult = TaskResult.failure(taskId, error);
        failureResult.setWorkerId(workerId);
        taskRegistry.storeResult(taskId, failureResult);
        notificationService.publishTaskLifecycle(
                new NotificationService.TaskLifecycleEvent(taskId, previousStatus, TaskStatus.FAULTED));
    }

    public List<Worker> getActiveWorkers() {
        return workers.values().stream()
                .filter(Worker::isActive)
                .toList();
    }

    public Optional<Worker> getById(String workerId) {
        return Optional.ofNullable(workers.get(workerId));
    }

    /**
     * Registers an autonomous task initiated by a decentralized worker.
     *
     * <p>Autonomous workers work on their own agency rather than waiting for TaskBroker allocation.
     * This method allows workers to notify the system when they start work, enabling:
     * <ul>
     *   <li>Task tracking as part of an overall case (via caseFileId)</li>
     *   <li>PropagationContext lineage for hierarchical tracing</li>
     *   <li>Integration with monitoring, metrics, and observability</li>
     *   <li>Support for sub-workers (children in the execution hierarchy)</li>
     * </ul>
     *
     * <p>The created Task is immediately in ASSIGNED state (worker already owns it).
     * The worker should call {@link #submitResult} when work completes.
     *
     * @param workerId Worker's unique identifier
     * @param taskType Type of task being performed
     * @param context Task context/parameters
     * @param caseFileId Optional CaseFile ID to associate this work with a case
     * @param parentContext Optional parent PropagationContext for lineage tracking
     * @return Created Task instance with AUTONOMOUS origin
     * @throws UnauthorizedException if worker is not registered
     */
    public Task notifyAutonomousWork(
            String workerId,
            String taskType,
            Map<String, Object> context,
            String caseFileId,
            PropagationContext parentContext) throws UnauthorizedException {
        validateExecutor(workerId);

        Task task = new Task();
        task.setTaskType(taskType);
        task.setContext(new HashMap<>(context));
        task.setTaskOrigin(TaskOrigin.AUTONOMOUS);
        task.setAssignedWorkerId(workerId);
        task.setStatus(TaskStatus.ASSIGNED);

        if (caseFileId != null) {
            task.setCaseFileId(caseFileId);
        }

        // Create or propagate PropagationContext for lineage tracking
        if (parentContext != null) {
            // This is a sub-worker - create child context
            task.setPropagationContext(parentContext.createChild(Map.of(
                    "workerId", workerId,
                    "taskType", taskType,
                    "origin", "autonomous"
            )));
        } else {
            // This is a root autonomous worker - create root context
            task.setPropagationContext(PropagationContext.createRoot(Map.of(
                    "workerId", workerId,
                    "taskType", taskType,
                    "origin", "autonomous"
            )));
        }

        // Store in TaskRegistry for tracking
        taskRegistry.store(task);

        // Notify lifecycle listeners
        notificationService.publishTaskLifecycle(
                new NotificationService.TaskLifecycleEvent(
                        task.getTaskId(),
                        TaskStatus.PENDING,
                        TaskStatus.ASSIGNED
                )
        );

        return task;
    }

    /**
     * Convenience overload for autonomous work without parent context.
     */
    public Task notifyAutonomousWork(
            String workerId,
            String taskType,
            Map<String, Object> context,
            String caseFileId) throws UnauthorizedException {
        return notifyAutonomousWork(workerId, taskType, context, caseFileId, null);
    }

    /**
     * Convenience overload for autonomous work without case association.
     */
    public Task notifyAutonomousWork(
            String workerId,
            String taskType,
            Map<String, Object> context) throws UnauthorizedException {
        return notifyAutonomousWork(workerId, taskType, context, null, null);
    }

    private void validateExecutor(String workerId) throws UnauthorizedException {
        if (!workers.containsKey(workerId)) {
            throw new UnauthorizedException("Unknown worker: " + workerId);
        }
    }
}
