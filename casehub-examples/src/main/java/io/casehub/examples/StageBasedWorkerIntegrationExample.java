package io.casehub.examples;

import io.casehub.control.CasePlanModel;
import io.casehub.control.DefaultCasePlanModel;
import io.casehub.control.Milestone;
import io.casehub.control.PlanItem;
import io.casehub.control.Stage;
import io.casehub.coordination.PropagationContext;
import io.casehub.core.*;

import java.util.*;

/**
 * Demonstrates CMMN Stages and Milestones integrated with real TaskDefinitions.
 *
 * <p>This example shows how Stages can contain TaskDefinitions that execute,
 * combining CMMN case planning with the CaseHub execution model.
 *
 * <p>Workflow stages:
 * <ol>
 *   <li><b>Data Preparation Stage</b>: Contains text extraction task</li>
 *   <li><b>Analysis Stage</b>: Contains NER and sentiment analysis tasks</li>
 *   <li><b>Output Stage</b>: Contains summary generation task</li>
 * </ol>
 */
public class StageBasedWorkerIntegrationExample {

    public static void main(String[] args) {
        System.out.println("""
                ╔══════════════════════════════════════════════════════════════╗
                ║  CaseHub: Stages + Workers Integration Example             ║
                ║  CMMN Stages with Real TaskDefinition Execution            ║
                ╚══════════════════════════════════════════════════════════════╝
                """);

        // Create registry and register task definitions
        TaskDefinitionRegistry registry = new TaskDefinitionRegistry();
        String caseType = "staged-analysis";
        Set<String> caseTypes = Set.of(caseType);

        // Stage 1: Data Preparation - Text Extraction
        TaskDefinition extractionTask = new TextExtractionTask();
        registry.register(extractionTask, caseTypes);
        System.out.println("  ✓ Registered: " + extractionTask.getName());

        // Stage 2: Analysis - Named Entity Recognition
        TaskDefinition nerTask = new NerTask();
        registry.register(nerTask, caseTypes);
        System.out.println("  ✓ Registered: " + nerTask.getName());

        // Stage 2: Analysis - Sentiment Analysis
        TaskDefinition sentimentTask = new SentimentTask();
        registry.register(sentimentTask, caseTypes);
        System.out.println("  ✓ Registered: " + sentimentTask.getName());

        // Stage 3: Output - Summary Generation
        TaskDefinition summaryTask = new SummaryTask();
        registry.register(summaryTask, caseTypes);
        System.out.println("  ✓ Registered: " + summaryTask.getName());

        // Create CaseFile and CasePlanModel
        PropagationContext propagationContext = PropagationContext.createRoot();
        Map<String, Object> initialState = Map.of("raw_document", "sample-document.pdf");
        CaseFile caseFile = new DefaultCaseFile(
                "stage-worker-001",
                "staged-analysis",
                new HashMap<>(initialState),
                propagationContext
        );
        CasePlanModel casePlanModel = new DefaultCasePlanModel(caseFile);

        // Create stages
        System.out.println("\n📋 Creating Workflow Stages:");

        Stage preparationStage = Stage.create("Data Preparation")
                .withEntryCriteria(Set.of("raw_document"))
                .withAutocomplete(true);
        preparationStage.setCaseFileId(caseFile.getCaseFileId());
        casePlanModel.addStage(preparationStage);
        System.out.println("  ✓ Stage: Data Preparation");

        Stage analysisStage = Stage.create("Analysis")
                .withEntryCriteria(Set.of("extracted_text"))
                .withAutocomplete(true);
        analysisStage.setCaseFileId(caseFile.getCaseFileId());
        casePlanModel.addStage(analysisStage);
        System.out.println("  ✓ Stage: Analysis");

        Stage outputStage = Stage.create("Output")
                .withEntryCriteria(Set.of("entities", "sentiment"))
                .withAutocomplete(true);
        outputStage.setCaseFileId(caseFile.getCaseFileId());
        casePlanModel.addStage(outputStage);
        System.out.println("  ✓ Stage: Output");

        // Create milestones
        System.out.println("\n🏁 Creating Milestones:");

        Milestone dataReady = Milestone.create("Data Ready")
                .withAchievementCriteria(Set.of("extracted_text"));
        dataReady.setCaseFileId(caseFile.getCaseFileId());
        casePlanModel.addMilestone(dataReady);
        System.out.println("  ✓ Milestone: Data Ready");

        Milestone analysisComplete = Milestone.create("Analysis Complete")
                .withAchievementCriteria(Set.of("entities", "sentiment"));
        analysisComplete.setCaseFileId(caseFile.getCaseFileId());
        casePlanModel.addMilestone(analysisComplete);
        System.out.println("  ✓ Milestone: Analysis Complete");

        Milestone reportReady = Milestone.create("Report Ready")
                .withAchievementCriteria(Set.of("summary"));
        reportReady.setCaseFileId(caseFile.getCaseFileId());
        casePlanModel.addMilestone(reportReady);
        System.out.println("  ✓ Milestone: Report Ready");

        // Execute workflow with manual control loop
        System.out.println("\n🔄 Executing Staged Workflow:\n");

        // Create ListenerEvaluator and inject dependencies manually
        ListenerEvaluator evaluator = new ListenerEvaluator();
        try {
            java.lang.reflect.Field field = ListenerEvaluator.class.getDeclaredField("poisonPillDetector");
            field.setAccessible(true);
            field.set(evaluator, new io.casehub.resilience.PoisonPillDetector());
        } catch (Exception e) {
            System.err.println("Failed to initialize evaluator: " + e.getMessage());
            return;
        }

        int maxIterations = 20;
        int iteration = 0;

        while (iteration < maxIterations) {
            iteration++;

            boolean workDone = false;

            // Evaluate and activate stages
            List<Stage> activatedStages = evaluator.evaluateAndActivateStages(caseFile, casePlanModel);
            for (Stage stage : activatedStages) {
                System.out.println("  ➤ Stage ACTIVATED: " + stage.getName());
                workDone = true;
            }

            // Evaluate and create plan items (only for tasks not already completed)
            List<TaskDefinition> registered = registry.getForCaseType(caseType);

            // Filter out tasks that have already completed OR have their produced keys in CaseFile
            List<TaskDefinition> eligibleTasks = registered.stream()
                    .filter(td -> {
                        // Check if task already completed
                        boolean hasCompletedPlanItem = casePlanModel.getAgenda().stream()
                                .anyMatch(pi -> pi.getTaskDefinitionId().equals(td.getId())
                                        && pi.getStatus() == PlanItem.PlanItemStatus.COMPLETED);
                        if (hasCompletedPlanItem) {
                            return false;
                        }

                        // Check if produced keys already exist (idempotency)
                        boolean producedKeysExist = td.producedKeys().stream()
                                .allMatch(caseFile::contains);
                        return !producedKeysExist;
                    })
                    .toList();

            List<PlanItem> createdItems = evaluator.evaluateAndCreatePlanItems(
                    caseFile, casePlanModel, eligibleTasks, null);

            if (!createdItems.isEmpty()) {
                System.out.println("  📌 Created " + createdItems.size() + " plan item(s)");
                workDone = true;
            }

            // Execute top-priority plan item
            List<PlanItem> agenda = casePlanModel.getAgenda();
            Optional<PlanItem> nextItem = agenda.stream()
                    .filter(pi -> pi.getStatus() == PlanItem.PlanItemStatus.PENDING)
                    .sorted()
                    .findFirst();

            if (nextItem.isPresent()) {
                PlanItem planItem = nextItem.get();
                planItem.setStatus(PlanItem.PlanItemStatus.RUNNING);

                Optional<TaskDefinition> taskDefOpt = registry.getById(planItem.getTaskDefinitionId());
                if (taskDefOpt.isPresent()) {
                    TaskDefinition taskDef = taskDefOpt.get();
                    System.out.println("  ⚙️  Executing: " + taskDef.getName());

                    try {
                        taskDef.execute(caseFile);
                        planItem.setStatus(PlanItem.PlanItemStatus.COMPLETED);
                        workDone = true;
                    } catch (Exception e) {
                        System.err.println("  ❌ Task failed: " + e.getMessage());
                        planItem.setStatus(PlanItem.PlanItemStatus.FAULTED);
                    }
                }
            }

            // Evaluate and achieve milestones
            List<Milestone> achievedMilestones = evaluator.evaluateAndAchieveMilestones(caseFile, casePlanModel);
            for (Milestone milestone : achievedMilestones) {
                System.out.println("  🏁 Milestone ACHIEVED: " + milestone.getName());
                workDone = true;
            }

            // Evaluate and complete stages
            List<Stage> completedStages = evaluator.evaluateAndCompleteStages(caseFile, casePlanModel);
            for (Stage stage : completedStages) {
                String action = stage.getStatus() == Stage.StageStatus.COMPLETED ? "COMPLETED" : "TERMINATED";
                System.out.println("  ✓ Stage " + action + ": " + stage.getName());
                workDone = true;
            }

            // Check if quiescent - no more work can be done
            if (!workDone && evaluator.isQuiescent(casePlanModel)) {
                System.out.println("\n✓ Workflow quiescent - all stages complete!\n");
                break;
            }
        }

        // Display results
        displayResults(caseFile, casePlanModel);
    }

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

