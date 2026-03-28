package io.casehub.examples.workers;

import io.casehub.coordination.PropagationContext;
import io.casehub.worker.*;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Example autonomous worker that monitors external events and self-initiates work
 * when it observes interesting conditions.
 *
 * <p>Unlike traditional broker-allocated workers that wait for TaskBroker to assign work,
 * autonomous workers operate on their own agency:
 * <ul>
 *   <li>Monitor external systems (API, message queue, filesystem, etc.)</li>
 *   <li>Decide autonomously when work is needed</li>
 *   <li>Notify the system via {@link WorkerRegistry#notifyAutonomousWork}</li>
 *   <li>Perform work and submit results</li>
 *   <li>Integrate with PropagationContext for lineage tracking</li>
 * </ul>
 *
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Event-driven workflows: Monitor message queues, trigger analysis</li>
 *   <li>Scheduled analysis: Periodic scans that spawn case-specific work</li>
 *   <li>Threshold monitoring: Watch metrics, trigger alerts/analysis</li>
 *   <li>Multi-agent collaboration: Agents observe shared workspace, contribute autonomously</li>
 * </ul>
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * // Start autonomous monitoring worker
 * AutonomousMonitoringWorker worker = new AutonomousMonitoringWorker(
 *     workerRegistry,
 *     "fraud-monitor-1",
 *     "http://api.example.com/transactions"
 * );
 * new Thread(worker).start();
 *
 * // Worker autonomously monitors API, triggers work when thresholds exceeded
 * // No TaskBroker involvement - fully decentralized
 * }</pre>
 */
public class AutonomousMonitoringWorker implements Runnable {

    private static final Logger log = Logger.getLogger(AutonomousMonitoringWorker.class);

    private final WorkerRegistry workerRegistry;
    private final String workerId;
    private final String workerName;
    private final String monitoringEndpoint;
    private final AtomicBoolean running = new AtomicBoolean(true);

    // Monitoring configuration
    private static final long POLL_INTERVAL_MS = 10_000;  // Check every 10 seconds
    private static final long HEARTBEAT_INTERVAL_MS = 30_000;  // Heartbeat every 30 seconds
    private static final double FRAUD_SCORE_THRESHOLD = 0.75;

    private Instant lastHeartbeat = Instant.now();

    public AutonomousMonitoringWorker(
            WorkerRegistry workerRegistry,
            String workerName,
            String monitoringEndpoint) throws Exception {
        this.workerRegistry = workerRegistry;
        this.workerName = workerName;
        this.monitoringEndpoint = monitoringEndpoint;

        // Register worker with capabilities
        Set<String> capabilities = Set.of("monitoring", "fraud-detection", "autonomous");
        this.workerId = workerRegistry.register(workerName, capabilities, "demo-api-key");

        log.infof("✓ Autonomous monitoring worker registered: %s (capabilities: %s)",
                workerId, capabilities);
    }

