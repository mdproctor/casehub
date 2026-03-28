package io.casehub.flow;

import java.util.Map;
import java.util.Set;

/**
 * Defines a Quarkus Flow workflow that can be executed by {@link FlowWorker}.
 *
 * <p>Workflow definitions declare:
 * <ul>
 *   <li><b>Workflow ID</b> - Unique identifier matching task type</li>
 *   <li><b>Required Capabilities</b> - What capabilities this workflow needs</li>
 *   <li><b>Execution Logic</b> - The workflow implementation</li>
 * </ul>
 *
 * <p>Workflows receive {@link FlowExecutionContext} containing:
 * <ul>
 *   <li>Task context data (input parameters)</li>
 *   <li>PropagationContext for lineage tracking</li>
 *   <li>Task metadata (ID, type, etc.)</li>
 * </ul>
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * public class DocumentProcessingWorkflow implements FlowWorkflowDefinition {
 *     @Override
 *     public String getWorkflowId() {
 *         return "document-processing";
 *     }
 *
 *     @Override
 *     public Map<String, Object> execute(FlowExecutionContext context) {
 *         // Access input data
 *         String documentUrl = (String) context.getInput("document_url");
 *
 *         // Execute workflow steps
 *         String text = extractText(documentUrl);
 *         Map<String, Object> entities = recognizeEntities(text);
 *         Map<String, Object> summary = generateSummary(text, entities);
 *
 *         // Return results
 *         return Map.of(
 *             "text", text,
 *             "entities", entities,
 *             "summary", summary
 *         );
 *     }
 * }
 * }</pre>
 */
public interface FlowWorkflowDefinition {

    /**
     * Unique workflow identifier. Should match the task type that triggers this workflow.
     *
     * @return Workflow ID (e.g., "document-processing", "fraud-analysis")
     */
    String getWorkflowId();

    /**
     * Capabilities required to execute this workflow.
     * Used for worker selection and validation.
     *
     * @return Set of required capabilities (default: empty)
     */
    default Set<String> getRequiredCapabilities() {
        return Set.of();
    }

    /**
     * Execute the workflow with the given context.
     *
     * <p>The workflow should:
     * <ol>
     *   <li>Read input data from context.getInput()</li>
     *   <li>Execute workflow steps (may be synchronous or async)</li>
     *   <li>Return results as a Map</li>
     *   <li>Throw exceptions on failure (will be caught and reported)</li>
     * </ol>
     *
     * <p>The returned Map becomes the TaskResult.data that is sent back to
     * the requestor or contributed to the CaseFile.
     *
     * @param context Execution context with input data and metadata
     * @return Workflow results (will become TaskResult.data)
     * @throws Exception if workflow execution fails
     */
    Map<String, Object> execute(FlowExecutionContext context) throws Exception;

    /**
     * Optional: Workflow description for documentation/monitoring.
     *
     * @return Human-readable description
     */
    default String getDescription() {
        return "Flow workflow: " + getWorkflowId();
    }

    /**
     * Optional: Estimated execution time for monitoring/timeout configuration.
     *
     * @return Estimated duration in milliseconds (default: 60000 = 1 minute)
     */
    default long getEstimatedDurationMs() {
        return 60_000;  // 1 minute default
    }
}
