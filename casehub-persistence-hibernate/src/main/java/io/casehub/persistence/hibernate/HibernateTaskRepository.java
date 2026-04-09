package io.casehub.persistence.hibernate;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;
import io.casehub.worker.Task;
import io.casehub.worker.TaskOrigin;
import io.casehub.worker.TaskRepository;
import io.casehub.worker.TaskStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Hibernate implementation of {@link TaskRepository} using the JPA EntityManager directly.
 * Uses PostgreSQL for production; tests override the datasource to H2.
 *
 * <p>Avoids implementing {@code PanacheRepositoryBase} to prevent method-signature conflicts
 * between Panache's {@code findById(Id) -> Entity} and the SPI's
 * {@code findById(Long) -> Optional<Task>}.
 */
@ApplicationScoped
public class HibernateTaskRepository implements TaskRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public Task create(String taskType, Map<String, Object> context,
                       Set<String> requiredCapabilities, PropagationContext propagationContext,
                       CaseFile owningCase) {
        HibernateCaseFile hcf = (HibernateCaseFile) owningCase;
        HibernateTask task = new HibernateTask(taskType, context, requiredCapabilities,
                propagationContext, hcf);
        task.setTaskOrigin(TaskOrigin.BROKER_ALLOCATED);
        em.persist(task);
        hcf.addTask(task);
        return task;
    }

    @Override
    @Transactional
    public Task createAutonomous(String taskType, Map<String, Object> context,
                                 String assignedWorkerId, CaseFile owningCase,
                                 PropagationContext propagationContext) {
        HibernateCaseFile hcf = (HibernateCaseFile) owningCase;
        HibernateTask task = new HibernateTask(taskType, context, Set.of(),
                propagationContext, hcf);
        task.setTaskOrigin(TaskOrigin.AUTONOMOUS);
        task.setAssignedWorkerId(assignedWorkerId);
        task.setStatus(TaskStatus.ASSIGNED);
        em.persist(task);
        hcf.addTask(task);
        return task;
    }

    @Override
    @Transactional
    public Optional<Task> findById(Long id) {
        return Optional.ofNullable(em.find(HibernateTask.class, id));
    }

    @Override
    @Transactional
    public List<Task> findByStatus(TaskStatus status) {
        return em.createQuery(
                        "SELECT t FROM HibernateTask t WHERE t.status = :status",
                        HibernateTask.class)
                .setParameter("status", status)
                .getResultList()
                .stream()
                .map(t -> (Task) t)
                .toList();
    }

    @Override
    @Transactional
    public List<Task> findByWorker(String workerId) {
        return em.createQuery(
                        "SELECT t FROM HibernateTask t WHERE t.assignedWorkerId = :workerId",
                        HibernateTask.class)
                .setParameter("workerId", workerId)
                .getResultList()
                .stream()
                .map(t -> (Task) t)
                .toList();
    }

    @Override
    @Transactional
    public void save(Task task) {
        if (task instanceof HibernateTask ht) {
            em.merge(ht);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        HibernateTask task = em.find(HibernateTask.class, id);
        if (task != null) {
            em.remove(task);
        }
    }
}
