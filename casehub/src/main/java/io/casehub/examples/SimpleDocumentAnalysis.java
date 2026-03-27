package io.casehub.examples;

import java.util.*;

/**
 * Simplified, self-contained example demonstrating CaseHub's core concepts.
 *
 * This is a **conceptual demonstration** showing:
 * 1. Shared workspace pattern (CaseFile analogy)
 * 2. Data-driven activation (tasks fire when inputs available)
 * 3. Collaborative problem-solving (solution emerges from contributions)
 * 4. Opportunistic execution (no hardcoded workflow)
 *
 * Run: java io.casehub.examples.SimpleDocumentAnalysis
 */
public class SimpleDocumentAnalysis {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  CaseHub Architecture Demo: Legal Document Analysis       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        // The shared workspace (analogous to CaseFile)
        SharedWorkspace workspace = new SharedWorkspace("case-001");

        // Define specialist tasks (analogous to TaskDefinitions)
        List<SpecialistTask> tasks = createTasks();

        System.out.println("📋 Registered Specialist Tasks:");
        for (SpecialistTask task : tasks) {
            System.out.printf("   %s\n", task.name);
            System.out.printf("      Needs: %s\n", task.requiredKeys);
            System.out.printf("      Produces: %s\n\n", task.producedKeys);
        }

        // Initialize workspace with raw documents
        System.out.println("📄 Initial State:");
        List<String> documents = List.of(
                "Contract between Acme Corp and Beta Inc. Effective 2026-01-01. " +
                "Penalty clause: $50,000 for material breach. " +
                "Term: 24 months with auto-renewal.",

                "Addendum dated 2026-02-15. Risk: Regulatory compliance required by 2026-06-01. " +
                "Failure to comply may result in contract termination and financial penalties."
        );
        workspace.put("raw_documents", documents);
        System.out.printf("   raw_documents: %d documents loaded\n\n", documents.size());

        // Simulate CaseEngine control loop
        System.out.println("⚙️  CaseEngine Control Loop:\n");
        System.out.println("   [Checking which tasks can fire based on available data...]\n");

        int iteration = 0;
        boolean progressMade = true;
        Set<String> completed = new HashSet<>();

        while (progressMade && iteration < 10) {
            progressMade = false;
            iteration++;

            System.out.println("   ─────────── Iteration " + iteration + " ───────────");

            // Check each task's preconditions
            for (SpecialistTask task : tasks) {
                if (!completed.contains(task.name) && canExecute(task, workspace)) {
                    System.out.println("   ✓ " + task.name + " is eligible (has all required keys)");

                    // Execute the task
                    task.execute(workspace);
                    completed.add(task.name);
                    progressMade = true;

                    System.out.println("     └─> Contributed: " + task.producedKeys);
                    System.out.println();
                }
            }

            if (!progressMade) {
                System.out.println("   ⏸  No more eligible tasks (quiescent state)\n");
            }
        }

        // Display final results
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("📊 FINAL WORKSPACE STATE:\n");
        workspace.display();