    @Override
    public void run() {
        log.infof("🔍 Starting autonomous monitoring loop: %s", workerName);

        while (running.get()) {
            try {
                // Send heartbeat periodically
                if (shouldSendHeartbeat()) {
                    workerRegistry.heartbeat(workerId);
                    lastHeartbeat = Instant.now();
                }

                // Monitor external system
                List<MonitoringEvent> events = pollForEvents();

                // Autonomously decide if work is needed
                for (MonitoringEvent event : events) {
                    if (shouldTriggerAnalysis(event)) {
                        // Self-initiate work - notify system
                        triggerAutonomousAnalysis(event);
                    }
                }

                Thread.sleep(POLL_INTERVAL_MS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running.set(false);
            } catch (Exception e) {
                log.errorf(e, "Error in autonomous monitoring loop");
            }
        }

        log.infof("🛑 Autonomous monitoring worker stopped: %s", workerName);
    }

    /**
     * Poll external system for events to monitor.
     * In a real implementation, this would call an API, read from a queue, etc.
     */
    private List<MonitoringEvent> pollForEvents() {
        // DEMO: Simulate polling an API for transactions
        // Real implementation would use HTTP client, message queue consumer, etc.

        List<MonitoringEvent> events = new ArrayList<>();

        // Simulate finding a suspicious transaction
        if (Math.random() < 0.1) {  // 10% chance per poll
            MonitoringEvent event = new MonitoringEvent(
                    "txn-" + UUID.randomUUID().toString(),
                    "transaction",
                    Map.of(
                            "amount", 15000.0,
                            "currency", "USD",
                            "country", "NG",
                            "fraud_score", 0.82,
                            "timestamp", Instant.now().toString()
                    )
            );
            events.add(event);
            log.infof("📊 Detected suspicious event: %s (fraud_score: %.2f)",
                    event.eventId, event.data.get("fraud_score"));
        }

        return events;
    }

    /**
     * Autonomous decision: Should this event trigger analysis work?
     */
    private boolean shouldTriggerAnalysis(MonitoringEvent event) {
        if (!"transaction".equals(event.eventType)) {
            return false;
        }

        // Check fraud score threshold
        Object scoreObj = event.data.get("fraud_score");
        if (scoreObj instanceof Number) {
            double fraudScore = ((Number) scoreObj).doubleValue();
            return fraudScore > FRAUD_SCORE_THRESHOLD;
        }

        return false;
    }

    /**
     * Trigger autonomous analysis work.
     * This demonstrates the autonomous worker pattern:
     * 1. Worker decides work is needed (not allocated by TaskBroker)
     * 2. Worker notifies system via notifyAutonomousWork()
     * 3. System tracks the work with full lineage
     * 4. Worker performs work and submits result
     */
    private void triggerAutonomousAnalysis(MonitoringEvent event) {
        try {
            log.infof("🚨 Triggering autonomous fraud analysis for event: %s", event.eventId);

            // Step 1: Notify system we're starting autonomous work
            // This creates a Task with AUTONOMOUS origin and links to PropagationContext
            Task task = workerRegistry.notifyAutonomousWork(
                    workerId,
                    "fraud-analysis",
                    event.data,
                    "fraud-case-" + event.eventId  // Associate with a case
            );

            log.infof("  → Task created: %s (origin: %s, caseFile: %s)",
                    task.getTaskId(),
                    task.getTaskOrigin(),
                    task.getCaseFileId().orElse("none"));

            // Step 2: Update task to RUNNING (TaskRegistry already set to ASSIGNED)
            // No need to manually transition to RUNNING - just start working

            // Step 3: Perform the actual analysis work
            Map<String, Object> analysisResult = performFraudAnalysis(event);

            // Step 4: Submit completion result
            TaskResult result = TaskResult.success(task.getTaskId(), analysisResult);
            workerRegistry.submitResult(workerId, task.getTaskId(), result);

            log.infof("  ✓ Fraud analysis complete: %s (verdict: %s)",
                    task.getTaskId(),
                    analysisResult.get("verdict"));

            // Step 5: Optionally spawn sub-workers for deeper analysis
            if ("HIGH_RISK".equals(analysisResult.get("verdict"))) {
                spawnDeepAnalysisSubWorker(task, event);
            }

        } catch (Exception e) {
            log.errorf(e, "Failed to trigger autonomous analysis for event: %s", event.eventId);
        }
    }

    /**
     * Perform fraud analysis.
     * In a real implementation, this might call ML models, rule engines, etc.
     */
    private Map<String, Object> performFraudAnalysis(MonitoringEvent event) {
        // DEMO: Simulate analysis
        double fraudScore = ((Number) event.data.get("fraud_score")).doubleValue();
        double amount = ((Number) event.data.get("amount")).doubleValue();

        String verdict;
        List<String> reasons = new ArrayList<>();

        if (fraudScore > 0.9) {
            verdict = "HIGH_RISK";
            reasons.add("Fraud score exceeds 0.9");
        } else if (fraudScore > 0.75) {
            verdict = "MEDIUM_RISK";
            reasons.add("Fraud score exceeds 0.75");
        } else {
            verdict = "LOW_RISK";
        }

        if (amount > 10000) {
            reasons.add("Large transaction amount");
        }

        return Map.of(
                "verdict", verdict,
                "fraud_score", fraudScore,
                "amount", amount,
                "reasons", reasons,
                "analyzed_at", Instant.now().toString(),
                "analyzed_by", workerId
        );
    }

    /**
     * Spawn a sub-worker for deeper analysis.
     * This demonstrates PropagationContext lineage: the sub-worker's task
     * becomes a child in the execution hierarchy.
     */
    private void spawnDeepAnalysisSubWorker(Task parentTask, MonitoringEvent event) {
        try {
            log.infof("  🔬 Spawning deep analysis sub-worker for high-risk case");

            // Create child PropagationContext from parent
            PropagationContext parentContext = parentTask.getPropagationContext();
            PropagationContext childContext = parentContext.createChild(Map.of(
                    "parent_task", parentTask.getTaskId(),
                    "analysis_type", "deep"
            ));

            // Notify autonomous work with parent context
            // This links the sub-worker's task into the lineage tree
            Task subTask = workerRegistry.notifyAutonomousWork(
                    workerId,
                    "deep-fraud-analysis",
                    Map.of(
                            "parent_event", event.eventId,
                            "parent_task", parentTask.getTaskId(),
                            "depth", "deep"
                    ),
                    parentTask.getCaseFileId().orElse(null),
                    childContext  // Parent context for lineage
            );

            log.infof("    → Sub-worker task created: %s (depth: %d, parent: %s)",
                    subTask.getTaskId(),
                    subTask.getPropagationContext().getDepth(),
                    subTask.getPropagationContext().getParentSpanId().orElse("none"));

            // Simulate deep analysis
            Map<String, Object> deepResult = Map.of(
                    "analysis_type", "deep",
                    "recommendation", "BLOCK_TRANSACTION",
                    "confidence", 0.95
            );

            TaskResult result = TaskResult.success(subTask.getTaskId(), deepResult);
            workerRegistry.submitResult(workerId, subTask.getTaskId(), result);

            log.infof("    ✓ Deep analysis complete: %s", subTask.getTaskId());

        } catch (Exception e) {
            log.errorf(e, "Failed to spawn sub-worker");
        }
    }

    private boolean shouldSendHeartbeat() {
        return Instant.now().isAfter(lastHeartbeat.plusMillis(HEARTBEAT_INTERVAL_MS));
    }

    public void shutdown() {
        running.set(false);
        workerRegistry.unregister(workerId);
        log.infof("Autonomous worker shutdown: %s", workerId);
    }

    /**
     * Represents an event detected by the monitoring worker.
     */
    static class MonitoringEvent {
        final String eventId;
        final String eventType;
        final Map<String, Object> data;

        MonitoringEvent(String eventId, String eventType, Map<String, Object> data) {
            this.eventId = eventId;
            this.eventType = eventType;
            this.data = data;
        }
    }

    /**
     * Demo application showing autonomous worker in action.
     *
     * NOTE: This is a conceptual example. For a working demo, run this within
     * a Quarkus application context where CDI dependencies are properly injected.
     * See casehub/src/main/java/io/casehub/examples/DocumentAnalysisWithLlmApp.java
     * for a complete working example with proper Quarkus setup.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("""
                ╔════════════════════════════════════════════════════════════╗
                ║  CaseHub: Autonomous Monitoring Worker - Conceptual Demo  ║
                ║  Decentralized Worker Pattern                             ║
                ╚════════════════════════════════════════════════════════════╝

                This is a conceptual example showing the autonomous worker pattern.
                For a working implementation, integrate this into a Quarkus application
                where WorkerRegistry, TaskRegistry, and NotificationService are properly
                injected via CDI.

                Key Concepts Demonstrated:
                ✓ Autonomous worker self-initiates work (not broker-allocated)
                ✓ Worker monitors external systems and decides when work is needed
                ✓ Worker notifies system via WorkerRegistry.notifyAutonomousWork()
                ✓ Tasks tracked with AUTONOMOUS origin
                ✓ Full PropagationContext lineage for parent-child relationships
                ✓ Sub-workers can be spawned with hierarchical tracking

                See: docs/DESIGN.md section 4.3 for full specification
                See: casehub/src/main/java/io/casehub/examples for working examples
                """);
    }
}
