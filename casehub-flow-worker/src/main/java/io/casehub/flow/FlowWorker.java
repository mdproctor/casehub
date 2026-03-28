package io.casehub.flow;

import io.casehub.error.ErrorInfo;
import io.casehub.worker.*;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Worker that executes Quarkus Flow workflows from CaseHub tasks.
 *
 * <p>This worker bridges CaseHub's task model with Quarkus Flow workflows:
 * <ul>
 *   <li>Registers with WorkerRegistry with "flow" capability</li>
 *   <li>Claims tasks from TaskBroker (broker-allocated pattern)</li>
 *   <li>Can also run autonomously (autonomous pattern)</li>
 *   <li>Executes registered Flow workflows</li>
 *   <li>Returns workflow results as TaskResult</li>
 *   <li>Full PropagationContext support for lineage tracking</li>
 * </ul>
 *
 * <p><b>Architecture:</b>
 * <pre>
 * CaseHub Task → FlowWorker → FlowWorkflowDefinition → Quarkus Flow
 *     ↓              ↓               ↓                      ↓
 * Task context → FlowExecutionContext → Workflow steps → Results
 *     ↑                                                      ↓
 *     └──────────── TaskResult ← Result mapping ←──────────┘
 * </pre>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * @Inject
 * FlowWorker flowWorker;
 *
 * @Inject
 * FlowWorkflowRegistry workflowRegistry;
 *
 * // 1. Register workflows
 * workflowRegistry.register(new DocumentProcessingWorkflow());
 * workflowRegistry.register(new FraudAnalysisWorkflow());
 *
 * // 2. Start worker
 * new Thread(flowWorker).start();
 *
 * // 3. Submit tasks (via TaskBroker or autonomous)
 * TaskRequest request = TaskRequest.builder()
 *     .taskType("document-processing")  // Matches workflow ID
 *     .context(Map.of("document_url", url))
 *     .requiredCapabilities(Set.of("flow"))
 *     .build();
 * TaskHandle handle = taskBroker.submitTask(request);
 * }</pre>
 */
public class FlowWorker implements Runnable {

    private static final Logger log = Logger.getLogger(FlowWorker.class);

    private final WorkerRegistry workerRegistry;
    private final FlowWorkflowRegistry workflowRegistry;
    private final String workerId;
    private final String workerName;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Configuration
    private static final long POLL_INTERVAL_MS = 5_000;  // Poll every 5 seconds
    private static final long HEARTBEAT_INTERVAL_MS = 20_000;  // Heartbeat every 20 seconds
    private Instant lastHeartbeat = Instant.now();

