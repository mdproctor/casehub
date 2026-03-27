package io.casehub.worker;

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
 * @see Worker
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

    private void validateExecutor(String workerId) throws UnauthorizedException {
        if (!workers.containsKey(workerId)) {
            throw new UnauthorizedException("Unknown worker: " + workerId);
        }
    }
}
