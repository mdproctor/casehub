package io.casehub.examples.workers;

import io.casehub.error.ErrorInfo;
import io.casehub.worker.*;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Worker implementation that provides LLM-based reasoning capabilities using the Claude API.
 *
 * Capabilities: ["llm", "reasoning", "text-generation", "summarization", "analysis"]
 *
 * This worker demonstrates:
 * 1. Worker registration with WorkerRegistry
 * 2. Task claiming loop
 * 3. External API integration (Claude API)
 * 4. Heartbeat mechanism
 * 5. Result submission
 * 6. Error handling and failure reporting
 *
 * Typical usage from a TaskDefinition:
 * <pre>
 * TaskRequest request = TaskRequest.builder()
 *     .taskType("llm-analysis")
 *     .context(Map.of(
 *         "prompt", "Analyze this contract for legal risks...",
 *         "text", contractText,
 *         "max_tokens", 2000
 *     ))
 *     .requiredCapabilities(Set.of("llm", "reasoning"))
 *     .build();
 *
 * TaskHandle handle = taskBroker.submitTask(request);
 * TaskResult result = handle.awaitResult(Duration.ofMinutes(2));
 * String analysis = (String) result.getData().get("response");
 * </pre>
 *
 * Environment variables required:
 * - ANTHROPIC_API_KEY: Your Claude API key
 */
public class LlmReasoningWorker implements Runnable {

    private static final Logger LOG = Logger.getLogger(LlmReasoningWorker.class);

    private final WorkerRegistry workerRegistry;
    private final String workerId;
    private final String apiKey;
    private final HttpClient httpClient;
    private final AtomicBoolean running = new AtomicBoolean(true);

    // Configuration
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";

    /**
     * Creates and registers an LLM reasoning worker.
     *
     * @param workerRegistry The registry to register with
     * @param workerName Human-readable worker name
     * @param apiKey Worker API key for authentication
     * @throws Exception if registration fails
     */
    public LlmReasoningWorker(WorkerRegistry workerRegistry, String workerName, String apiKey)
            throws Exception {
        this.workerRegistry = workerRegistry;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Register with WorkerRegistry
        Set<String> capabilities = Set.of(
            "llm",
            "reasoning",
            "text-generation",
            "summarization",
            "analysis",
            "question-answering"
        );

        this.workerId = workerRegistry.register(workerName, capabilities, apiKey);
        LOG.infof("LlmReasoningWorker registered: id=%s, name=%s", workerId, workerName);
    }

    /**
     * Main worker loop: claim tasks, process them, submit results, send heartbeats.
     */
    @Override
    public void run() {
        LOG.infof("LlmReasoningWorker %s starting execution loop", workerId);

        long lastHeartbeat = System.currentTimeMillis();

        while (running.get()) {
            try {
                // Send heartbeat if needed
                if (System.currentTimeMillis() - lastHeartbeat > HEARTBEAT_INTERVAL.toMillis()) {
                    workerRegistry.heartbeat(workerId);
                    lastHeartbeat = System.currentTimeMillis();
                    LOG.debugf("Heartbeat sent for worker %s", workerId);
                }

                // Try to claim a task
                Optional<Task> taskOpt = workerRegistry.claimTask(workerId);

                if (taskOpt.isPresent()) {
                    Task task = taskOpt.get();
                    LOG.infof("Claimed task: %s (type: %s)", task.getTaskId(), task.getTaskType());

                    processTask(task);

                } else {
                    // No tasks available, wait before polling again
                    Thread.sleep(POLL_INTERVAL.toMillis());
                }

            } catch (InterruptedException e) {
                LOG.info("Worker interrupted, shutting down");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.errorf(e, "Unexpected error in worker loop: %s", e.getMessage());
                // Continue running despite errors
            }
        }

        // Unregister on shutdown
        try {
            workerRegistry.unregister(workerId);
            LOG.infof("LlmReasoningWorker %s unregistered", workerId);
        } catch (Exception e) {
            LOG.errorf(e, "Error during unregister: %s", e.getMessage());
        }
    }

