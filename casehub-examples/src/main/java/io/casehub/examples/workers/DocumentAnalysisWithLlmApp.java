package io.casehub.examples.workers;

import io.casehub.coordination.CaseEngine;
import io.casehub.core.CaseFile;
import io.casehub.core.CaseStatus;
import io.casehub.core.TaskDefinition;
import io.casehub.core.TaskDefinitionRegistry;
// Note: Using local copies of TaskDefinitions for self-contained example
import io.casehub.worker.TaskBroker;
import io.casehub.worker.WorkerRegistry;
import io.quarkus.runtime.QuarkusApplication;
// import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * Complete example demonstrating the dual execution model with LlmReasoningWorker.
 *
 * This example shows:
 * 1. CaseFile Model: Multiple TaskDefinitions collaboratively solving a problem
 * 2. Task Model: LlmReasoningWorker processing heavy reasoning tasks
 * 3. Integration: TaskDefinition delegating to Worker and contributing results back
 *
 * Workflow:
 * ────────
 * 1. Text Extraction → extracted_text
 * 2. Entity Recognition → entities
 * 3. Risk Assessment → risk_assessment
 * 4. LLM Analysis (via Worker) → strategic_recommendations  ← NEW!
 * 5. Executive Summary → executive_summary
 *
 * The LLM Analysis step demonstrates:
 * - TaskDefinition reads from CaseFile
 * - TaskDefinition submits task to Worker
 * - Worker (LlmReasoningWorker) processes using Claude API
 * - Worker returns results
 * - TaskDefinition contributes back to CaseFile
 * - Downstream TaskDefinitions can use LLM insights
 *
 * Prerequisites:
 * ─────────────
 * - Set ANTHROPIC_API_KEY environment variable
 * - Run: export ANTHROPIC_API_KEY=sk-ant-...
 *
 * Run with:
 * ─────────
 * mvn quarkus:dev -Dquarkus.args="--llm-enabled"
 */
// @QuarkusMain
public class DocumentAnalysisWithLlmApp implements QuarkusApplication {

    @Inject
    CaseEngine caseEngine;

    @Inject
    TaskDefinitionRegistry registry;

    @Inject
    TaskBroker taskBroker;

    @Inject
    WorkerRegistry workerRegistry;

    private ExecutorService workerExecutor;
    private LlmReasoningWorker llmWorker;