    /**
     * Create FlowWorker with CDI injection.
     *
     * @param workerRegistry WorkerRegistry for registration
     * @param workflowRegistry FlowWorkflowRegistry for workflow lookup
     */
    @Inject
    public FlowWorker(WorkerRegistry workerRegistry, FlowWorkflowRegistry workflowRegistry) {
        this.workerRegistry = workerRegistry;
        this.workflowRegistry = workflowRegistry;
        this.workerName = "flow-worker-" + System.currentTimeMillis();

        // Register worker with "flow" capability
        try {
            this.workerId = workerRegistry.register(
                    workerName,
                    Set.of("flow", "workflow", "quarkus-flow"),
                    "flow-worker-api-key"  // TODO: Make configurable
            );
            log.infof("✓ FlowWorker registered: %s (capabilities: flow, workflow, quarkus-flow)", workerId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register FlowWorker", e);
        }
    }

    /**
     * Create FlowWorker with explicit worker name.
     *
     * @param workerRegistry WorkerRegistry for registration
     * @param workflowRegistry FlowWorkflowRegistry for workflow lookup
     * @param workerName Worker name
     */
    public FlowWorker(WorkerRegistry workerRegistry, FlowWorkflowRegistry workflowRegistry, String workerName) {
        this.workerRegistry = workerRegistry;
        this.workflowRegistry = workflowRegistry;
        this.workerName = workerName;

        try {
            this.workerId = workerRegistry.register(
                    workerName,
                    Set.of("flow", "workflow", "quarkus-flow"),
                    "flow-worker-api-key"
            );
            log.infof("✓ FlowWorker registered: %s (capabilities: flow, workflow, quarkus-flow)", workerId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register FlowWorker", e);
        }
    }

    @Override
    public void run() {
        running.set(true);
        log.infof("🚀 FlowWorker started: %s", workerName);

        while (running.get()) {
            try {
                // Send heartbeat periodically
                if (shouldSendHeartbeat()) {
                    workerRegistry.heartbeat(workerId);
                    lastHeartbeat = Instant.now();
                }

                // Claim next available task
                Optional<Task> taskOpt = workerRegistry.claimTask(workerId);

                if (taskOpt.isPresent()) {
                    Task task = taskOpt.get();
                    log.infof("📋 Claimed task: %s (type: %s)", task.getTaskId(), task.getTaskType());
                    processTask(task);
                } else {
                    // No tasks available - sleep before next poll
                    Thread.sleep(POLL_INTERVAL_MS);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running.set(false);
            } catch (Exception e) {
                log.errorf(e, "Error in FlowWorker main loop");
                // Continue running despite error
            }
        }

        log.infof("🛑 FlowWorker stopped: %s", workerName);
    }

    /**
     * Process a task by executing the corresponding workflow.
     *
     * @param task Task to process
     */
    private void processTask(Task task) {
        String taskId = task.getTaskId();
        String taskType = task.getTaskType();

        try {
            // Lookup workflow by task type
            Optional<FlowWorkflowDefinition> workflowOpt = workflowRegistry.get(taskType);

            if (workflowOpt.isEmpty()) {
                log.errorf("No workflow registered for task type: %s", taskType);
                reportError(taskId, "NO_WORKFLOW",
                           "No workflow registered for type: " + taskType, false);
                return;
            }

            FlowWorkflowDefinition workflow = workflowOpt.get();
            log.infof("  → Executing workflow: %s", workflow.getDescription());

            // Create execution context
            FlowExecutionContext context = new FlowExecutionContext(task, workerId);

            // Execute workflow
            Instant startTime = Instant.now();
            Map<String, Object> results = workflow.execute(context);
            Instant endTime = Instant.now();

            long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
            log.infof("  ✓ Workflow completed: %s (duration: %dms)", taskId, durationMs);

            // Submit success result
            TaskResult result = TaskResult.success(taskId, results);
            workerRegistry.submitResult(workerId, taskId, result);

        } catch (Exception e) {
            log.errorf(e, "Workflow execution failed: %s", taskId);
            reportError(taskId, "WORKFLOW_EXECUTION_FAILED",
                       "Workflow execution failed: " + e.getMessage(), true);
        }
    }

    /**
     * Report task execution error.
     *
     * @param taskId Task ID
     * @param errorCode Error code
     * @param message Error message
     * @param retryable Whether error is retryable
     */
    private void reportError(String taskId, String errorCode, String message, boolean retryable) {
        ErrorInfo error = new ErrorInfo();
        error.setErrorCode(errorCode);
        error.setMessage(message);
        error.setRetryable(retryable);
        error.setTimestamp(Instant.now());
        error.setWorkerId(Optional.of(workerId));

        try {
            workerRegistry.reportFailure(workerId, taskId, error);
        } catch (Exception e) {
            log.errorf(e, "Failed to report error for task: %s", taskId);
        }
    }

    /**
     * Check if heartbeat should be sent.
     *
     * @return True if heartbeat interval elapsed
     */
    private boolean shouldSendHeartbeat() {
        return Instant.now().isAfter(lastHeartbeat.plusMillis(HEARTBEAT_INTERVAL_MS));
    }

    /**
     * Gracefully shutdown the worker.
     */
    public void shutdown() {
        log.infof("Shutting down FlowWorker: %s", workerName);
        running.set(false);
        workerRegistry.unregister(workerId);
    }

    /**
     * Check if worker is running.
     *
     * @return True if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Get worker ID.
     *
     * @return Worker ID
     */
    public String getWorkerId() {
        return workerId;
    }

    /**
     * Get worker name.
     *
     * @return Worker name
     */
    public String getWorkerName() {
        return workerName;
    }
}
