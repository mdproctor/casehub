package io.casehub.persistence.memory;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;
import io.casehub.worker.Task;
import io.casehub.worker.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryTaskRepositoryTest {

    private InMemoryCaseFileRepository caseFileRepository;
    private InMemoryTaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        caseFileRepository = new InMemoryCaseFileRepository();
        taskRepository = new InMemoryTaskRepository();
    }

    @Test
    void createReturnsTaskWithUniqueId() {
        PropagationContext ctx = PropagationContext.createRoot();
        Task t1 = taskRepository.create("type-a", Map.of(), Set.of(), ctx, null);
        Task t2 = taskRepository.create("type-b", Map.of(), Set.of(), ctx, null);
        assertNotNull(t1.getId());
        assertNotEquals(t1.getId(), t2.getId());
    }

    @Test
    void createLinksCaseFile() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf = caseFileRepository.create("case", Map.of(), ctx);
        Task task = taskRepository.create("my-task", Map.of(), Set.of(), ctx, cf);

        assertTrue(task.getOwningCase().isPresent());
        assertEquals(cf.getId(), task.getOwningCase().get().getId());
        assertEquals(1, cf.getTasks().size());
        assertEquals(task.getId(), cf.getTasks().get(0).getId());
    }

    @Test
    void findByIdWorks() {
        PropagationContext ctx = PropagationContext.createRoot();
        Task task = taskRepository.create("type-a", Map.of(), Set.of(), ctx, null);
        Optional<Task> found = taskRepository.findById(task.getId());
        assertTrue(found.isPresent());
        assertEquals(task.getId(), found.get().getId());
    }

    @Test
    void findByStatusFiltersCorrectly() {
        PropagationContext ctx = PropagationContext.createRoot();
        Task t1 = taskRepository.create("type-a", Map.of(), Set.of(), ctx, null);
        Task t2 = taskRepository.create("type-b", Map.of(), Set.of(), ctx, null);
        t1.setStatus(TaskStatus.ASSIGNED);

        List<Task> assigned = taskRepository.findByStatus(TaskStatus.ASSIGNED);
        assertEquals(1, assigned.size());
        assertEquals(t1.getId(), assigned.get(0).getId());

        List<Task> pending = taskRepository.findByStatus(TaskStatus.PENDING);
        assertEquals(1, pending.size());
        assertEquals(t2.getId(), pending.get(0).getId());
    }

    @Test
    void findByWorkerFiltersCorrectly() {
        PropagationContext ctx = PropagationContext.createRoot();
        Task t1 = taskRepository.create("type-a", Map.of(), Set.of(), ctx, null);
        Task t2 = taskRepository.create("type-b", Map.of(), Set.of(), ctx, null);
        t1.setAssignedWorkerId("worker-1");

        List<Task> worker1Tasks = taskRepository.findByWorker("worker-1");
        assertEquals(1, worker1Tasks.size());
        assertEquals(t1.getId(), worker1Tasks.get(0).getId());

        assertTrue(taskRepository.findByWorker("worker-2").isEmpty());
    }
}
