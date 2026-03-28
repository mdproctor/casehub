package io.casehub.flow.examples;

import io.casehub.coordination.PropagationContext;
import io.casehub.flow.FlowExecutionContext;
import io.casehub.worker.Task;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Quarkus-based demo application showing QuarkusFlowDocumentWorkflow execution.
 *
 * <p>This version demonstrates:
 * <ul>
 *   <li>{@link QuarkusFlowDocumentWorkflow} - Full Quarkus Flow integration</li>
 *   <li>CDI dependency injection of workflow and functions</li>
 *   <li>Quarkus runtime execution via @QuarkusMain</li>
 *   <li>Programmatic API with FuncWorkflowBuilder</li>
 *   <li>Direct workflow execution using startInstance()</li>
 * </ul>
 *
 * <p>To run:
 * <pre>
 * mvn quarkus:dev
 * </pre>
 *
 * <p>Or for production build:
 * <pre>
 * mvn clean package
 * java -jar target/quarkus-app/quarkus-run.jar
 * </pre>
 */
@QuarkusMain
public class FlowWorkerQuarkusDemo implements QuarkusApplication {

    @Inject
    QuarkusFlowDocumentWorkflow workflow;

    @Inject
    DocumentFunctions documentFunctions;

    @Override
    public int run(String... args) throws Exception {
        System.out.println("""
                ╔════════════════════════════════════════════════════════════╗
                ║  CaseHub: Quarkus Flow Worker Demo (Quarkus Runtime)     ║
                ║  Full CDI Integration with @ApplicationScoped Beans       ║
                ╚════════════════════════════════════════════════════════════╝
                """);

        System.out.println("🔧 Quarkus runtime initialized");
        System.out.println("✓ Injected QuarkusFlowDocumentWorkflow (extends Flow)");
        System.out.println("✓ Injected DocumentFunctions (CDI bean)");
        System.out.println();

        System.out.println("📋 Workflow Details:");
        System.out.println("  ID: " + workflow.getWorkflowId());
        System.out.println("  Description: " + workflow.getDescription());
        System.out.println("  Type: Quarkus Flow (programmatic API)");
        System.out.println("  Functions: extractText, recognizeEntities, analyzeSentiment, generateSummary");
        System.out.println();

        // ========== Execute Workflow ==========

        System.out.println("📤 Executing workflow with Quarkus Flow engine...\n");

        // Create task with input
        PropagationContext propContext = PropagationContext.createRoot();

        Task task = new Task();
        task.setTaskId("quarkus-demo-001");
        task.setTaskType("document-processing-flow");
        task.setContext(Map.of(
                "document_url", "https://example.com/legal-contract.pdf",
                "language", "en"
        ));
        task.setPropagationContext(propContext);

        FlowExecutionContext context = new FlowExecutionContext(task, "quarkus-flow-worker");

        // Execute workflow using Quarkus Flow
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = workflow.execute(context);
        long duration = System.currentTimeMillis() - startTime;

        System.out.println();

        // ========== Display Results ==========

        System.out.println("""
                ╔════════════════════════════════════════════════════════════╗
                ║  WORKFLOW RESULTS                                          ║
                ╚════════════════════════════════════════════════════════════╝
                """);

        // Display extracted text
        if (result.containsKey("extractedText")) {
            String extractedText = (String) result.get("extractedText");
            System.out.println("📄 EXTRACTED TEXT:");
            System.out.println(extractedText.substring(0, Math.min(200, extractedText.length())) + "...");
            if (result.containsKey("wordCount")) {
                System.out.println("  Word count: " + result.get("wordCount"));
            }
            System.out.println();
        }

        // Display entities
        if (result.containsKey("entities")) {
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, String>> entities = (java.util.List<Map<String, String>>) result.get("entities");
            System.out.println("🏷️  ENTITIES (" + entities.size() + " found):");
            for (Map<String, String> entity : entities) {
                System.out.printf("  • %s (%s)%n", entity.get("name"), entity.get("type"));
            }
            System.out.println();
        }

        // Display sentiment
        if (result.containsKey("sentiment")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sentiment = (Map<String, Object>) result.get("sentiment");
            System.out.println("😊 SENTIMENT:");
            System.out.printf("  Overall: %s (score: %.2f)%n",
                    sentiment.get("overall"),
                    sentiment.get("score"));
            System.out.printf("  Confidence: %.2f%n", sentiment.get("confidence"));
            System.out.println();
        }

        // Display summary
        if (result.containsKey("summary")) {
            String summary = (String) result.get("summary");
            System.out.println("📋 SUMMARY:");
            System.out.println("  " + summary);
            System.out.println();
        }

        // Display metadata
        System.out.println("ℹ️  EXECUTION METADATA:");
        System.out.println("  Task ID: " + context.getTaskId());
        System.out.println("  Workflow ID: " + workflow.getWorkflowId());
        System.out.println("  Worker ID: quarkus-flow-worker");
        System.out.println("  Trace ID: " + propContext.getTraceId());
        System.out.println("  Span ID: " + propContext.getSpanId());
        System.out.println("  Processing time: " + duration + "ms");
        System.out.println();

        System.out.println("✓ Quarkus Flow demo complete!\n");

        // ========== Implementation Details ==========

        System.out.println("QUARKUS FLOW INTEGRATION:");
        System.out.println("  • QuarkusFlowDocumentWorkflow extends io.quarkiverse.flow.Flow");
        System.out.println("  • Uses @ApplicationScoped CDI beans");
        System.out.println("  • descriptor() returns FuncWorkflowBuilder.workflow(...)");
        System.out.println("  • execute() calls this.startInstance(input).await().indefinitely()");
        System.out.println("  • DocumentFunctions injected via @Inject");
        System.out.println();

        System.out.println("PROGRAMMATIC API FLOW:");
        System.out.println("  1. Workflow.descriptor() → defines workflow structure");
        System.out.println("  2. function(\"extractText\", lambda) → first step");
        System.out.println("  3. .exportAs(\"{ key: .value }\") → transform output");
        System.out.println("  4. Sequential execution through all functions");
        System.out.println("  5. this.startInstance(input) → execute workflow");
        System.out.println("  6. .await().indefinitely() → wait for completion");
        System.out.println();

        System.out.println("BENEFITS OF QUARKUS INTEGRATION:");
        System.out.println("  ✓ Full CDI dependency injection");
        System.out.println("  ✓ Quarkus dev mode hot reload");
        System.out.println("  ✓ Native executable compilation");
        System.out.println("  ✓ Metrics and health checks");
        System.out.println("  ✓ Integration with Quarkus ecosystem");
        System.out.println();

        System.out.println("FILES:");
        System.out.println("  • QuarkusFlowDocumentWorkflow.java - Workflow definition");
        System.out.println("  • DocumentFunctions.java - CDI bean with step implementations");
        System.out.println("  • FlowWorkerQuarkusDemo.java - This demo (requires Quarkus)");
        System.out.println("  • FlowWorkerDemo.java - Standalone demo (no Quarkus required)");
        System.out.println();

        return 0;
    }
}
