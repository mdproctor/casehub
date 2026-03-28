package io.casehub.flow;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for {@link FlowWorkflowDefinition} instances.
 *
 * <p>Manages workflow lifecycle:
 * <ul>
 *   <li>Register workflows by ID</li>
 *   <li>Lookup workflows for execution</li>
 *   <li>List all registered workflows</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * @Inject
 * FlowWorkflowRegistry registry;
 *
 * // Register workflow
 * registry.register(new DocumentProcessingWorkflow());
 * registry.register(new FraudAnalysisWorkflow());
 *
 * // Lookup for execution
 * Optional<FlowWorkflowDefinition> workflow = registry.get("document-processing");
 * }</pre>
 */
@ApplicationScoped
public class FlowWorkflowRegistry {

    private static final Logger log = Logger.getLogger(FlowWorkflowRegistry.class);

    private final Map<String, FlowWorkflowDefinition> workflows = new ConcurrentHashMap<>();

    /**
     * Register a workflow definition.
     *
     * @param workflow Workflow to register
     * @throws IllegalArgumentException if workflow ID is null or already registered
     */
    public void register(FlowWorkflowDefinition workflow) {
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow cannot be null");
        }

        String workflowId = workflow.getWorkflowId();
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("Workflow ID cannot be null or blank");
        }

        if (workflows.containsKey(workflowId)) {
            log.warnf("Workflow already registered, replacing: %s", workflowId);
        }

        workflows.put(workflowId, workflow);
        log.infof("✓ Registered Flow workflow: %s (%s)", workflowId, workflow.getDescription());
    }

    /**
     * Unregister a workflow.
     *
     * @param workflowId Workflow ID to remove
     * @return True if workflow was removed
     */
    public boolean unregister(String workflowId) {
        boolean removed = workflows.remove(workflowId) != null;
        if (removed) {
            log.infof("Unregistered Flow workflow: %s", workflowId);
        }
        return removed;
    }

    /**
     * Get workflow by ID.
     *
     * @param workflowId Workflow ID
     * @return Workflow if registered
     */
    public Optional<FlowWorkflowDefinition> get(String workflowId) {
        return Optional.ofNullable(workflows.get(workflowId));
    }

    /**
     * Check if workflow is registered.
     *
     * @param workflowId Workflow ID
     * @return True if registered
     */
    public boolean contains(String workflowId) {
        return workflows.containsKey(workflowId);
    }

    /**
     * Get all registered workflows.
     *
     * @return Immutable map of workflows
     */
    public Map<String, FlowWorkflowDefinition> getAll() {
        return Map.copyOf(workflows);
    }

    /**
     * Get count of registered workflows.
     *
     * @return Number of registered workflows
     */
    public int size() {
        return workflows.size();
    }

    /**
     * Clear all workflows.
     */
    public void clear() {
        int count = workflows.size();
        workflows.clear();
        log.infof("Cleared %d Flow workflows", count);
    }
}
