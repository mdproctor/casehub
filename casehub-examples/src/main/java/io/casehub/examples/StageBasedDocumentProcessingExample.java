package io.casehub.examples;

import io.casehub.control.CasePlanModel;
import io.casehub.control.DefaultCasePlanModel;
import io.casehub.control.Milestone;
import io.casehub.control.Stage;
import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;
import io.casehub.core.ListenerEvaluator;
import io.casehub.persistence.memory.InMemoryCaseFileRepository;
import io.casehub.core.TaskDefinition;
import io.casehub.resilience.RetryPolicy;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Demonstrates CMMN Stages and Milestones in CaseHub.
 *
 * <p>This example shows a document processing workflow organized into three stages:
 * <ol>
 *   <li><b>Extraction Stage</b>: Extract raw content from documents</li>
 *   <li><b>Analysis Stage</b>: Analyze extracted content (NER, sentiment)</li>
 *   <li><b>Synthesis Stage</b>: Generate final outputs (summary, recommendations)</li>
 * </ol>
 *
 * <p>Milestones mark key achievements:
 * <ul>
 *   <li><b>Content Extracted</b>: All raw content has been extracted</li>
 *   <li><b>Analysis Complete</b>: All analysis tasks finished</li>
 *   <li><b>Document Processed</b>: Final outputs generated</li>
 * </ul>
 *
 * <p>The workflow demonstrates:
 * <ul>
 *   <li>Stage-based task organization</li>
 *   <li>Entry/exit criteria for stage lifecycle</li>
 *   <li>Milestone achievement tracking</li>
 *   <li>Autocomplete behavior when all required items finish</li>
 *   <li>Hierarchical stage containment</li>
 * </ul>
 */
public class StageBasedDocumentProcessingExample {

    public static void main(String[] args) {
        System.out.println("""
                ╔══════════════════════════════════════════════════════════════╗
                ║  CaseHub: CMMN Stages and Milestones Example               ║
                ║  Multi-Stage Document Processing Workflow                   ║
                ╚══════════════════════════════════════════════════════════════╝
                """);

        // Create CaseFile and CasePlanModel
        PropagationContext propagationContext = PropagationContext.createRoot();
        CaseFile caseFile = new InMemoryCaseFileRepository().create(
                "document-processing",
                new HashMap<>(),
                propagationContext
        );
        CasePlanModel casePlanModel = new DefaultCasePlanModel(caseFile);

        // Create three-stage workflow
        createWorkflowStages(caseFile, casePlanModel);

        // Create milestones
        createWorkflowMilestones(caseFile, casePlanModel);

        // Simulate workflow execution
        simulateWorkflow(caseFile, casePlanModel);

        System.out.println("\n✓ Stage-based workflow complete!\n");
        displayResults(caseFile, casePlanModel);
    }

    /**
     * Creates three stages: Extraction, Analysis, Synthesis
     */
    private static void createWorkflowStages(CaseFile caseFile, CasePlanModel casePlanModel) {
        System.out.println("\n📋 Creating Workflow Stages:");

        // Stage 1: Extraction
        Stage extractionStage = Stage.create("Extraction")
                .withEntryCriteria(Set.of("raw_documents"))
                .withAutocomplete(true);
        extractionStage.setCaseFileId(caseFile.getId().toString());
        casePlanModel.addStage(extractionStage);
        System.out.println("  ✓ Stage 1: Extraction (entry: raw_documents)");

        // Stage 2: Analysis
        Stage analysisStage = Stage.create("Analysis")
                .withEntryCriteria(Set.of("extracted_text"))
                .withAutocomplete(true);
        analysisStage.setCaseFileId(caseFile.getId().toString());
        casePlanModel.addStage(analysisStage);
        System.out.println("  ✓ Stage 2: Analysis (entry: extracted_text)");

        // Stage 3: Synthesis
        Stage synthesisStage = Stage.create("Synthesis")
                .withEntryCriteria(Set.of("entities", "sentiment"))
                .withExitCriteria(Set.of("summary", "recommendations"));
        synthesisStage.setCaseFileId(caseFile.getId().toString());
        casePlanModel.addStage(synthesisStage);
        System.out.println("  ✓ Stage 3: Synthesis (entry: entities+sentiment, exit: summary+recommendations)");
    }

