package io.casehub.flow.examples;

import io.casehub.coordination.PropagationContext;
import io.casehub.flow.FlowExecutionContext;
import io.casehub.flow.FlowWorkflowRegistry;
import io.casehub.worker.DefaultTask;

import java.util.Map;

/**
 * Demo application showing Quarkus Flow programmatic API workflow execution.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>{@link QuarkusFlowDocumentWorkflow} - Programmatic workflow using FuncWorkflowBuilder</li>
 *   <li>{@link DocumentFunctions} - CDI bean with workflow step implementations</li>
 *   <li>Quarkus Flow execution via descriptor() and startInstance()</li>
 *   <li>Sequential task execution with data transformation</li>
 * </ul>
 */
public class FlowWorkerDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("""
                ╔════════════════════════════════════════════════════════════╗
                ║  CaseHub: Quarkus Flow Worker Demo                        ║
                ║  Programmatic API Execution                               ║
                ╚════════════════════════════════════════════════════════════╝
                """);

        System.out.println("🔧 Setting up workflows...\n");

        // Create workflow registry
        FlowWorkflowRegistry registry = new FlowWorkflowRegistry();

        // Register simple workflow (works without Quarkus runtime)
        DocumentProcessingWorkflow workflow = new DocumentProcessingWorkflow();
        registry.register(workflow);

        System.out.println("✓ Registered workflow: " + workflow.getWorkflowId());
        System.out.println("  Description: " + workflow.getDescription());
        System.out.println("  Type: Simple FlowWorkflowDefinition");
        System.out.println();

        System.out.println("📋 Quarkus Flow Programmatic API:");
        System.out.println("   For production, use QuarkusFlowDocumentWorkflow.java");
        System.out.println("   - Extends io.quarkiverse.flow.Flow");
        System.out.println("   - Uses FuncWorkflowBuilder programmatic API");
        System.out.println("   - Requires Quarkus runtime context (@ApplicationScoped bean)");
        System.out.println("   - See: PROGRAMMATIC_API.md for details");
        System.out.println();

        // ========== Execute Workflow ==========

        System.out.println("📤 Executing workflow...\n");

        // Create task with input
        PropagationContext propContext = PropagationContext.createRoot();

        DefaultTask task = new DefaultTask();
        task.setTaskType("document-processing-flow");
        task.setContext(Map.of(
                "document_url", "https://example.com/contract.pdf",
                "language", "en"
        ));
        task.setPropagationContext(propContext);

        FlowExecutionContext context = new FlowExecutionContext(task, "flow-worker-demo");

        // Execute workflow
        Map<String, Object> result = workflow.execute(context);

        System.out.println();

        // ========== Display Results ==========

        System.out.println("""
                ╔════════════════════════════════════════════════════════════╗
                ║  WORKFLOW RESULTS                                          ║
                ╚════════════════════════════════════════════════════════════╝
                """);

        // Display extracted text
        if (result.containsKey("extracted_text") || result.containsKey("extractedText")) {
            String extractedText = (String) result.getOrDefault("extracted_text", result.get("extractedText"));
            System.out.println("📄 EXTRACTED TEXT:");
            System.out.println(extractedText.substring(0, Math.min(200, extractedText.length())) + "...");

            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
            if (metadata != null) {
                System.out.println("  Word count: " + metadata.get("word_count"));
            } else if (result.containsKey("wordCount")) {
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
                System.out.printf("  • %s (%s)\n", entity.get("name"), entity.get("type"));
            }
            System.out.println();
        }

        // Display sentiment
        if (result.containsKey("sentiment")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sentiment = (Map<String, Object>) result.get("sentiment");
            System.out.println("😊 SENTIMENT:");
            System.out.printf("  Overall: %s (score: %.2f)\n",
                    sentiment.get("overall"),
                    sentiment.get("score"));
            System.out.printf("  Confidence: %.2f\n", sentiment.get("confidence"));
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
        if (result.containsKey("metadata")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
            System.out.println("ℹ️  METADATA:");
            System.out.println("  Task ID: " + context.getTaskId());
            System.out.println("  Workflow ID: " + workflow.getWorkflowId());
            System.out.println("  Worker ID: " + metadata.get("worker_id"));
            System.out.println("  Trace ID: " + metadata.get("trace_id"));
            System.out.println("  Processing time: " + metadata.get("processing_time_ms") + "ms");
            System.out.println();
        }

        System.out.println("✓ Demo complete!\n");

        System.out.println("IMPLEMENTATION:");
        System.out.println("  Workflow: QuarkusFlowDocumentWorkflow.java");
        System.out.println("  Engine: Quarkus Flow (FuncWorkflowBuilder)");
        System.out.println("  Functions: DocumentFunctions.java");
        System.out.println("  API: Programmatic (extends Flow, implements FlowWorkflowDefinition)");
        System.out.println();

        System.out.println("PROGRAMMATIC API:");
        System.out.println("  • FuncWorkflowBuilder.workflow(\"document-processing\")");
        System.out.println("  • .tasks(function(), function(), ...)");
        System.out.println("  • function(\"name\", lambda).exportAs(\"{ key: .value }\")");
        System.out.println("  • this.startInstance(input).await().indefinitely()");
        System.out.println();

        System.out.println("NOTE:");
        System.out.println("  QuarkusFlowDocumentWorkflow requires Quarkus runtime (@ApplicationScoped)");
        System.out.println("  This demo uses DocumentProcessingWorkflow (works standalone)");
        System.out.println("  Both implement the same FlowWorkflowDefinition interface");
        System.out.println();

        System.out.println("See PROGRAMMATIC_API.md for QuarkusFlowDocumentWorkflow usage.");
        System.out.println();
    }
}
