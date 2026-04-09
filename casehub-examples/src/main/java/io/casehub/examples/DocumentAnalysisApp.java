package io.casehub.examples;

import io.casehub.coordination.CaseEngine;
import io.casehub.core.CaseFile;
import io.casehub.core.CaseStatus;
import io.casehub.core.TaskDefinition;
import io.casehub.core.TaskDefinitionRegistry;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * Comprehensive example demonstrating the CaseHub architecture using the actual implementation.
 *
 * This example shows:
 * 1. CaseFile Model - Collaborative problem-solving with shared workspace
 * 2. Data-Driven Activation - TaskDefinitions fire when inputs are available
 * 3. Control Loop - CaseEngine orchestrates execution
 * 4. Real TaskDefinition implementations
 * 5. Actual CaseFile API usage
 *
 * Scenario: Legal Document Analysis
 * ================================
 * Input: Raw legal documents
 * Output: Structured analysis with entities, risk assessment, and summary
 *
 * Workflow (data-driven, not hardcoded):
 *   raw_documents → Text Extraction → extracted_text
 *   extracted_text → Entity Recognition → entities
 *   (extracted_text + entities) → Risk Analysis → risk_assessment
 *   (entities + risk_assessment) → Summary → executive_summary
 *
 * Run with: mvn quarkus:dev
 */
@QuarkusMain
public class DocumentAnalysisApp implements QuarkusApplication {

    @Inject
    CaseEngine caseEngine;

    @Inject
    TaskDefinitionRegistry registry;

