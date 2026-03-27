package io.casehub.examples.workers;

import io.casehub.core.CaseFile;
import io.casehub.core.TaskDefinition;
import io.casehub.worker.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * TaskDefinition that delegates complex reasoning to an LlmReasoningWorker.
 *
 * This demonstrates the dual execution model:
 * 1. CaseFile Model: TaskDefinition participates in collaborative problem-solving
 * 2. Task Model: TaskDefinition delegates heavy work to external Workers
 *
 * Entry Criteria: ["extracted_text", "entities", "risk_assessment"]
 * Produces: ["strategic_recommendations"]
 *
 * Flow:
 * 1. Read data from CaseFile (extracted_text, entities, risk_assessment)
 * 2. Build a complex prompt for LLM analysis
 * 3. Submit task to LlmReasoningWorker via TaskBroker
 * 4. Wait for Worker to complete analysis
 * 5. Contribute Worker's insights back to CaseFile
 *
 * This TaskDefinition shows how CaseFile model and Task model work together seamlessly.
 */
public class LlmAnalysisTaskDefinition implements TaskDefinition {

    private final TaskBroker taskBroker;

    public LlmAnalysisTaskDefinition(TaskBroker taskBroker) {
        this.taskBroker = taskBroker;
    }

    @Override
    public String getId() {
        return "llm-strategic-analysis";
    }

    @Override
    public String getName() {
        return "LLM-Powered Strategic Analysis";
    }

    @Override
    public Set<String> entryCriteria() {
        // This TaskDefinition fires after basic analysis is complete
        return Set.of("extracted_text", "entities", "risk_assessment");
    }

    @Override
    public Set<String> producedKeys() {
        return Set.of("strategic_recommendations", "llm_insights");
    }

