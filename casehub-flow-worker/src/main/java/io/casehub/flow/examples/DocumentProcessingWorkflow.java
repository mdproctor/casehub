package io.casehub.flow.examples;

import io.casehub.flow.FlowExecutionContext;
import io.casehub.flow.FlowWorkflowDefinition;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;

/**
 * Example Quarkus Flow workflow for document processing.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>Multi-step workflow execution</li>
 *   <li>Reading input from FlowExecutionContext</li>
 *   <li>Sequential processing steps</li>
 *   <li>Result aggregation and return</li>
 *   <li>Error handling</li>
 * </ul>
 *
 * <p><b>Workflow Steps:</b>
 * <ol>
 *   <li><b>Extract Text</b> - Extract text from document URL</li>
 *   <li><b>Recognize Entities</b> - Identify named entities in text</li>
 *   <li><b>Analyze Sentiment</b> - Determine sentiment (positive/negative/neutral)</li>
 *   <li><b>Generate Summary</b> - Create executive summary</li>
 * </ol>
 *
 * <p><b>Input:</b>
 * <pre>{@code
 * {
 *   "document_url": "https://example.com/document.pdf",
 *   "language": "en" (optional)
 * }
 * }</pre>
 *
 * <p><b>Output:</b>
 * <pre>{@code
 * {
 *   "extracted_text": "...",
 *   "entities": [{"name": "Acme Corp", "type": "organization"}, ...],
 *   "sentiment": {"overall": "positive", "score": 0.75},
 *   "summary": "...",
 *   "metadata": {
 *     "word_count": 1500,
 *     "processing_time_ms": 2341,
 *     "processed_at": "2026-03-27T03:00:00Z"
 *   }
 * }
 * }</pre>
 */
public class DocumentProcessingWorkflow implements FlowWorkflowDefinition {

    private static final Logger log = Logger.getLogger(DocumentProcessingWorkflow.class);

    @Override
    public String getWorkflowId() {
        return "document-processing-flow";
    }

    @Override
    public String getDescription() {
        return "Multi-step document processing workflow: extract → entities → sentiment → summary";
    }

    @Override
    public Set<String> getRequiredCapabilities() {
        return Set.of("flow", "nlp", "text-processing");
    }

    @Override
    public long getEstimatedDurationMs() {
        return 30_000;  // 30 seconds
    }

    @Override
    public Map<String, Object> execute(FlowExecutionContext context) throws Exception {
        log.infof("🔄 Starting document processing workflow (task: %s, trace: %s)",
                 context.getTaskId(), context.getTraceId());

        Instant startTime = Instant.now();

        // Step 1: Validate and extract input
        String documentUrl = context.getInput("document_url", String.class)
                .orElseThrow(() -> new IllegalArgumentException("Missing required input: document_url"));

        String language = context.getInput("language", String.class).orElse("en");

        log.infof("  Input: document_url=%s, language=%s", documentUrl, language);

        // Check budget before proceeding
        if (context.isBudgetExhausted()) {
            throw new RuntimeException("Execution budget exhausted before workflow start");
        }

        // Step 2: Extract text from document
        log.info("  [1/4] Extracting text...");
        String extractedText = extractText(documentUrl);
        int wordCount = countWords(extractedText);
        log.infof("    ✓ Extracted %d words", wordCount);

        // Step 3: Recognize named entities
        log.info("  [2/4] Recognizing entities...");
        List<Map<String, String>> entities = recognizeEntities(extractedText, language);
        log.infof("    ✓ Found %d entities", entities.size());

        // Step 4: Analyze sentiment
        log.info("  [3/4] Analyzing sentiment...");
        Map<String, Object> sentiment = analyzeSentiment(extractedText, language);
        log.infof("    ✓ Sentiment: %s (score: %.2f)",
                 sentiment.get("overall"), sentiment.get("score"));

        // Step 5: Generate summary
        log.info("  [4/4] Generating summary...");
        String summary = generateSummary(extractedText, entities, sentiment);
        log.infof("    ✓ Summary generated (%d chars)", summary.length());

        Instant endTime = Instant.now();
        long processingTimeMs = endTime.toEpochMilli() - startTime.toEpochMilli();

        // Assemble results
        Map<String, Object> results = new HashMap<>();
        results.put("extracted_text", extractedText);
        results.put("entities", entities);
        results.put("sentiment", sentiment);
        results.put("summary", summary);
        results.put("metadata", Map.of(
                "word_count", wordCount,
                "entity_count", entities.size(),
                "processing_time_ms", processingTimeMs,
                "processed_at", endTime.toString(),
                "language", language,
                "worker_id", context.getWorkerId(),
                "trace_id", context.getTraceId()
        ));

        log.infof("✓ Document processing workflow completed (duration: %dms)", processingTimeMs);
        return results;
    }

