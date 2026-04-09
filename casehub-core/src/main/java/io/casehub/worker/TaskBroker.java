package io.casehub.worker;

import io.casehub.error.TaskSubmissionException;
import io.casehub.resilience.IdempotencyService;
import io.casehub.resilience.PoisonPillDetector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * The requestor-facing orchestration service for the Task model. Serves as the entry point
 * for task submission, cancellation, and result retrieval, delegating to {@link TaskRegistry}
 * for storage and {@link TaskScheduler} for routing.
 *
 * @see TaskRequest
 * @see TaskHandle
 */
@ApplicationScoped
public class TaskBroker {

    @Inject
    TaskRegistry taskRegistry;

    @Inject
    TaskScheduler taskScheduler;

    @Inject
    PoisonPillDetector poisonPillDetector;

    @Inject
    IdempotencyService idempotencyService;

    private final Map<String, DefaultTaskHandle> handles = new ConcurrentHashMap<>();

    public TaskHandle submitTask(TaskRequest request) throws TaskSubmissionException {
        // Check for quarantined task types
        if (poisonPillDetector.isQuarantined(request.getTaskType())) {
            throw new TaskSubmissionException(
                    "Task type '" + request.getTaskType() + "' is quarantined due to repeated failures");
        }

        // Check idempotency
        Optional<String> idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey.isPresent() && idempotencyService.isAlreadyProcessed(idempotencyKey.get())) {
            Optional<Object> cached = idempotencyService.getCachedResult(idempotencyKey.get());
            if (cached.isPresent() && cached.get() instanceof DefaultTaskHandle cachedHandle) {
                return cachedHandle;
            }
        }

        // Create task from request and store it
        Task task = new DefaultTask(request);
        taskRegistry.store(task);

        // Create and store the handle
        DefaultTaskHandle handle = new DefaultTaskHandle(task, taskRegistry);
        handles.put(task.getId().toString(), handle);

        // Try to assign a worker
        Optional<Worker> worker = taskScheduler.selectWorker(task);
        if (worker.isPresent()) {
            task.setAssignedWorkerId(worker.get().getWorkerId());
            taskRegistry.updateStatus(task.getId().toString(), TaskStatus.ASSIGNED);
        }

        // Cache handle for idempotency
        idempotencyKey.ifPresent(key -> idempotencyService.markProcessed(key, handle));

        return handle;
    }

    public TaskHandle submitTask(TaskRequest request, Duration timeout) throws TaskSubmissionException {
        // Timeout enforcement is handled by TimeoutEnforcer; delegate to the main submission path
        return submitTask(request);
    }

    public boolean cancelTask(TaskHandle handle) {
        Optional<Task> taskOpt = taskRegistry.get(handle.getTaskId());
        if (taskOpt.isEmpty()) {
            return false;
        }

        Task task = taskOpt.get();
        TaskStatus status = task.getStatus();
        if (isTerminal(status)) {
            return false;
        }

        taskRegistry.updateStatus(task.getId().toString(), TaskStatus.CANCELLED);

        // Complete the handle's future with a cancelled result
        if (handle instanceof DefaultTaskHandle defaultHandle) {
            TaskResult cancelledResult = new TaskResult(task.getId().toString(), TaskStatus.CANCELLED, Map.of());
            defaultHandle.completeWith(cancelledResult);
        }

        return true;
    }

    public TaskResult awaitResult(TaskHandle handle, Duration timeout)
            throws InterruptedException, TimeoutException {
        return handle.awaitResult(timeout);
    }

    public TaskStatus getStatus(TaskHandle handle) {
        return handle.getStatus();
    }

    private static boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.COMPLETED
                || status == TaskStatus.FAULTED
                || status == TaskStatus.CANCELLED
                || status == TaskStatus.FAULTED;
    }
}
