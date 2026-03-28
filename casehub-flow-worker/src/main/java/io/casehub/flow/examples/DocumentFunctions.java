package io.casehub.flow.examples;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Functions for document processing workflow.
 *
 * <p>These functions are referenced by the Serverless Workflow definition
 * in {@code src/main/resources/workflows/document-processing.sw.json}.
 *
 * <p>Each function is called by the workflow engine with input data and
 * returns results that flow to the next state.
 */
@ApplicationScoped
public class DocumentFunctions {

    private static final Logger log = Logger.getLogger(DocumentFunctions.class);

    /**
     * Extract text from document URL.
     */
    public Map<String, Object> extractText(Map<String, Object> input) {
        String documentUrl = (String) input.get("documentUrl");
        log.infof("  [1/4] Extracting text from: %s", documentUrl);

        try {
            Thread.sleep(300);  // Simulate OCR processing
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate text extraction
        String extractedText = """
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

        int wordCount = extractedText.split("\\s+").length;
        log.infof("    ✓ Extracted %d words", wordCount);

        Map<String, Object> result = new HashMap<>();
        result.put("extractedText", extractedText);
        result.put("wordCount", wordCount);
        return result;
    }

    /**
     * Recognize named entities in text.
     */
    public Map<String, Object> recognizeEntities(Map<String, Object> input) {
        String text = (String) input.getOrDefault("text", input.get("extractedText"));
        log.infof("  [2/4] Recognizing entities in %d chars", text.length());

        try {
            Thread.sleep(200);  // Simulate NER processing
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate entity recognition
        List<Map<String, String>> entities = List.of(
                Map.of("name", "Acme Corporation", "type", "organization", "position", "47"),
                Map.of("name", "Global Tech Solutions", "type", "organization", "position", "102"),
                Map.of("name", "John Smith", "type", "person", "position", "412"),
                Map.of("name", "Sarah Johnson", "type", "person", "position", "451"),
                Map.of("name", "March 15, 2026", "type", "date", "position", "68"),
                Map.of("name", "April 1, 2026", "type", "date", "position", "223"),
                Map.of("name", "$50,000", "type", "money", "position", "265")
        );

        log.infof("    ✓ Found %d entities", entities.size());

        // Pass through previous context and add new data
        Map<String, Object> result = new HashMap<>(input);
        result.put("entities", entities);
        return result;
    }

    /**
     * Analyze sentiment of text.
     */
    public Map<String, Object> analyzeSentiment(Map<String, Object> input) {
        String text = (String) input.getOrDefault("text", input.get("extractedText"));
        log.infof("  [3/4] Analyzing sentiment of %d chars", text.length());

        try {
            Thread.sleep(200);  // Simulate sentiment analysis
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate sentiment analysis
        Map<String, Object> sentiment = Map.of(
                "overall", "neutral",
                "score", 0.5,  // -1.0 (negative) to 1.0 (positive)
                "confidence", 0.87,
                "breakdown", Map.of(
                        "positive_phrases", 3,
                        "negative_phrases", 1,
                        "neutral_phrases", 18
                )
        );

        log.infof("    ✓ Sentiment: %s (score: %.2f)", sentiment.get("overall"), sentiment.get("score"));

        // Pass through previous context and add new data
        Map<String, Object> result = new HashMap<>(input);
        result.put("sentiment", sentiment);
        return result;
    }

    /**
     * Generate summary from extracted information.
     */
    public Map<String, Object> generateSummary(Map<String, Object> input) {
        log.info("  [4/4] Generating summary...");

        try {
            Thread.sleep(300);  // Simulate summarization
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> entities = (List<Map<String, String>>) input.get("entities");
        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) input.get("sentiment");

        // Generate summary from extracted information
        String summary = String.format(
                "CONTRACT SUMMARY: Agreement between %s and %s for cloud infrastructure services. " +
                "Duration: 24 months starting %s. Payment: %s monthly. " +
                "Signed by %s and %s. Document sentiment: %s (confidence: %.2f).",
                entities.get(0).get("name"),
                entities.get(1).get("name"),
                entities.get(5).get("name"),
                entities.get(6).get("name"),
                entities.get(2).get("name"),
                entities.get(3).get("name"),
                sentiment.get("overall"),
                sentiment.get("confidence")
        );

        log.infof("    ✓ Summary generated (%d chars)", summary.length());

        // Pass through previous context and add new data
        Map<String, Object> result = new HashMap<>(input);
        result.put("summary", summary);
        return result;
    }
}
