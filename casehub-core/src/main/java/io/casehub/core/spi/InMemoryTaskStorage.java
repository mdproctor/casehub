package io.casehub.core.spi;

import io.casehub.worker.TaskStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link TaskStorageProvider} using
 * {@link ConcurrentHashMap}. Thread-safe for concurrent access. All data
 * is lost on application restart.
 */
@ApplicationScoped
public class InMemoryTaskStorage implements TaskStorageProvider {

    private final Map<String, TaskDataImpl> tasks = new ConcurrentHashMap<>();
    private final Map<String, TaskResultImpl> results = new ConcurrentHashMap<>();

    @Override
    public void storeTask(String taskId, TaskData data) {
        tasks.put(taskId, new TaskDataImpl(
                data.getTaskId(),
                data.getTaskType(),
                data.getContext(),
                data.getStatus()
        ));
    }

    @Override
    public Optional<TaskData> retrieveTask(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public void updateTaskStatus(String taskId, TaskStatus newStatus) {
        TaskDataImpl task = tasks.get(taskId);
        if (task != null) {
            task.status = newStatus;
        }
    }

    @Override
    public void delete(String taskId) {
        tasks.remove(taskId);
        results.remove(taskId);
    }

    @Override
    public void storeResult(String taskId, TaskResult result) {
        results.put(taskId, new TaskResultImpl(
                result.getTaskId(),
                result.getStatus(),
                result.getData()
        ));
    }

    @Override
    public Optional<TaskResult> retrieveResult(String taskId) {
        return Optional.ofNullable(results.get(taskId));
    }

    @Override
    public List<TaskData> findTasksByStatus(TaskStatus status) {
        return tasks.values().stream()
                .filter(task -> task.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskData> findTasksByWorker(String workerId) {
        return tasks.values().stream()
                .filter(task -> {
                    Object worker = task.getContext().get("workerId");
                    return worker != null && worker.equals(workerId);
                })
                .collect(Collectors.toList());
    }

    @Override
    public void cleanup(Predicate<TaskData> shouldDelete) {
        List<String> toDelete = tasks.values().stream()
                .filter(shouldDelete)
                .map(TaskData::getTaskId)
                .collect(Collectors.toList());
        toDelete.forEach(this::delete);
    }

    private static class TaskDataImpl implements TaskData {
        private final String taskId;
        private final String taskType;
        private final Map<String, Object> context;
        private TaskStatus status;

        TaskDataImpl(String taskId, String taskType, Map<String, Object> context, TaskStatus status) {
            this.taskId = taskId;
            this.taskType = taskType;
            this.context = new ConcurrentHashMap<>(context);
            this.status = status;
        }

        @Override
        public String getTaskId() {
            return taskId;
        }

        @Override
        public String getTaskType() {
            return taskType;
        }

        @Override
        public Map<String, Object> getContext() {
            return context;
        }

        @Override
        public TaskStatus getStatus() {
            return status;
        }
    }

    private static class TaskResultImpl implements TaskResult {
        private final String taskId;
        private final TaskStatus status;
        private final Map<String, Object> data;

        TaskResultImpl(String taskId, TaskStatus status, Map<String, Object> data) {
            this.taskId = taskId;
            this.status = status;
            this.data = new ConcurrentHashMap<>(data);
        }

        @Override
        public String getTaskId() {
            return taskId;
        }

        @Override
        public TaskStatus getStatus() {
            return status;
        }

        @Override
        public Map<String, Object> getData() {
            return data;
        }
    }
}