        System.out.println("\n" + "═".repeat(63));
        System.out.println("🎯 KEY ARCHITECTURAL FEATURES DEMONSTRATED:\n");
        System.out.println("   ✓ Data-Driven Activation");
        System.out.println("     Each task fired only when its required keys were present\n");
        System.out.println("   ✓ Collaborative Problem-Solving");
        System.out.println("     4 independent specialists built the solution incrementally\n");
        System.out.println("   ✓ Shared Workspace Pattern");
        System.out.println("     All specialists read/write from common data space\n");
        System.out.println("   ✓ Opportunistic Execution");
        System.out.println("     No hardcoded workflow - order emerged from data dependencies\n");
        System.out.println("   ✓ Blackboard Architecture");
        System.out.println("     Classic separation: workspace, knowledge sources, control\n");
        System.out.println("═══════════════════════════════════════════════════════════\n");
    }

    private static boolean canExecute(SpecialistTask task, SharedWorkspace workspace) {
        for (String requiredKey : task.requiredKeys) {
            if (!workspace.contains(requiredKey)) {
                return false;
            }
        }
        return true;
    }

    private static List<SpecialistTask> createTasks() {
        List<SpecialistTask> tasks = new ArrayList<>();

        // 1. Text Extraction
        tasks.add(new SpecialistTask(
                "Text Extraction",
                Set.of("raw_documents"),
                Set.of("extracted_text"),
                workspace -> {
                    System.out.println("     [EXECUTING] Extracting text from documents...");
                    @SuppressWarnings("unchecked")
                    List<String> docs = (List<String>) workspace.get("raw_documents");

                    Map<String, String> extracted = new LinkedHashMap<>();
                    for (int i = 0; i < docs.size(); i++) {
                        extracted.put("doc_" + i, docs.get(i));
                    }
                    workspace.put("extracted_text", extracted);
                }
        ));

        // 2. Named Entity Recognition
        tasks.add(new SpecialistTask(
                "Named Entity Recognition",
                Set.of("extracted_text"),
                Set.of("entities"),
                workspace -> {
                    System.out.println("     [EXECUTING] Extracting named entities...");
                    @SuppressWarnings("unchecked")
                    Map<String, String> texts = (Map<String, String>) workspace.get("extracted_text");

                    Set<String> organizations = new HashSet<>();
                    Set<String> dates = new HashSet<>();
                    Set<String> amounts = new HashSet<>();

                    for (String text : texts.values()) {
                        if (text.contains("Acme Corp")) organizations.add("Acme Corp");
                        if (text.contains("Beta Inc")) organizations.add("Beta Inc");

                        String[] words = text.split("\\s+");
                        for (String word : words) {
                            if (word.matches("\\d{4}-\\d{2}-\\d{2}")) {
                                dates.add(word.replaceAll("[,.]", ""));
                            }
                            if (word.startsWith("$") && word.length() > 1) {
                                amounts.add(word.replaceAll("[,.]$", ""));
                            }
                        }
                    }

                    Map<String, Object> entities = new LinkedHashMap<>();
                    entities.put("organizations", new ArrayList<>(organizations));
                    entities.put("dates", new ArrayList<>(dates));
                    entities.put("monetary_amounts", new ArrayList<>(amounts));

                    workspace.put("entities", entities);
                }
        ));

        // 3. Risk Analysis
        tasks.add(new SpecialistTask(
                "Risk Assessment",
                Set.of("entities", "extracted_text"),
                Set.of("risk_assessment"),
                workspace -> {
                    System.out.println("     [EXECUTING] Analyzing risk factors...");
                    @SuppressWarnings("unchecked")
                    Map<String, String> texts = (Map<String, String>) workspace.get("extracted_text");

                    String allText = String.join(" ", texts.values()).toLowerCase();

                    List<String> riskFactors = new ArrayList<>();
                    int riskScore = 0;

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

                    Map<String, Object> assessment = new LinkedHashMap<>();
                    assessment.put("risk_score", riskScore);
                    assessment.put("risk_level", riskScore > 50 ? "HIGH" : riskScore > 30 ? "MEDIUM" : "LOW");
                    assessment.put("risk_factors", riskFactors);

                    workspace.put("risk_assessment", assessment);
                }
        ));

        // 4. Executive Summary
        tasks.add(new SpecialistTask(
                "Executive Summary",
                Set.of("entities", "risk_assessment"),
                Set.of("executive_summary"),
                workspace -> {
                    System.out.println("     [EXECUTING] Generating executive summary...");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> entities = (Map<String, Object>) workspace.get("entities");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> risk = (Map<String, Object>) workspace.get("risk_assessment");

                    @SuppressWarnings("unchecked")
                    List<String> orgs = (List<String>) entities.get("organizations");
                    @SuppressWarnings("unchecked")
                    List<String> dates = (List<String>) entities.get("dates");
                    int riskScore = (int) risk.get("risk_score");
                    String riskLevel = (String) risk.get("risk_level");
                    @SuppressWarnings("unchecked")
                    List<String> riskFactors = (List<String>) risk.get("risk_factors");

                    StringBuilder summary = new StringBuilder();
                    summary.append("EXECUTIVE SUMMARY\n");
                    summary.append("─────────────────\n\n");
                    summary.append("Parties: ").append(String.join(" and ", orgs)).append("\n");
                    summary.append("Key Dates: ").append(String.join(", ", dates)).append("\n\n");
                    summary.append(String.format("Risk Assessment: %s (%d/100)\n", riskLevel, riskScore));
                    summary.append("\nIdentified Risk Factors:\n");
                    for (String factor : riskFactors) {
                        summary.append("  • ").append(factor).append("\n");
                    }
                    summary.append("\nRecommendations:\n");
                    if (riskScore > 50) {
                        summary.append("  • URGENT: Legal review required before execution\n");
                        summary.append("  • Escalate to senior counsel\n");
                        summary.append("  • Consider penalty cap negotiations\n");
                    } else if (riskScore > 30) {
                        summary.append("  • Standard legal review recommended\n");
                        summary.append("  • Monitor compliance deadlines\n");
                    } else {
                        summary.append("  • Standard approval process\n");
                    }
                    summary.append("  • Archive for audit trail\n");

                    workspace.put("executive_summary", summary.toString());
                }
        ));

        return tasks;
    }

    /**
     * Simple shared workspace (conceptual analogy to CaseFile)
     */
    static class SharedWorkspace {
        private final String id;
        private final Map<String, Object> data = new LinkedHashMap<>();

        public SharedWorkspace(String id) {
            this.id = id;
        }

        public void put(String key, Object value) {
            data.put(key, value);
        }

        public Object get(String key) {
            return data.get(key);
        }

        public boolean contains(String key) {
            return data.containsKey(key);
        }

        public void display() {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                System.out.printf("   📌 %s:\n", key);

                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<?, ?> map = (Map<?, ?>) value;
                    for (Map.Entry<?, ?> item : map.entrySet()) {
                        System.out.printf("      %s: %s\n", item.getKey(), formatValue(item.getValue()));
                    }
                } else if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<?> list = (List<?>) value;
                    for (Object item : list) {
                        System.out.printf("      • %s\n", formatValue(item));
                    }
                } else if (value instanceof String && ((String) value).contains("\n")) {
                    for (String line : ((String) value).split("\n")) {
                        System.out.printf("      %s\n", line);
                    }
                } else {
                    System.out.printf("      %s\n", formatValue(value));
                }
                System.out.println();
            }
        }

        private String formatValue(Object value) {
            if (value instanceof String) {
                String str = (String) value;
                return str.length() > 80 ? str.substring(0, 77) + "..." : str;
            }
            return String.valueOf(value);
        }
    }

    /**
     * Simple task abstraction (conceptual analogy to TaskDefinition)
     */
    static class SpecialistTask {
        final String name;
        final Set<String> requiredKeys;
        final Set<String> producedKeys;
        final TaskExecutor executor;

        public SpecialistTask(String name, Set<String> requiredKeys, Set<String> producedKeys, TaskExecutor executor) {
            this.name = name;
            this.requiredKeys = requiredKeys;
            this.producedKeys = producedKeys;
            this.executor = executor;
        }

        public void execute(SharedWorkspace workspace) {
            executor.execute(workspace);
        }
    }

    @FunctionalInterface
    interface TaskExecutor {
        void execute(SharedWorkspace workspace);
    }
}