    @Override
    public int run(String... args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  CaseHub: Document Analysis with LLM Worker               ║");
        System.out.println("║  Demonstrating Dual Execution Model                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        // Check for API key
        if (System.getenv("ANTHROPIC_API_KEY") == null) {
            System.err.println("❌ ERROR: ANTHROPIC_API_KEY environment variable not set\n");
            System.err.println("Please set your Claude API key:");
            System.err.println("  export ANTHROPIC_API_KEY=sk-ant-your-key-here\n");
            System.err.println("Get your API key from: https://console.anthropic.com/\n");
            return 1;
        }

        try {
            // Step 1: Start LlmReasoningWorker
            startWorker();

            // Step 2: Register all TaskDefinitions (including LLM-based one)
            registerTaskDefinitions();

            // Step 3: Create and solve case
            Map<String, Object> initialState = createInitialState();
            CaseFile caseFile = caseEngine.createAndSolve("legal-document-analysis", initialState);

            // Step 4: Wait for completion
            System.out.println("⏳ Waiting for case to complete (includes LLM analysis)...\n");
            caseEngine.awaitCompletion(caseFile, Duration.ofMinutes(5));

            // Step 5: Display results
            displayResults(caseFile);

            // Step 6: Cleanup
            stopWorker();

            return 0;

        } catch (InterruptedException | TimeoutException e) {
            System.err.println("❌ Case execution timed out or was interrupted: " + e.getMessage());
            stopWorker();
            return 1;
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            stopWorker();
            return 1;
        }
    }

    /**
     * Start the LlmReasoningWorker in a background thread.
     */
    private void startWorker() throws Exception {
        System.out.println("🤖 Starting LlmReasoningWorker...\n");

        llmWorker = new LlmReasoningWorker(
            workerRegistry,
            "llm-worker-1",
            "worker-api-key-12345"
        );

        workerExecutor = Executors.newSingleThreadExecutor();
        workerExecutor.submit(llmWorker);

        System.out.println("  ✓ LlmReasoningWorker registered and running");
        System.out.println("    Worker ID: " + llmWorker.getWorkerId());
        System.out.println("    Capabilities: [llm, reasoning, text-generation, analysis]");
        System.out.println();

        // Give worker time to start
        Thread.sleep(500);
    }

    /**
     * Stop the LlmReasoningWorker.
     */
    private void stopWorker() {
        if (llmWorker != null) {
            System.out.println("\n🛑 Stopping LlmReasoningWorker...");
            llmWorker.shutdown();
        }
        if (workerExecutor != null) {
            workerExecutor.shutdown();
        }
    }

    /**
     * Register TaskDefinitions including the new LLM-based one.
     */
    private void registerTaskDefinitions() {
        System.out.println("📋 Registering TaskDefinitions...\n");

        String caseType = "legal-document-analysis";
        Set<String> caseTypes = Set.of(caseType);

        // Original TaskDefinitions (from DocumentAnalysisApp)
        registry.register(new TextExtractionTaskDefinition(), caseTypes);
        System.out.println("  ✓ Text Extraction (raw_documents → extracted_text)");

        registry.register(new EntityRecognitionTaskDefinition(), caseTypes);
        System.out.println("  ✓ Entity Recognition (extracted_text → entities)");

        registry.register(new RiskAnalysisTaskDefinition(), caseTypes);
        System.out.println("  ✓ Risk Assessment (text + entities → risk_assessment)");

        // NEW: LLM-based strategic analysis (delegates to Worker)
        registry.register(new LlmAnalysisTaskDefinition(taskBroker), caseTypes);
        System.out.println("  ✓ LLM Strategic Analysis (delegates to Worker) ⭐");
        System.out.println("    Entry: [extracted_text, entities, risk_assessment]");
        System.out.println("    Produces: [strategic_recommendations, llm_insights]");

        registry.register(new EnhancedSummaryTaskDefinition(), caseTypes);
        System.out.println("  ✓ Enhanced Summary (includes LLM insights)");

        System.out.println();
    }

    /**
     * Create initial state with contract documents.
     */
    private Map<String, Object> createInitialState() {
        System.out.println("📄 Creating Initial State...\n");

        List<String> documents = List.of(
            "SUPPLY AGREEMENT between Acme Corp (Supplier) and Beta Inc (Customer). " +
            "Effective Date: 2026-01-01. Term: 24 months with automatic renewal. " +
            "Payment Terms: Net 30 days. Penalty for late payment: $50,000 per month. " +
            "Minimum Order Quantity: 10,000 units/month. " +
            "Breach clause: Material breach allows immediate termination with 90-day notice. " +
            "Governing Law: Delaware. " +
            "Price protection: No price increases for first 12 months.",

            "ADDENDUM dated 2026-02-15 to Supply Agreement. " +
            "NEW REQUIREMENTS: Regulatory compliance with FDA standards required by 2026-06-01. " +
            "Failure to achieve compliance will result in: " +
            "(a) contract termination, " +
            "(b) financial penalties of $100,000, " +
            "(c) indemnification for all customer losses. " +
            "Quality standards: 99.5% defect-free rate required. " +
            "Penalties for quality failures: $25,000 per percentage point below standard. " +
            "Insurance requirements: $5M product liability coverage. " +
            "This addendum supersedes conflicting terms in original agreement."
        );

        Map<String, Object> initialState = new HashMap<>();
        initialState.put("raw_documents", documents);

        System.out.println("  Contract Type: Multi-year supply agreement with amendments");
        System.out.println("  Documents: " + documents.size() + " loaded");
        System.out.println("  Complexity: HIGH (regulatory compliance, penalties, quality standards)");
        System.out.println();

        return initialState;
    }

    /**
     * Display comprehensive results including LLM insights.
     */
    private void displayResults(CaseFile caseFile) {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("📊 FINAL RESULTS\n");

        System.out.println("  Case ID: " + caseFile.getCaseFileId());
        System.out.println("  Status: " + caseFile.getStatus());
        System.out.println();

        if (caseFile.getStatus() != CaseStatus.WAITING && caseFile.getStatus() != CaseStatus.COMPLETED) {
            System.out.println("  ⚠️  Case did not complete successfully");
            return;
        }

        // Display risk assessment
        caseFile.get("risk_assessment", Map.class).ifPresent(risk -> {
            Map<String, Object> riskMap = (Map<String, Object>) risk;
            System.out.println("  📌 RISK ASSESSMENT:");
            System.out.println("     Risk Level: " + riskMap.get("risk_level") +
                             " (" + riskMap.get("risk_score") + "/100)");
            System.out.println();
        });

        // Display LLM insights (the key addition!)
        caseFile.get("llm_insights", String.class).ifPresent(insights -> {
            System.out.println("  📌 LLM STRATEGIC ANALYSIS:");
            System.out.println("     ─────────────────────────");
            for (String line : insights.split("\n")) {
                System.out.println("     " + line);
            }
            System.out.println();
        });

        // Display structured recommendations
        caseFile.get("strategic_recommendations", Map.class).ifPresent(recs -> {
            Map<String, Object> recsMap = (Map<String, Object>) recs;
            System.out.println("  📌 STRUCTURED RECOMMENDATIONS:");
            System.out.println("     ───────────────────────────");

            displayRecommendationSection("Immediate Actions", recs.get("immediate_actions"));
            displayRecommendationSection("Negotiation Points", recsMap.get("negotiation_points"));
            displayRecommendationSection("Key Deadlines", recsMap.get("key_deadlines"));
            displayRecommendationSection("Risk Mitigation", recsMap.get("risk_mitigation"));

            System.out.println();
        });

        // Display executive summary
        caseFile.get("executive_summary", String.class).ifPresent(summary -> {
            System.out.println("  📌 EXECUTIVE SUMMARY:");
            System.out.println("     ─────────────────");
            for (String line : summary.split("\n")) {
                System.out.println("     " + line);
            }
            System.out.println();
        });

        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("🎯 ARCHITECTURAL FEATURES DEMONSTRATED:\n");
        System.out.println("   ✓ CaseFile Model (Collaborative Problem-Solving)");
        System.out.println("     5 TaskDefinitions working on shared CaseFile\n");
        System.out.println("   ✓ Task Model (Request-Response with Workers)");
        System.out.println("     LlmReasoningWorker processing complex reasoning\n");
        System.out.println("   ✓ Dual Execution Model Integration");
        System.out.println("     TaskDefinition → Worker → TaskDefinition flow\n");
        System.out.println("   ✓ External API Integration");
        System.out.println("     Claude API called by Worker for LLM reasoning\n");
        System.out.println("   ✓ Worker Lifecycle Management");
        System.out.println("     Registration, heartbeat, task claiming, result submission\n");
        System.out.println("   ✓ Data Flow Across Models");
        System.out.println("     CaseFile → Task → Worker → Task → CaseFile\n");
        System.out.println("═══════════════════════════════════════════════════════════\n");
    }

    private void displayRecommendationSection(String title, Object items) {
        if (items instanceof List) {
            List<?> list = (List<?>) items;
            if (!list.isEmpty()) {
                System.out.println("\n     " + title + ":");
                for (Object item : list) {
                    System.out.println("       • " + item);
                }
            }
        }
    }

    //
    // TaskDefinition Implementations (copied from DocumentAnalysisApp for self-contained example)
    //

    static class TextExtractionTaskDefinition implements TaskDefinition {
        @Override
        public String getId() { return "text-extractor"; }
        @Override
        public String getName() { return "Text Extraction"; }
        @Override
        public Set<String> entryCriteria() { return Set.of("raw_documents"); }
        @Override
        public Set<String> producedKeys() { return Set.of("extracted_text"); }
        @Override
        public void execute(CaseFile caseFile) {
            System.out.println("  [EXECUTING] Text Extraction...");
            Optional<List> docsOpt = caseFile.get("raw_documents", List.class);
            if (docsOpt.isEmpty()) return;
            @SuppressWarnings("unchecked")
            List<String> documents = (List<String>) docsOpt.get();
            Map<String, String> extracted = new LinkedHashMap<>();
            for (int i = 0; i < documents.size(); i++) {
                extracted.put("doc_" + i, documents.get(i));
            }
            caseFile.put("extracted_text", extracted);
            System.out.println("    ✓ Extracted text from " + documents.size() + " documents");
        }
    }

    static class EntityRecognitionTaskDefinition implements TaskDefinition {
        @Override
        public String getId() { return "entity-recognizer"; }
        @Override
        public String getName() { return "Named Entity Recognition"; }
        @Override
        public Set<String> entryCriteria() { return Set.of("extracted_text"); }
        @Override
        public Set<String> producedKeys() { return Set.of("entities"); }
        @Override
        public void execute(CaseFile caseFile) {
            System.out.println("  [EXECUTING] Named Entity Recognition...");
            Optional<Map> textOpt = caseFile.get("extracted_text", Map.class);
            if (textOpt.isEmpty()) return;
            @SuppressWarnings("unchecked")
            Map<String, String> texts = (Map<String, String>) textOpt.get();
            Set<String> organizations = new HashSet<>();
            Set<String> dates = new HashSet<>();
            Set<String> amounts = new HashSet<>();
            for (String text : texts.values()) {
                if (text.contains("Acme Corp")) organizations.add("Acme Corp");
                if (text.contains("Beta Inc")) organizations.add("Beta Inc");
                for (String word : text.split("\\s+")) {
                    if (word.matches("\\d{4}-\\d{2}-\\d{2}")) dates.add(word.replaceAll("[,.]", ""));
                    if (word.startsWith("$") && word.length() > 1) amounts.add(word.replaceAll("[,.]$", ""));
                }
            }
            Map<String, Object> entities = new LinkedHashMap<>();
            entities.put("organizations", new ArrayList<>(organizations));
            entities.put("dates", new ArrayList<>(dates));
            entities.put("monetary_amounts", new ArrayList<>(amounts));
            caseFile.put("entities", entities);
            System.out.println("    ✓ Found " + organizations.size() + " organizations, " + dates.size() + " dates");
        }
    }

    static class RiskAnalysisTaskDefinition implements TaskDefinition {
        @Override
        public String getId() { return "risk-analyzer"; }
        @Override
        public String getName() { return "Risk Assessment"; }
        @Override
        public Set<String> entryCriteria() { return Set.of("extracted_text", "entities"); }
        @Override
        public Set<String> producedKeys() { return Set.of("risk_assessment"); }
        @Override
        public void execute(CaseFile caseFile) {
            System.out.println("  [EXECUTING] Risk Assessment...");
            Optional<Map> textOpt = caseFile.get("extracted_text", Map.class);
            if (textOpt.isEmpty()) return;
            @SuppressWarnings("unchecked")
            Map<String, String> texts = (Map<String, String>) textOpt.get();
            String allText = String.join(" ", texts.values()).toLowerCase();
            List<String> riskFactors = new ArrayList<>();
            int riskScore = 0;
            if (allText.contains("penalty")) { riskFactors.add("Financial penalties specified"); riskScore += 30; }
            if (allText.contains("breach")) { riskFactors.add("Breach clauses present"); riskScore += 25; }
            if (allText.contains("compliance")) { riskFactors.add("Regulatory compliance required"); riskScore += 20; }
            if (allText.contains("termination")) { riskFactors.add("Contract termination clauses"); riskScore += 15; }
            if (allText.contains("indemnification")) { riskFactors.add("Indemnification obligations"); riskScore += 10; }
            Map<String, Object> assessment = new LinkedHashMap<>();
            assessment.put("risk_score", riskScore);
            assessment.put("risk_level", riskScore > 60 ? "HIGH" : riskScore > 30 ? "MEDIUM" : "LOW");
            assessment.put("risk_factors", riskFactors);
            caseFile.put("risk_assessment", assessment);
            System.out.println("    ✓ Risk Level: " + assessment.get("risk_level") + " (score: " + riskScore + "/100)");
        }
    }

    /**
     * Enhanced summary TaskDefinition that incorporates LLM insights.
     */
    static class EnhancedSummaryTaskDefinition implements TaskDefinition {
        @Override
        public String getId() { return "enhanced-summarizer"; }

        @Override
        public String getName() { return "Enhanced Executive Summary"; }

        @Override
        public Set<String> entryCriteria() {
            // Now requires LLM insights
            return Set.of("entities", "risk_assessment", "strategic_recommendations");
        }

        @Override
        public Set<String> producedKeys() {
            return Set.of("executive_summary");
        }

        @Override
        public void execute(CaseFile caseFile) {
            System.out.println("  [EXECUTING] Enhanced Executive Summary (with LLM insights)...");

            Optional<Map> entitiesOpt = caseFile.get("entities", Map.class);
            Optional<Map> riskOpt = caseFile.get("risk_assessment", Map.class);
            Optional<Map> recsOpt = caseFile.get("strategic_recommendations", Map.class);

            if (entitiesOpt.isEmpty() || riskOpt.isEmpty() || recsOpt.isEmpty()) {
                System.out.println("    ⚠️  Missing required data");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> entities = (Map<String, Object>) entitiesOpt.get();
            @SuppressWarnings("unchecked")
            Map<String, Object> risk = (Map<String, Object>) riskOpt.get();
            @SuppressWarnings("unchecked")
            Map<String, Object> recommendations = (Map<String, Object>) recsOpt.get();

            @SuppressWarnings("unchecked")
            List<String> orgs = (List<String>) entities.get("organizations");
            int riskScore = (int) risk.get("risk_score");
            String riskLevel = (String) risk.get("risk_level");

            @SuppressWarnings("unchecked")
            List<String> immediateActions = (List<String>) recommendations.getOrDefault("immediate_actions", List.of());

            StringBuilder summary = new StringBuilder();
            summary.append("ENHANCED EXECUTIVE SUMMARY\n");
            summary.append("══════════════════════════\n");
            summary.append("(AI-Enhanced Analysis)\n\n");

            summary.append("Parties: ").append(String.join(" and ", orgs)).append("\n");
            summary.append("Risk Level: ").append(riskLevel).append(" (").append(riskScore).append("/100)\n\n");

            summary.append("AI-RECOMMENDED IMMEDIATE ACTIONS:\n");
            for (String action : immediateActions) {
                summary.append("  🔴 ").append(action).append("\n");
            }

            summary.append("\nNOTE: This summary incorporates AI-powered strategic analysis\n");
            summary.append("from Claude API via LlmReasoningWorker.");

            caseFile.put("executive_summary", summary.toString());
            caseFile.complete();

            System.out.println("    ✓ Enhanced summary complete with LLM insights");
        }
    }
}
