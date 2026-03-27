package io.casehub.worker;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory storage and lifecycle management for {@link Task Tasks} and their
 * {@link TaskResult results}. Manages task state transitions, provides queries by status
 * and worker, and handles cleanup. The MVP implementation uses {@link java.util.concurrent.ConcurrentHashMap}.
 *
 * @see Task
 * @see TaskResult
 */
@ApplicationScoped
public class TaskRegistry {

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final Map<String, TaskResult> results = new ConcurrentHashMap<>();

    public void store(Task task) {
        tasks.put(task.getTaskId(), task);
    }

    public Optional<Task> get(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    public void updateStatus(String taskId, TaskStatus newStatus) {
        Task task = tasks.get(taskId);
        if (task != null) {
            synchronized (task) {
                task.setStatus(newStatus);
            }
        }
    }

    public void storeResult(String taskId, TaskResult result) {
        results.put(taskId, result);
        updateStatus(taskId, result.getStatus());
    }

    public Optional<TaskResult> getResult(String taskId) {
        return Optional.ofNullable(results.get(taskId));
    }

    public List<Task> findByStatus(TaskStatus status) {
        return tasks.values().stream()
                .filter(t -> t.getStatus() == status)
                .toList();
    }

    public List<Task> findByWorker(String workerId) {
        return tasks.values().stream()
                .filter(t -> t.getAssignedWorkerId().map(id -> id.equals(workerId)).orElse(false))
                .toList();
    }

    public void delete(String taskId) {
        tasks.remove(taskId);
        results.remove(taskId);
    }
}
