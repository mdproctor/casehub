package io.casehub.core.spi;

import io.casehub.worker.TaskStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * SPI for persisting Task state in the request-response model. Provides task CRUD,
 * result management, queries by status or worker, and TTL-based cleanup. The MVP
 * implementation is {@code InMemoryTaskStorage}, which uses
 * {@link java.util.concurrent.ConcurrentHashMap}.
 *
 * @see <a href="doc/spec.md#3.4">Spec section 3.4</a>
 */
public interface TaskStorageProvider {
    // Task CRUD
    void storeTask(String taskId, TaskData data);
    Optional<TaskData> retrieveTask(String taskId);
    void updateTaskStatus(String taskId, TaskStatus newStatus);
    void delete(String taskId);

    // Result management
    void storeResult(String taskId, TaskResult result);
    Optional<TaskResult> retrieveResult(String taskId);

    // Queries
    List<TaskData> findTasksByStatus(TaskStatus status);
    List<TaskData> findTasksByWorker(String workerId);

    // Lifecycle
    void cleanup(Predicate<TaskData> shouldDelete);

    /**
     * Read-only view of a stored task's core data, including its identifier, type,
     * execution context, and current status.
     */
    interface TaskData {
        String getTaskId();
        String getTaskType();
        Map<String, Object> getContext();
        TaskStatus getStatus();
    }

    /**
     * Read-only view of a task's execution result, carrying the final status and
     * any output data produced by the worker.
     */
    interface TaskResult {
        String getTaskId();
        TaskStatus getStatus();
        Map<String, Object> getData();
    }
}
