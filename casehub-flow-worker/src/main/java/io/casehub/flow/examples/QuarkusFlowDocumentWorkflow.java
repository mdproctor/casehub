package io.casehub.flow.examples;

import io.casehub.flow.FlowExecutionContext;
import io.casehub.flow.FlowWorkflowDefinition;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.*;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

/**
 * Document processing workflow using Quarkus Flow programmatic API.
 *
 * <p>This demonstrates workflow integration with CaseHub using the programmatic DSL:
 * <ul>
 *   <li>Workflow defined programmatically using {@link FuncWorkflowBuilder}</li>
 *   <li>Workflow functions implemented in {@link DocumentFunctions}</li>
 *   <li>Sequential task execution with data passing between steps</li>
 *   <li>Results returned to CaseHub via FlowExecutionContext</li>
 * </ul>
 *
 * <p><b>Workflow Definition:</b> Programmatic using {@code descriptor()} method
 *
 * <p><b>Workflow Tasks:</b>
 * <ol>
 *   <li><b>extractText</b> - Extract text from document URL</li>
 *   <li><b>recognizeEntities</b> - Identify named entities</li>
 *   <li><b>analyzeSentiment</b> - Determine sentiment</li>
 *   <li><b>generateSummary</b> - Create executive summary</li>
 * </ol>
 *
 * <p><b>Functions:</b> {@link DocumentFunctions}
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * @Inject
 * FlowWorkflowRegistry registry;
 *
 * @Inject
 * QuarkusFlowDocumentWorkflow workflow;
 *
 * registry.register(workflow);
 *
 * // Submit task
 * TaskRequest request = TaskRequest.builder()
 *     .taskType("quarkus-flow-document-processing")
 *     .context(Map.of("documentUrl", "https://example.com/doc.pdf"))
 *     .requiredCapabilities(Set.of("flow"))
 *     .build();
 *
 * TaskResult result = taskBroker.submitTask(request).awaitResult();
 * }</pre>
 */
@ApplicationScoped
public class QuarkusFlowDocumentWorkflow extends Flow implements FlowWorkflowDefinition {

    private static final Logger log = Logger.getLogger(QuarkusFlowDocumentWorkflow.class);

    @Inject
    DocumentFunctions documentFunctions;

    @Override
    @SuppressWarnings("unchecked")
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("document-processing")
                .tasks(
                        // Task 1: Extract text from document
                        function("extractText",
                                documentFunctions::extractText,
                                Map.class),

                        // Task 2: Recognize named entities - pass whole context
                        function("recognizeEntities",
                                ctx -> documentFunctions.recognizeEntities((Map<String, Object>) ctx),
                                Map.class),

                        // Task 3: Analyze sentiment - pass whole context
                        function("analyzeSentiment",
                                ctx -> documentFunctions.analyzeSentiment((Map<String, Object>) ctx),
                                Map.class),

                        // Task 4: Generate summary - pass whole context
                        function("generateSummary",
                                ctx -> documentFunctions.generateSummary((Map<String, Object>) ctx),
                                Map.class)
                )
                .build();
    }

    @Override
    public String getWorkflowId() {
        return "quarkus-flow-document-processing";
    }

    @Override
    public String getDescription() {
        return "Document processing using Quarkus Flow workflow engine";
    }

    @Override
    public Set<String> getRequiredCapabilities() {
        return Set.of("flow", "quarkus-flow", "document-processing");
    }

    @Override
    public long getEstimatedDurationMs() {
        return 30_000;  // 30 seconds
    }

    @Override
    public Map<String, Object> execute(FlowExecutionContext context) throws Exception {
        log.infof("🔄 Executing Quarkus Flow workflow (task: %s, trace: %s)",
                 context.getTaskId(), context.getTraceId());

        // Extract input from CaseHub context (handle both documentUrl and document_url)
        String documentUrl = context.getInput("documentUrl", String.class)
                .or(() -> context.getInput("document_url", String.class))
                .orElseThrow(() -> new IllegalArgumentException("Missing required input: documentUrl or document_url"));

        String language = context.getInput("language", String.class).orElse("en");

        log.infof("  Input: documentUrl=%s, language=%s", documentUrl, language);

        // Create workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("documentUrl", documentUrl);
        workflowInput.put("language", language);
        workflowInput.put("traceId", context.getTraceId());
        workflowInput.put("taskId", context.getTaskId());

        log.info("  Starting Quarkus Flow workflow execution...");

        // Execute workflow using Quarkus Flow
        WorkflowInstance instance = this.instance(workflowInput);
        var result = this.startInstance(workflowInput).await().indefinitely();

        log.infof("✓ Quarkus Flow workflow completed (task: %s)", context.getTaskId());

        // Extract results from workflow model
        return extractWorkflowData(result);
    }

    /**
     * Extract workflow data from WorkflowModel.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractWorkflowData(io.serverlessworkflow.impl.WorkflowModel model) {
        return model.asMap().orElse(Map.of());
    }

}