        System.out.println("\nPlan Items:");
        for (PlanItem planItem : casePlanModel.getAgenda()) {
            System.out.printf("  • %s: %s%n", planItem.getTaskDefinitionId(), planItem.getStatus());
        }

        System.out.println("\nCaseFile Contents:");
        Map<String, Object> snapshot = caseFile.snapshot();
        snapshot.forEach((key, value) -> {
            String valueStr = value.toString();
            if (valueStr.length() > 60) {
                valueStr = valueStr.substring(0, 57) + "...";
            }
            System.out.printf("  • %s: %s%n", key, valueStr);
        });

        System.out.println("\n═══════════════════════════════════════════════════════════");
    }

    //
    // TaskDefinition Implementations
    //

    static class TextExtractionTask implements TaskDefinition {
        @Override
        public String getId() {
            return "text-extraction";
        }

        @Override
        public String getName() {
            return "Text Extraction";
        }

        @Override
        public Set<String> entryCriteria() {
            return Set.of("raw_document");
        }

        @Override
        public Set<String> producedKeys() {
            return Set.of("extracted_text");
        }

        @Override
        public void execute(CaseFile caseFile) {
            String doc = caseFile.get("raw_document", String.class).orElse("");
            System.out.println("  [Worker] Extracting text from document...");
            caseFile.put("extracted_text", "Extracted content from: " + doc);
        }
    }

    static class NerTask implements TaskDefinition {
        @Override
        public String getId() {
            return "ner-analysis";
        }

        @Override
        public String getName() {
            return "Named Entity Recognition";
        }

        @Override
        public Set<String> entryCriteria() {
            return Set.of("extracted_text");
        }

        @Override
        public Set<String> producedKeys() {
            return Set.of("entities");
        }

        @Override
        public void execute(CaseFile caseFile) {
            System.out.println("  [Worker] Performing Named Entity Recognition...");
            List<Map<String, String>> entities = List.of(
                    Map.of("name", "CaseHub", "type", "PRODUCT"),
                    Map.of("name", "CMMN", "type", "STANDARD")
            );
            caseFile.put("entities", entities);
        }
    }

    static class SentimentTask implements TaskDefinition {
        @Override
        public String getId() {
            return "sentiment-analysis";
        }

        @Override
        public String getName() {
            return "Sentiment Analysis";
        }

        @Override
        public Set<String> entryCriteria() {
            return Set.of("extracted_text");
        }

        @Override
        public Set<String> producedKeys() {
            return Set.of("sentiment");
        }

        @Override
        public void execute(CaseFile caseFile) {
            System.out.println("  [Worker] Analyzing sentiment...");
            caseFile.put("sentiment", Map.of("score", 0.85, "label", "positive"));
        }
    }

    static class SummaryTask implements TaskDefinition {
        @Override
        public String getId() {
            return "summary-generation";
        }

        @Override
        public String getName() {
            return "Summary Generation";
        }

        @Override
        public Set<String> entryCriteria() {
            return Set.of("entities", "sentiment");
        }

        @Override
        public Set<String> producedKeys() {
            return Set.of("summary");
        }

        @Override
        public void execute(CaseFile caseFile) {
            System.out.println("  [Worker] Generating summary...");
            caseFile.put("summary", "Document analysis complete. Positive sentiment detected with key entities.");
        }
    }
}