    @Override
    public void execute(CaseFile caseFile) {
        System.out.println("  [EXECUTING] LLM Strategic Analysis (delegating to Worker)...");

        try {
            // 1. Gather data from CaseFile
            Optional<Map> extractedTextOpt = caseFile.get("extracted_text", Map.class);
            Optional<Map> entitiesOpt = caseFile.get("entities", Map.class);
            Optional<Map> riskOpt = caseFile.get("risk_assessment", Map.class);

            if (extractedTextOpt.isEmpty() || entitiesOpt.isEmpty() || riskOpt.isEmpty()) {
                System.out.println("    ⚠️  Missing required data for LLM analysis");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, String> extractedText = (Map<String, String>) extractedTextOpt.get();
            @SuppressWarnings("unchecked")
            Map<String, Object> entities = (Map<String, Object>) entitiesOpt.get();
            @SuppressWarnings("unchecked")
            Map<String, Object> riskAssessment = (Map<String, Object>) riskOpt.get();

            // 2. Build comprehensive prompt for LLM
            String prompt = buildAnalysisPrompt(extractedText, entities, riskAssessment);

            // 3. Submit task to LlmReasoningWorker via Task model
            TaskRequest request = TaskRequest.builder()
                    .taskType("strategic-contract-analysis")
                    .context(Map.of(
                        "prompt", prompt,
                        "max_tokens", 3000,
                        "model", "claude-sonnet-4-20250514"
                    ))
                    .requiredCapabilities(Set.of("llm", "reasoning"))
                    .propagationContext(caseFile.getPropagationContext())
                    .build();

            System.out.println("    → Submitting task to LlmReasoningWorker...");
            TaskHandle handle = taskBroker.submitTask(request);

            // 4. Wait for Worker to complete (with timeout)
            System.out.println("    ⏳ Waiting for LLM analysis (timeout: 2 minutes)...");
            TaskResult result = handle.awaitResult(Duration.ofMinutes(2));

            // 5. Process Worker's result
            if (result.getStatus() == TaskStatus.COMPLETED) {
                String llmResponse = (String) result.getData().get("response");
                Integer tokensUsed = (Integer) result.getData().get("tokens_used");

                System.out.println("    ✓ LLM analysis complete (tokens: " + tokensUsed + ")");

                // Parse LLM response into structured data
                Map<String, Object> insights = parseLlmResponse(llmResponse);

                // 6. Contribute Worker's insights back to CaseFile
                caseFile.put("llm_insights", llmResponse);
                caseFile.put("strategic_recommendations", insights);

                System.out.println("    ✓ Contributed strategic recommendations to CaseFile");

            } else {
                // Worker failed
                String errorMsg = result.getError()
                        .map(e -> e.getMessage())
                        .orElse("Unknown error");

                System.err.println("    ✗ LLM analysis failed: " + errorMsg);

                // Could implement fallback logic here
                caseFile.put("llm_insights", "Analysis failed: " + errorMsg);
            }

        } catch (TimeoutException e) {
            System.err.println("    ✗ LLM analysis timed out after 2 minutes");
            caseFile.put("llm_insights", "Analysis timed out - please retry");

        } catch (InterruptedException e) {
            System.err.println("    ✗ LLM analysis interrupted");
            Thread.currentThread().interrupt();

        } catch (Exception e) {
            System.err.println("    ✗ Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Build a comprehensive prompt for strategic analysis.
     */
    private String buildAnalysisPrompt(Map<String, String> extractedText,
                                      Map<String, Object> entities,
                                      Map<String, Object> riskAssessment) {

        @SuppressWarnings("unchecked")
        List<String> orgs = (List<String>) entities.getOrDefault("organizations", List.of());
        int riskScore = (int) riskAssessment.getOrDefault("risk_score", 0);
        String riskLevel = (String) riskAssessment.getOrDefault("risk_level", "UNKNOWN");
        @SuppressWarnings("unchecked")
        List<String> riskFactors = (List<String>) riskAssessment.getOrDefault("risk_factors", List.of());

        // Get first document text as context
        String documentContext = extractedText.values().stream()
                .findFirst()
                .orElse("");

        return String.format("""
            You are a legal AI assistant analyzing a contract between %s.

            RISK ASSESSMENT SUMMARY:
            - Risk Level: %s
            - Risk Score: %d/100
            - Key Risk Factors:
            %s

            Your task: Provide strategic recommendations for this contract.

            Analyze:
            1. CRITICAL ACTIONS: What must be done immediately?
            2. NEGOTIATION STRATEGY: What terms should be renegotiated?
            3. TIMELINE: What are the key deadlines and compliance dates?
            4. MITIGATION: How can identified risks be mitigated?
            5. DECISION: Should this contract proceed, be renegotiated, or be rejected?

            Provide a clear, actionable response structured as:
            - IMMEDIATE ACTIONS: [bullet list]
            - NEGOTIATION POINTS: [bullet list]
            - KEY DEADLINES: [bullet list]
            - RISK MITIGATION: [bullet list]
            - RECOMMENDATION: [clear decision with rationale]

            Be specific and reference actual contract terms where relevant.
            """,
            String.join(" and ", orgs),
            riskLevel,
            riskScore,
            riskFactors.stream()
                .map(f -> "  • " + f)
                .reduce("", (a, b) -> a + b + "\n")
        );
    }

    /**
     * Parse LLM response into structured recommendations.
     * In production, this could use more sophisticated parsing.
     */
    private Map<String, Object> parseLlmResponse(String llmResponse) {
        Map<String, Object> recommendations = new HashMap<>();

        // Extract sections (simple parsing - production would be more robust)
        recommendations.put("immediate_actions", extractSection(llmResponse, "IMMEDIATE ACTIONS"));
        recommendations.put("negotiation_points", extractSection(llmResponse, "NEGOTIATION POINTS"));
        recommendations.put("key_deadlines", extractSection(llmResponse, "KEY DEADLINES"));
        recommendations.put("risk_mitigation", extractSection(llmResponse, "RISK MITIGATION"));
        recommendations.put("recommendation", extractSection(llmResponse, "RECOMMENDATION"));
        recommendations.put("full_analysis", llmResponse);

        return recommendations;
    }

    /**
     * Extract a section from the LLM response.
     */
    private List<String> extractSection(String response, String sectionName) {
        List<String> items = new ArrayList<>();

        int sectionStart = response.indexOf(sectionName);
        if (sectionStart == -1) {
            return items;
        }

        int contentStart = response.indexOf(":", sectionStart) + 1;
        int nextSectionStart = response.indexOf("\n\n", contentStart);
        if (nextSectionStart == -1) {
            nextSectionStart = response.length();
        }

        String sectionContent = response.substring(contentStart, nextSectionStart).trim();

        // Extract bullet points
        String[] lines = sectionContent.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("•") || line.startsWith("-") || line.startsWith("*")) {
                items.add(line.substring(1).trim());
            } else if (!line.isEmpty() && line.contains(":")) {
                items.add(line);
            }
        }

        return items;
    }

    @Override
    public Duration getExecutionTimeout() {
        // LLM analysis can take a while
        return Duration.ofMinutes(3);
    }
}