    /**
     * Creates milestones marking key achievements
     */
    private static void createWorkflowMilestones(CaseFile caseFile, CasePlanModel casePlanModel) {
        System.out.println("\n🏁 Creating Milestones:");

        // Milestone 1: Content Extracted
        Milestone contentExtracted = Milestone.create("Content Extracted")
                .withAchievementCriteria(Set.of("extracted_text"));
        contentExtracted.setCaseFileId(caseFile.getId().toString());
        casePlanModel.addMilestone(contentExtracted);
        System.out.println("  ✓ Milestone: Content Extracted (criteria: extracted_text)");

        // Milestone 2: Analysis Complete
        Milestone analysisComplete = Milestone.create("Analysis Complete")
                .withAchievementCriteria(Set.of("entities", "sentiment"));
        analysisComplete.setCaseFileId(caseFile.getId().toString());
        casePlanModel.addMilestone(analysisComplete);
        System.out.println("  ✓ Milestone: Analysis Complete (criteria: entities+sentiment)");

        // Milestone 3: Document Processed
        Milestone documentProcessed = Milestone.create("Document Processed")
                .withAchievementCriteria(Set.of("summary", "recommendations"));
        documentProcessed.setCaseFileId(caseFile.getId().toString());
        casePlanModel.addMilestone(documentProcessed);
        System.out.println("  ✓ Milestone: Document Processed (criteria: summary+recommendations)");
    }

    /**
     * Simulates workflow execution with stages and milestones
     */
    private static void simulateWorkflow(CaseFile caseFile, CasePlanModel casePlanModel) {
        System.out.println("\n🔄 Executing Workflow:\n");

        ListenerEvaluator evaluator = new ListenerEvaluator();

        // Initial input
        System.out.println("Step 1: Provide raw documents");
        caseFile.put("raw_documents", List.of("doc1.pdf", "doc2.pdf"));
        evaluateStagesAndMilestones(caseFile, casePlanModel, evaluator);

        // Extraction completes
        System.out.println("\nStep 2: Extract text from documents");
        caseFile.put("extracted_text", "Sample document text content...");
        evaluateStagesAndMilestones(caseFile, casePlanModel, evaluator);

        // Analysis completes
        System.out.println("\nStep 3: Analyze content (NER + Sentiment)");
        caseFile.put("entities", List.of(
                Map.of("name", "Acme Corp", "type", "organization"),
                Map.of("name", "John Smith", "type", "person")
        ));
        caseFile.put("sentiment", Map.of("overall", "positive", "score", 0.75));
        evaluateStagesAndMilestones(caseFile, casePlanModel, evaluator);

        // Synthesis completes
        System.out.println("\nStep 4: Generate summary and recommendations");
        caseFile.put("summary", "Document discusses partnership between Acme Corp and John Smith...");
        caseFile.put("recommendations", List.of("Review contract terms", "Schedule follow-up"));
        evaluateStagesAndMilestones(caseFile, casePlanModel, evaluator);
    }

    /**
     * Evaluates stages and milestones after each step
     */
    private static void evaluateStagesAndMilestones(CaseFile caseFile,
                                                    CasePlanModel casePlanModel,
                                                    ListenerEvaluator evaluator) {
        // Evaluate and activate stages
        List<Stage> activated = evaluator.evaluateAndActivateStages(caseFile, casePlanModel);
        for (Stage stage : activated) {
            System.out.println("  ➤ Stage ACTIVATED: " + stage.getName());
        }

        // Evaluate and achieve milestones
        List<Milestone> achieved = evaluator.evaluateAndAchieveMilestones(caseFile, casePlanModel);
        for (Milestone milestone : achieved) {
            System.out.println("  🏁 Milestone ACHIEVED: " + milestone.getName());
        }

        // Evaluate and complete stages
        List<Stage> completed = evaluator.evaluateAndCompleteStages(caseFile, casePlanModel);
        for (Stage stage : completed) {
            String action = stage.getStatus() == Stage.StageStatus.COMPLETED ? "COMPLETED" : "TERMINATED";
            System.out.println("  ✓ Stage " + action + ": " + stage.getName());
        }
    }

    /**
     * Displays final results
     */
    private static void displayResults(CaseFile caseFile, CasePlanModel casePlanModel) {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("FINAL STATE:");
        System.out.println("═══════════════════════════════════════════════════════════");

        System.out.println("\nStages:");
        for (Stage stage : casePlanModel.getAllStages()) {
            System.out.printf("  • %s: %s%n", stage.getName(), stage.getStatus());
        }

        System.out.println("\nMilestones:");
        for (Milestone milestone : casePlanModel.getAllMilestones()) {
            System.out.printf("  • %s: %s%n", milestone.getName(), milestone.getStatus());
        }

        System.out.println("\nCaseFile Contents:");
        Map<String, Object> snapshot = caseFile.snapshot();
        snapshot.forEach((key, value) -> {
            String valueStr = value.toString();
            if (valueStr.length() > 50) {
                valueStr = valueStr.substring(0, 47) + "...";
            }
            System.out.printf("  • %s: %s%n", key, valueStr);
        });

        System.out.println("\n═══════════════════════════════════════════════════════════");
    }
}