    @Override
    public int run(String... args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  CaseHub Architecture Demo: Legal Document Analysis       ║");
        System.out.println("║  Using Real CaseHub Implementation                         ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        try {
            // Step 1: Register TaskDefinitions
            registerTaskDefinitions();

            // Step 2: Create initial state
            Map<String, Object> initialState = createInitialState();

            // Step 3: Create and solve the case
            System.out.println("⚙️  Creating case and starting CaseEngine control loop...\n");
            CaseFile caseFile = caseEngine.createAndSolve("legal-document-analysis", initialState);

            // Step 4: Wait for completion
            System.out.println("⏳ Waiting for case to complete...\n");
            caseEngine.awaitCompletion(caseFile, Duration.ofMinutes(1));

            // Step 5: Display results
            displayResults(caseFile);

            return 0;

        } catch (InterruptedException | TimeoutException e) {
            System.err.println("❌ Case execution timed out or was interrupted: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private void registerTaskDefinitions() {
        System.out.println("📋 Registering TaskDefinitions...\n");

        String caseType = "legal-document-analysis";
        Set<String> caseTypes = Set.of(caseType);

        // 1. Text Extraction TaskDefinition
        TaskDefinition textExtractor = new TextExtractionTaskDefinition();
        registry.register(textExtractor, caseTypes);
        System.out.println("  ✓ Registered: " + textExtractor.getName());
        System.out.println("    Needs: " + textExtractor.entryCriteria());
        System.out.println("    Produces: " + textExtractor.producedKeys());

        // 2. Entity Recognition TaskDefinition
        TaskDefinition entityRecognizer = new EntityRecognitionTaskDefinition();
        registry.register(entityRecognizer, caseTypes);
        System.out.println("  ✓ Registered: " + entityRecognizer.getName());
        System.out.println("    Needs: " + entityRecognizer.entryCriteria());
        System.out.println("    Produces: " + entityRecognizer.producedKeys());

        // 3. Risk Analysis TaskDefinition
        TaskDefinition riskAnalyzer = new RiskAnalysisTaskDefinition();
        registry.register(riskAnalyzer, caseTypes);
        System.out.println("  ✓ Registered: " + riskAnalyzer.getName());
        System.out.println("    Needs: " + riskAnalyzer.entryCriteria());
        System.out.println("    Produces: " + riskAnalyzer.producedKeys());

        // 4. Summary Generation TaskDefinition
        TaskDefinition summarizer = new SummaryTaskDefinition();
        registry.register(summarizer, caseTypes);
        System.out.println("  ✓ Registered: " + summarizer.getName());
        System.out.println("    Needs: " + summarizer.entryCriteria());
        System.out.println("    Produces: " + summarizer.producedKeys());

        System.out.println();
    }

    private Map<String, Object> createInitialState() {
        System.out.println("📄 Creating Initial State...\n");

        List<String> documents = List.of(
                "Contract between Acme Corp and Beta Inc. Effective 2026-01-01. " +
                "Penalty clause: $50,000 for material breach. " +
                "Term: 24 months with auto-renewal. " +
                "Governing law: Delaware.",

                "Addendum dated 2026-02-15. Risk: Regulatory compliance required by 2026-06-01. " +
                "Failure to comply may result in contract termination and financial penalties. " +
                "Indemnification clause applies to all regulatory violations."
        );

        Map<String, Object> initialState = new HashMap<>();
        initialState.put("raw_documents", documents);

        System.out.println("  Initial CaseFile contains:");
        System.out.println("    raw_documents: " + documents.size() + " documents");
        System.out.println();

        return initialState;
    }

    private void displayResults(CaseFile caseFile) {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("📊 FINAL RESULTS\n");

        System.out.println("  Case ID: " + caseFile.getId());
        System.out.println("  Status: " + caseFile.getStatus());
        System.out.println("  Created: " + caseFile.getCreatedAt());
        System.out.println();

        if (caseFile.getStatus() != CaseStatus.WAITING && caseFile.getStatus() != CaseStatus.COMPLETED) {
            System.out.println("  ⚠️  Case did not complete successfully");
            return;
        }

        System.out.println("  CaseFile Contents:");
        System.out.println("  ─────────────────");

        // Display extracted text
        caseFile.get("extracted_text", Map.class).ifPresent(extracted -> {
            System.out.println("\n  📌 extracted_text:");
            Map<String, String> textMap = (Map<String, String>) extracted;
            textMap.forEach((key, value) -> {
                String preview = value.length() > 80 ? value.substring(0, 77) + "..." : value;
                System.out.println("      " + key + ": " + preview);
            });
        });

        // Display entities
        caseFile.get("entities", Map.class).ifPresent(entities -> {
            System.out.println("\n  📌 entities:");
            Map<String, Object> entityMap = (Map<String, Object>) entities;
            entityMap.forEach((key, value) -> {
                System.out.println("      " + key + ": " + value);
            });
        });

        // Display risk assessment
        caseFile.get("risk_assessment", Map.class).ifPresent(risk -> {
            System.out.println("\n  📌 risk_assessment:");
            Map<String, Object> riskMap = (Map<String, Object>) risk;
            System.out.println("      risk_score: " + riskMap.get("risk_score"));
            System.out.println("      risk_level: " + riskMap.get("risk_level"));
            System.out.println("      risk_factors: " + riskMap.get("risk_factors"));
        });

        // Display summary
        caseFile.get("executive_summary", String.class).ifPresent(summary -> {
            System.out.println("\n  📌 executive_summary:");
            for (String line : summary.split("\n")) {
                System.out.println("      " + line);
            }
        });

        System.out.println("\n" + "═".repeat(63));
        System.out.println("🎯 KEY ARCHITECTURAL FEATURES DEMONSTRATED:\n");
        System.out.println("   ✓ Data-Driven Activation");
        System.out.println("     Each TaskDefinition fired when its entry criteria were met\n");
        System.out.println("   ✓ Collaborative Problem-Solving");
        System.out.println("     4 independent TaskDefinitions built the solution\n");
        System.out.println("   ✓ Shared Workspace (CaseFile)");
        System.out.println("     All TaskDefinitions read/write from common CaseFile\n");
        System.out.println("   ✓ Control Loop (CaseEngine)");
        System.out.println("     Automatic orchestration, evaluation, and execution\n");
        System.out.println("   ✓ Real CaseHub Implementation");
        System.out.println("     Using actual TaskDefinition, CaseFile, CaseEngine APIs\n");
        System.out.println("═══════════════════════════════════════════════════════════\n");
    }

    //
    // TaskDefinition Implementations
    //

    /**
     * Extracts text from raw documents (simulates OCR).
     */
    static class TextExtractionTaskDefinition implements TaskDefinition {
        @Override
        public String getId() {
            return "text-extractor";
        }

        @Override
        public String getName() {
            return "Text Extraction";
        }

        @Override
        public Set<String> entryCriteria() {
            return Set.of("raw_documents");
        }

        @Override
        public Set<String> producedKeys() {
            return Set.of("extracted_text");
        }

        @Override
        public void execute(CaseFile caseFile) {
            System.out.println("  [EXECUTING] Text Extraction...");

            Optional<List> docsOpt = caseFile.get("raw_documents", List.class);
            if (docsOpt.isEmpty()) {
                System.out.println("    ⚠️  No raw_documents found");
                return;
            }

            @SuppressWarnings("unchecked")
            List<String> documents = (List<String>) docsOpt.get();

            // Simulate text extraction (in reality, would call OCR service)
            Map<String, String> extracted = new LinkedHashMap<>();
            for (int i = 0; i < documents.size(); i++) {
                extracted.put("doc_" + i, documents.get(i));
            }

            caseFile.put("extracted_text", extracted);
            System.out.println("    ✓ Extracted text from " + documents.size() + " documents");
        }
    }

    /**
     * Performs Named Entity Recognition on extracted text.
     */
    static class EntityRecognitionTaskDefinition implements TaskDefinition {
        @Override
        public String getId() {
            return "entity-recognizer";
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
            System.out.println("  [EXECUTING] Named Entity Recognition...");

            Optional<Map> textOpt = caseFile.get("extracted_text", Map.class);
            if (textOpt.isEmpty()) {
                System.out.println("    ⚠️  No extracted_text found");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, String> texts = (Map<String, String>) textOpt.get();

            // Simple pattern matching for entities
            Set<String> organizations = new HashSet<>();
            Set<String> dates = new HashSet<>();
            Set<String> amounts = new HashSet<>();
            Set<String> locations = new HashSet<>();

            for (String text : texts.values()) {
                // Organizations
                if (text.contains("Acme Corp")) organizations.add("Acme Corp");
                if (text.contains("Beta Inc")) organizations.add("Beta Inc");

                // Dates
                String[] words = text.split("\\s+");
                for (String word : words) {
                    if (word.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        dates.add(word.replaceAll("[,.]", ""));
                    }
                }

                // Amounts
                for (String word : words) {
                    if (word.startsWith("$") && word.length() > 1) {
                        amounts.add(word.replaceAll("[,.]$", ""));
                    }
                }

                // Locations
                if (text.contains("Delaware")) locations.add("Delaware");
            }

            Map<String, Object> entities = new LinkedHashMap<>();
            entities.put("organizations", new ArrayList<>(organizations));
            entities.put("dates", new ArrayList<>(dates));
            entities.put("monetary_amounts", new ArrayList<>(amounts));
            entities.put("locations", new ArrayList<>(locations));

            caseFile.put("entities", entities);
            System.out.println("    ✓ Found " + organizations.size() + " organizations, " +
                    dates.size() + " dates, " + amounts.size() + " amounts");
        }
    }

    /**
     * Analyzes risk based on extracted text and entities.
     */
    static class RiskAnalysisTaskDefinition implements TaskDefinition {
        @Override
        public String getId() {
            return "risk-analyzer";
        }

        @Override
        public String getName() {
            return "Risk Assessment";
        }

        @Override
        public Set<String> entryCriteria() {
            return Set.of("extracted_text", "entities");
        }

        @Override
        public Set<String> producedKeys() {
            return Set.of("risk_assessment");
        }

        @Override
        public void execute(CaseFile caseFile) {
            System.out.println("  [EXECUTING] Risk Assessment...");

            Optional<Map> textOpt = caseFile.get("extracted_text", Map.class);
            if (textOpt.isEmpty()) {
                System.out.println("    ⚠️  No extracted_text found");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, String> texts = (Map<String, String>) textOpt.get();
            String allText = String.join(" ", texts.values()).toLowerCase();

            List<String> riskFactors = new ArrayList<>();
            int riskScore = 0;

            // Risk detection heuristics
            if (allText.contains("penalty") || allText.contains("penalties")) {
                riskFactors.add("Financial penalties specified");
                riskScore += 30;
            }
            if (allText.contains("breach")) {
                riskFactors.add("Breach clauses present");
                riskScore += 25;
            }
            if (allText.contains("compliance")) {
                riskFactors.add("Regulatory compliance required");
                riskScore += 20;
            }
            if (allText.contains("termination")) {
                riskFactors.add("Contract termination clauses");
                riskScore += 15;
            }
            if (allText.contains("auto-renewal")) {
                riskFactors.add("Auto-renewal terms present");
                riskScore += 10;
            }
            if (allText.contains("indemnification")) {
                riskFactors.add("Indemnification obligations");
                riskScore += 10;
            }

            String riskLevel = riskScore > 60 ? "HIGH" : riskScore > 30 ? "MEDIUM" : "LOW";

            Map<String, Object> assessment = new LinkedHashMap<>();
            assessment.put("risk_score", riskScore);
            assessment.put("risk_level", riskLevel);
            assessment.put("risk_factors", riskFactors);

            caseFile.put("risk_assessment", assessment);
            System.out.println("    ✓ Risk Level: " + riskLevel + " (score: " + riskScore + "/100)");
        }
    }

    /**
     * Generates executive summary from all analyzed data.
     */
    static class SummaryTaskDefinition implements TaskDefinition {
        @Override
        public String getId() {
            return "summarizer";
        }

        @Override
        public String getName() {
            return "Executive Summary Generator";
        }

        @Override
        public Set<String> entryCriteria() {
            return Set.of("entities", "risk_assessment");
        }

        @Override
        public Set<String> producedKeys() {
            return Set.of("executive_summary");
        }

        @Override
        public void execute(CaseFile caseFile) {
            System.out.println("  [EXECUTING] Executive Summary Generation...");

            Optional<Map> entitiesOpt = caseFile.get("entities", Map.class);
            Optional<Map> riskOpt = caseFile.get("risk_assessment", Map.class);

            if (entitiesOpt.isEmpty() || riskOpt.isEmpty()) {
                System.out.println("    ⚠️  Missing required data");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> entities = (Map<String, Object>) entitiesOpt.get();
            @SuppressWarnings("unchecked")
            Map<String, Object> risk = (Map<String, Object>) riskOpt.get();

            @SuppressWarnings("unchecked")
            List<String> orgs = (List<String>) entities.get("organizations");
            @SuppressWarnings("unchecked")
            List<String> dates = (List<String>) entities.get("dates");
            @SuppressWarnings("unchecked")
            List<String> locations = (List<String>) entities.get("locations");

            int riskScore = (int) risk.get("risk_score");
            String riskLevel = (String) risk.get("risk_level");
            @SuppressWarnings("unchecked")
            List<String> riskFactors = (List<String>) risk.get("risk_factors");

            StringBuilder summary = new StringBuilder();
            summary.append("EXECUTIVE SUMMARY\n");
            summary.append("═════════════════\n\n");

            summary.append("Contract Parties:\n");
            for (String org : orgs) {
                summary.append("  • ").append(org).append("\n");
            }
            summary.append("\n");

            if (!dates.isEmpty()) {
                summary.append("Key Dates: ").append(String.join(", ", dates)).append("\n");
            }
            if (!locations.isEmpty()) {
                summary.append("Jurisdiction: ").append(String.join(", ", locations)).append("\n");
            }
            summary.append("\n");

            summary.append(String.format("RISK ASSESSMENT: %s (%d/100)\n", riskLevel, riskScore));
            summary.append("─────────────────────────────────\n");
            if (!riskFactors.isEmpty()) {
                summary.append("Identified Risk Factors:\n");
                for (String factor : riskFactors) {
                    summary.append("  • ").append(factor).append("\n");
                }
            }
            summary.append("\n");

            summary.append("RECOMMENDATIONS:\n");
            summary.append("────────────────\n");
            if (riskScore > 60) {
                summary.append("  🔴 URGENT: High-risk contract\n");
                summary.append("  • Escalate to senior legal counsel immediately\n");
                summary.append("  • Negotiate penalty cap and compliance timeline\n");
                summary.append("  • Legal review required before execution\n");
            } else if (riskScore > 30) {
                summary.append("  🟡 MODERATE: Standard legal review recommended\n");
                summary.append("  • Review compliance requirements and deadlines\n");
                summary.append("  • Monitor auto-renewal dates\n");
            } else {
                summary.append("  🟢 LOW: Standard approval process\n");
                summary.append("  • Routine contract review\n");
            }
            summary.append("  • Archive for compliance audit trail\n");

            caseFile.put("executive_summary", summary.toString());
            caseFile.complete();  // Mark case as complete
            System.out.println("    ✓ Executive summary generated, case marked complete");
        }
    }
}