    /**
     * Process a claimed task by calling the Claude API and submitting results.
     */
    private void processTask(Task task) {
        String taskId = task.getTaskId();

        try {
            // Extract task context
            Map<String, Object> context = task.getContext();
            String prompt = (String) context.get("prompt");
            String text = (String) context.getOrDefault("text", "");
            Integer maxTokens = (Integer) context.getOrDefault("max_tokens", 4096);
            String model = (String) context.getOrDefault("model", DEFAULT_MODEL);

            if (prompt == null || prompt.isEmpty()) {
                throw new IllegalArgumentException("Task context must include 'prompt'");
            }

            LOG.infof("Processing LLM task %s with model %s", taskId, model);

            // Build the actual prompt
            String fullPrompt = buildPrompt(prompt, text);

            // Call Claude API
            String response = callClaudeAPI(fullPrompt, maxTokens, model);

            // Submit successful result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("response", response);
            resultData.put("model", model);
            resultData.put("tokens_used", estimateTokens(fullPrompt, response));

            TaskResult result = TaskResult.success(taskId, resultData);
            workerRegistry.submitResult(workerId, taskId, result);

            LOG.infof("Task %s completed successfully", taskId);

        } catch (Exception e) {
            LOG.errorf(e, "Task %s failed: %s", taskId, e.getMessage());

            // Report failure
            ErrorInfo error = new ErrorInfo(
                "LLM_PROCESSING_FAILED",
                e.getMessage(),
                isRetryable(e)
            );
            error.setWorkerId(Optional.of(workerId));
            error.setStackTrace(Optional.of(getStackTrace(e)));

            try {
                workerRegistry.reportFailure(workerId, taskId, error);
            } catch (Exception reportError) {
                LOG.errorf(reportError, "Failed to report task failure: %s", reportError.getMessage());
            }
        }
    }

    /**
     * Build the full prompt from components.
     */
    private String buildPrompt(String prompt, String text) {
        if (text == null || text.isEmpty()) {
            return prompt;
        }
        return prompt + "\n\nText to analyze:\n" + text;
    }

    /**
     * Call the Claude API with the given prompt.
     *
     * @param prompt The prompt to send
     * @param maxTokens Maximum tokens in response
     * @param model The Claude model to use
     * @return The LLM response text
     * @throws IOException if API call fails
     * @throws InterruptedException if interrupted
     */
    private String callClaudeAPI(String prompt, int maxTokens, String model)
            throws IOException, InterruptedException {

        String claudeApiKey = System.getenv("ANTHROPIC_API_KEY");
        if (claudeApiKey == null || claudeApiKey.isEmpty()) {
            throw new IllegalStateException(
                "ANTHROPIC_API_KEY environment variable not set. " +
                "Get your API key from https://console.anthropic.com/"
            );
        }

        // Build request body
        String requestBody = String.format("""
            {
                "model": "%s",
                "max_tokens": %d,
                "messages": [
                    {
                        "role": "user",
                        "content": %s
                    }
                ]
            }
            """,
            model,
            maxTokens,
            escapeJson(prompt)
        );

        // Make HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CLAUDE_API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", claudeApiKey)
                .header("anthropic-version", "2023-06-01")
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        LOG.debugf("Calling Claude API: model=%s, max_tokens=%d", model, maxTokens);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException(
                String.format("Claude API error: status=%d, body=%s",
                    response.statusCode(), response.body())
            );
        }

        // Parse response (simple extraction, would use JSON library in production)
        return extractResponseText(response.body());
    }

    /**
     * Extract the text content from Claude API JSON response.
     * In production, use a proper JSON library (Jackson, Gson, etc.)
     */
    private String extractResponseText(String jsonResponse) {
        // Simple extraction for demo purposes
        // Production code should use Jackson or Gson

        int contentStart = jsonResponse.indexOf("\"text\":");
        if (contentStart == -1) {
            throw new RuntimeException("Invalid Claude API response: no 'text' field found");
        }

        contentStart = jsonResponse.indexOf("\"", contentStart + 7) + 1;
        int contentEnd = jsonResponse.indexOf("\"", contentStart);

        if (contentEnd == -1) {
            throw new RuntimeException("Invalid Claude API response: malformed 'text' field");
        }

        return jsonResponse.substring(contentStart, contentEnd)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    /**
     * Escape string for JSON (simple version, use JSON library in production).
     */
    private String escapeJson(String text) {
        return "\"" + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    /**
     * Estimate token count (rough approximation).
     */
    private int estimateTokens(String prompt, String response) {
        // Rough estimate: ~4 characters per token
        return (prompt.length() + response.length()) / 4;
    }

    /**
     * Determine if an exception is retryable.
     */
    private boolean isRetryable(Exception e) {
        // Network errors, timeouts, and rate limits are retryable
        if (e instanceof IOException) {
            return true;
        }
        if (e instanceof InterruptedException) {
            return false;
        }
        // API errors with 429 (rate limit) or 5xx are retryable
        String message = e.getMessage();
        if (message != null) {
            return message.contains("429") ||
                   message.contains("500") ||
                   message.contains("503");
        }
        return false;
    }

    /**
     * Get stack trace as string.
     */
    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("  at ").append(element.toString()).append("\n");
            if (sb.length() > 2000) {
                sb.append("  ... (truncated)");
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Request shutdown of the worker.
     */
    public void shutdown() {
        LOG.infof("Shutdown requested for worker %s", workerId);
        running.set(false);
    }

    /**
     * Get the worker ID.
     */
    public String getWorkerId() {
        return workerId;
    }

    /**
     * Check if worker is running.
     */
    public boolean isRunning() {
        return running.get();
    }
}