    // ========== Workflow Steps (simulated for demo) ==========

    /**
     * Extract text from document URL.
     * In production, this would call OCR service, PDF parser, etc.
     */
    private String extractText(String documentUrl) throws Exception {
        // Simulate extraction
        Thread.sleep(500);  // Simulate processing time

        // Demo: Return sample text based on URL
        return """
                CONFIDENTIAL CONTRACT AGREEMENT

                This Agreement is entered into on March 15, 2026, between Acme Corporation
                ("Company") and Global Tech Solutions ("Vendor").

                TERMS:
                1. Services: Vendor shall provide cloud infrastructure services.
                2. Duration: 24 months commencing April 1, 2026.
                3. Payment: $50,000 per month, payable on the first business day.
                4. Termination: Either party may terminate with 90 days written notice.

                CONFIDENTIALITY:
                Both parties agree to maintain strict confidentiality of proprietary information.

                Signed:
                John Smith, CEO, Acme Corporation
                Sarah Johnson, VP Sales, Global Tech Solutions
                """;
    }

    /**
     * Recognize named entities in text.
     * In production, this would call NER service (spaCy, Stanford NER, etc.).
     */
    private List<Map<String, String>> recognizeEntities(String text, String language) throws Exception {
        // Simulate entity recognition
        Thread.sleep(300);

        // Demo: Extract some entities
        List<Map<String, String>> entities = new ArrayList<>();
        entities.add(Map.of("name", "Acme Corporation", "type", "organization"));
        entities.add(Map.of("name", "Global Tech Solutions", "type", "organization"));
        entities.add(Map.of("name", "John Smith", "type", "person"));
        entities.add(Map.of("name", "Sarah Johnson", "type", "person"));
        entities.add(Map.of("name", "March 15, 2026", "type", "date"));
        entities.add(Map.of("name", "April 1, 2026", "type", "date"));
        entities.add(Map.of("name", "$50,000", "type", "money"));

        return entities;
    }

    /**
     * Analyze sentiment of text.
     * In production, this would call sentiment analysis service.
     */
    private Map<String, Object> analyzeSentiment(String text, String language) throws Exception {
        // Simulate sentiment analysis
        Thread.sleep(200);

        // Demo: Return neutral sentiment for contracts
        Map<String, Object> sentiment = new HashMap<>();
        sentiment.put("overall", "neutral");
        sentiment.put("score", 0.5);  // -1.0 (negative) to 1.0 (positive)
        sentiment.put("confidence", 0.85);
        sentiment.put("breakdown", Map.of(
                "positive_phrases", 2,
                "negative_phrases", 1,
                "neutral_phrases", 15
        ));

        return sentiment;
    }

    /**
     * Generate executive summary.
     * In production, this would call summarization service (LLM, extractive summarizer, etc.).
     */
    private String generateSummary(String text, List<Map<String, String>> entities,
                                   Map<String, Object> sentiment) throws Exception {
        // Simulate summary generation
        Thread.sleep(400);

        // Demo: Generate summary based on entities
        return String.format(
                "CONTRACT SUMMARY: Agreement between %s and %s for cloud infrastructure services. " +
                "Duration: 24 months starting %s. Payment: %s monthly. " +
                "Signed by %s and %s. Document sentiment: %s.",
                entities.get(0).get("name"),
                entities.get(1).get("name"),
                entities.get(5).get("name"),
                entities.get(6).get("name"),
                entities.get(2).get("name"),
                entities.get(3).get("name"),
                sentiment.get("overall")
        );
    }

    /**
     * Count words in text (simple whitespace split).
     */
    private int countWords(String text) {
        return text.trim().split("\\s+").length;
    }
}
