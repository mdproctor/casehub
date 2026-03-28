## LlmReasoningWorker - Claude API Integration for CaseHub

A production-ready Worker implementation that integrates Claude API into CaseHub's dual execution model.

---

## Overview

**LlmReasoningWorker** demonstrates how to:
- Implement the Worker pattern in CaseHub
- Integrate external APIs (Claude API) for heavy processing
- Bridge CaseFile model and Task model seamlessly
- Handle async work delegation from TaskDefinitions
- Manage worker lifecycle (registration, heartbeat, shutdown)

---

## Files

| File | Purpose |
|------|---------|
| **LlmReasoningWorker.java** | Worker implementation with Claude API integration |
| **LlmAnalysisTaskDefinition.java** | TaskDefinition that delegates to the Worker |
| **DocumentAnalysisWithLlmApp.java** | Complete example showing dual execution model |
| **README.md** | This documentation |

---

## Quick Start

### Prerequisites

1. **Claude API Key**: Get from https://console.anthropic.com/
2. **Set environment variable**:
   ```bash
   export ANTHROPIC_API_KEY=sk-ant-your-key-here
   ```

### Run the Example

```bash
cd casehub
export ANTHROPIC_API_KEY=sk-ant-...
mvn quarkus:dev
```

**What happens:**
1. LlmReasoningWorker starts and registers with WorkerRegistry
2. TaskDefinitions process document (OCR → NER → Risk)
3. LlmAnalysisTaskDefinition delegates complex reasoning to Worker
4. Worker calls Claude API for strategic analysis
5. Worker returns insights
6. TaskDefinition contributes insights to CaseFile
7. Final summary incorporates AI recommendations

---

## Architecture

### The Dual Execution Model

```
┌─────────────────────────────────────────────────────┐
│                   CASEFILE MODEL                    │
│              (Collaborative Problem-Solving)         │
│                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐ │
│  │ OCR Task │→ │ NER Task │→ │ Risk Task        │ │
│  └──────────┘  └──────────┘  └─────────┬────────┘ │
│                                          │          │
│                    ┌─────────────────────┘          │
│                    ▼                                │
│         ┌──────────────────────┐                   │
│         │ LLM Analysis Task    │                   │
│         │ (reads from CaseFile)│                   │
│         └──────────┬───────────┘                   │
└────────────────────┼────────────────────────────────┘
                     │
                     │ Delegates to Task Model
                     ▼
┌─────────────────────────────────────────────────────┐
│                    TASK MODEL                        │
│               (Request-Response)                     │
│                                                      │
│    ┌──────────────┐         ┌──────────────────┐   │
│    │  TaskBroker  │────────▶│ LlmReasoningWorker│  │
│    │ (submits task)         │ (claims & executes)  │
│    └──────┬───────┘         └────────┬─────────┘   │
│           │                           │             │
│           │◀──────────────────────────┘             │
│           │ TaskResult (LLM insights)               │
│           │                                         │
└───────────┼─────────────────────────────────────────┘
            │
            │ Returns to CaseFile Model
            ▼
┌─────────────────────────────────────────────────────┐
│  LLM Analysis Task contributes insights to CaseFile │
│           ▼                                          │
│  Downstream TaskDefinitions use LLM insights        │
└─────────────────────────────────────────────────────┘
```

### Data Flow

```
CaseFile (extracted_text, entities, risk_assessment)
    │
    ├─> LlmAnalysisTaskDefinition.execute()
    │       │
    │       ├─> Build prompt from CaseFile data
    │       │
    │       ├─> TaskBroker.submitTask(request)
    │       │       │
    │       │       └─> TaskScheduler selects Worker
    │       │               │
    │       │               ├─> LlmReasoningWorker.claimTask()
    │       │               │       │
    │       │               │       ├─> Call Claude API
    │       │               │       │
    │       │               │       └─> WorkerRegistry.submitResult()
    │       │               │
    │       │               └─> TaskResult returned
    │       │
    │       └─> Parse TaskResult, contribute to CaseFile
    │
    └─> CaseFile (now includes llm_insights, strategic_recommendations)
            │
            └─> Downstream TaskDefinitions can use these keys
```

---

## LlmReasoningWorker Implementation

### Core Features

**1. Worker Registration**
```java
LlmReasoningWorker worker = new LlmReasoningWorker(
    workerRegistry,
    "llm-worker-1",
    "api-key-12345"
);

// Registers with capabilities:
// ["llm", "reasoning", "text-generation", "summarization", "analysis"]
```

**2. Task Claiming Loop**
```java
@Override
public void run() {
    while (running.get()) {
        // Send heartbeat
        if (shouldSendHeartbeat()) {
            workerRegistry.heartbeat(workerId);
        }

        // Try to claim task
        Optional<Task> task = workerRegistry.claimTask(workerId);

        if (task.isPresent()) {
            processTask(task.get());
        } else {
            Thread.sleep(POLL_INTERVAL);
        }
    }
}
```

**3. Claude API Integration**
```java
private String callClaudeAPI(String prompt, int maxTokens, String model) {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.anthropic.com/v1/messages"))
        .header("x-api-key", System.getenv("ANTHROPIC_API_KEY"))
        .header("anthropic-version", "2023-06-01")
        .POST(buildRequestBody(prompt, maxTokens, model))
        .build();

    HttpResponse<String> response = httpClient.send(request, ...);
    return extractResponseText(response.body());
}
```

**4. Result Submission**
```java
Map<String, Object> resultData = Map.of(
    "response", llmResponse,
    "model", model,
    "tokens_used", estimatedTokens
);

TaskResult result = TaskResult.success(taskId, resultData);
workerRegistry.submitResult(workerId, taskId, result);
```

**5. Error Handling**
```java
catch (Exception e) {
    ErrorInfo error = new ErrorInfo(
        "LLM_PROCESSING_FAILED",
        e.getMessage(),
        isRetryable(e)  // Network errors, rate limits are retryable
    );
    workerRegistry.reportFailure(workerId, taskId, error);
}
```

---

## Using the Worker from TaskDefinition

### LlmAnalysisTaskDefinition Pattern

```java
public class LlmAnalysisTaskDefinition implements TaskDefinition {

    private final TaskBroker taskBroker;

    @Override
    public Set<String> entryCriteria() {
        // Wait for basic analysis to complete
        return Set.of("extracted_text", "entities", "risk_assessment");
    }

    @Override
    public Set<String> producedKeys() {
        // Produce AI insights
        return Set.of("strategic_recommendations", "llm_insights");
    }

    @Override
    public void execute(CaseFile caseFile) {
        // 1. Read from CaseFile
        Map<String, String> text = caseFile.get("extracted_text", Map.class).get();
        Map<String, Object> entities = caseFile.get("entities", Map.class).get();
        Map<String, Object> risk = caseFile.get("risk_assessment", Map.class).get();

        // 2. Build prompt
        String prompt = buildPrompt(text, entities, risk);

        // 3. Submit to Worker via Task model
        TaskRequest request = TaskRequest.builder()
            .taskType("strategic-analysis")
            .context(Map.of("prompt", prompt, "max_tokens", 3000))
            .requiredCapabilities(Set.of("llm", "reasoning"))
            .propagationContext(caseFile.getPropagationContext())
            .build();

        TaskHandle handle = taskBroker.submitTask(request);

        // 4. Wait for Worker
        TaskResult result = handle.awaitResult(Duration.ofMinutes(2));

        // 5. Contribute Worker's insights back to CaseFile
        if (result.getStatus() == TaskStatus.COMPLETED) {
            String llmResponse = (String) result.getData().get("response");
            Map<String, Object> insights = parseResponse(llmResponse);

            caseFile.put("llm_insights", llmResponse);
            caseFile.put("strategic_recommendations", insights);
        }
    }
}
```

---

## Configuration

### Environment Variables

| Variable | Required | Purpose |
|----------|----------|---------|
| `ANTHROPIC_API_KEY` | **Yes** | Your Claude API key |

### Worker Configuration

Can be customized in `LlmReasoningWorker`:

```java
// Polling and heartbeat
private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

// Claude API
private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";
private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
```

### Task Context Parameters

When submitting tasks to LlmReasoningWorker:

```java
Map<String, Object> context = Map.of(
    "prompt", "Your prompt here",        // Required
    "text", "Additional context",        // Optional
    "max_tokens", 3000,                  // Optional (default: 4096)
    "model", "claude-sonnet-4-20250514"  // Optional (default: sonnet)
);
```

---

## Example Output

```
╔════════════════════════════════════════════════════════════╗
║  CaseHub: Document Analysis with LLM Worker               ║
║  Demonstrating Dual Execution Model                       ║
╚════════════════════════════════════════════════════════════╝

🤖 Starting LlmReasoningWorker...

  ✓ LlmReasoningWorker registered and running
    Worker ID: a1b2c3d4-e5f6-7890-abcd-ef1234567890
    Capabilities: [llm, reasoning, text-generation, analysis]

📋 Registering TaskDefinitions...

  ✓ Text Extraction (raw_documents → extracted_text)
  ✓ Entity Recognition (extracted_text → entities)
  ✓ Risk Assessment (text + entities → risk_assessment)
  ✓ LLM Strategic Analysis (delegates to Worker) ⭐
    Entry: [extracted_text, entities, risk_assessment]
    Produces: [strategic_recommendations, llm_insights]
  ✓ Enhanced Summary (includes LLM insights)

⚙️  Creating case and starting CaseEngine control loop...

  [EXECUTING] Text Extraction...
    ✓ Extracted text from 2 documents
  [EXECUTING] Named Entity Recognition...
    ✓ Found 2 organizations, 3 dates, 1 amounts
  [EXECUTING] Risk Assessment...
    ✓ Risk Level: HIGH (score: 110/100)
  [EXECUTING] LLM Strategic Analysis (delegating to Worker)...
    → Submitting task to LlmReasoningWorker...
    ⏳ Waiting for LLM analysis (timeout: 2 minutes)...
    ✓ LLM analysis complete (tokens: 2847)
    ✓ Contributed strategic recommendations to CaseFile
  [EXECUTING] Enhanced Executive Summary (with LLM insights)...
    ✓ Enhanced summary complete with LLM insights

═══════════════════════════════════════════════════════════
📊 FINAL RESULTS

  📌 RISK ASSESSMENT:
     Risk Level: HIGH (110/100)

  📌 LLM STRATEGIC ANALYSIS:
     ─────────────────────────
     IMMEDIATE ACTIONS:
     • Escalate to senior legal counsel within 48 hours
     • Establish compliance task force for FDA requirements
     • Review insurance coverage adequacy ($5M required)
     • Audit current quality metrics against 99.5% standard

     NEGOTIATION POINTS:
     • Request 90-day extension for FDA compliance deadline
     • Negotiate penalty cap at $250,000 total exposure
     • Add force majeure clause for regulatory changes
     • Clarify indemnification scope and limits

     KEY DEADLINES:
     • 2026-06-01: FDA compliance deadline (CRITICAL)
     • Every 30 days: Payment cycle with late penalties
     • Every month: Minimum order quantity (10,000 units)
     • 2026-12-01: Price protection expires

     RISK MITIGATION:
     • Implement quality control program immediately
     • Secure $5M product liability insurance
     • Create FDA compliance roadmap with milestones
     • Establish escrow account for potential penalties

     RECOMMENDATION:
     CONDITIONAL PROCEED - Contract has significant value but
     requires immediate action on three fronts: (1) FDA compliance
     planning, (2) penalty cap negotiation, (3) quality system
     implementation. Do not execute without these protections.

  📌 EXECUTIVE SUMMARY:
     ─────────────────
     ENHANCED EXECUTIVE SUMMARY
     ══════════════════════════
     (AI-Enhanced Analysis)

     Parties: Acme Corp and Beta Inc
     Risk Level: HIGH (110/100)

     AI-RECOMMENDED IMMEDIATE ACTIONS:
       🔴 Escalate to senior legal counsel within 48 hours
       🔴 Establish compliance task force for FDA requirements
       🔴 Review insurance coverage adequacy ($5M required)
       🔴 Audit current quality metrics against 99.5% standard

═══════════════════════════════════════════════════════════
🎯 ARCHITECTURAL FEATURES DEMONSTRATED:

   ✓ CaseFile Model (Collaborative Problem-Solving)
     5 TaskDefinitions working on shared CaseFile

   ✓ Task Model (Request-Response with Workers)
     LlmReasoningWorker processing complex reasoning

   ✓ Dual Execution Model Integration
     TaskDefinition → Worker → TaskDefinition flow

   ✓ External API Integration
     Claude API called by Worker for LLM reasoning

   ✓ Worker Lifecycle Management
     Registration, heartbeat, task claiming, result submission

   ✓ Data Flow Across Models
     CaseFile → Task → Worker → Task → CaseFile

═══════════════════════════════════════════════════════════
```

---

## Key Concepts Demonstrated

### 1. Worker Pattern

**Registration:**
```java
String workerId = workerRegistry.register(
    "worker-name",
    Set.of("capability1", "capability2"),
    "api-key"
);
```

**Lifecycle:**
- Worker registers with capabilities
- Continuously polls for matching tasks
- Sends heartbeats to indicate liveness
- Claims task → processes → submits result
- Unregisters on shutdown

### 2. Capability-Based Routing

Tasks require specific capabilities:
```java
TaskRequest.builder()
    .requiredCapabilities(Set.of("llm", "reasoning"))
    .build();
```

TaskScheduler selects Workers with matching capabilities.

### 3. Dual Execution Model

**CaseFile Model**: Collaborative, shared workspace
```java
// TaskDefinition contributes to CaseFile
caseFile.put("extracted_text", text);
```

**Task Model**: Request-response, delegated work
```java
// TaskDefinition delegates to Worker
TaskHandle handle = taskBroker.submitTask(request);
TaskResult result = handle.awaitResult(timeout);
```

**Integration**: TaskDefinition bridges both models
```java
// Read from CaseFile
Map data = caseFile.get("key", Map.class).get();

// Delegate to Worker
TaskResult result = taskBroker.submitTask(...).awaitResult(...);

// Contribute Worker's result to CaseFile
caseFile.put("insights", result.getData());
```

### 4. Propagation Context

Context flows across execution models:
```java
TaskRequest.builder()
    .propagationContext(caseFile.getPropagationContext())
    .build();
```

Enables hierarchical tracing: Case → PlanItem → Task → Worker

---

## Production Considerations

### Error Handling

**Retryable Errors:**
- Network failures
- Rate limiting (429)
- Server errors (5xx)

**Non-Retryable:**
- Invalid API key (401)
- Bad request (400)
- Quota exceeded

### Scaling

**Horizontal Scaling:**
```java
// Start multiple workers
for (int i = 0; i < 5; i++) {
    LlmReasoningWorker worker = new LlmReasoningWorker(
        registry,
        "llm-worker-" + i,
        apiKey
    );
    executor.submit(worker);
}
```

Workers with same capabilities → round-robin distribution

**Vertical Scaling:**
- Adjust `max_tokens` per request
- Use faster models (Haiku) for simple tasks
- Use stronger models (Opus) for complex reasoning

### Monitoring

**Key Metrics:**
- Tasks claimed per worker
- Average processing time
- Error rate by error type
- API token usage
- Heartbeat intervals

### Cost Optimization

**Token Management:**
```java
// Estimate before calling
int estimatedTokens = (prompt.length() + maxTokens) / 4;
if (estimatedTokens > budget) {
    // Truncate or reject
}
```

**Model Selection:**
```java
// Use appropriate model for task complexity
if (isSimpleTask) {
    model = "claude-haiku-4";  // Cheaper, faster
} else {
    model = "claude-sonnet-4"; // Balanced
}
```

---

## Extending the Worker

### Add New Capabilities

```java
Set<String> capabilities = Set.of(
    "llm",
    "reasoning",
    "translation",      // NEW
    "code-generation",  // NEW
    "image-analysis"    // NEW
);
```

### Support Multiple LLM Providers

```java
private String callLlm(String prompt, String provider) {
    return switch (provider) {
        case "anthropic" -> callClaudeAPI(prompt);
        case "openai" -> callOpenAI(prompt);
        case "local" -> callLocalModel(prompt);
        default -> throw new IllegalArgumentException("Unknown provider");
    };
}
```

### Add Caching

```java
private final Map<String, String> cache = new ConcurrentHashMap<>();

private String processWithCache(String prompt) {
    String cacheKey = hashPrompt(prompt);
    return cache.computeIfAbsent(cacheKey, k -> callClaudeAPI(prompt));
}
```

---

## Troubleshooting

### Worker Not Claiming Tasks

**Check:**
1. Worker registered? `workerRegistry.getById(workerId)`
2. Capabilities match? Task requires `["llm"]`, worker has `["llm"]`
3. Worker active? Heartbeat sent recently
4. Tasks pending? `taskRegistry.findByStatus(PENDING)`

### API Errors

**401 Unauthorized:**
```bash
export ANTHROPIC_API_KEY=sk-ant-your-actual-key
```

**429 Rate Limited:**
- Implement exponential backoff
- Use multiple API keys
- Reduce request rate

**Timeout:**
- Increase timeout: `Duration.ofMinutes(5)`
- Use streaming API for long responses
- Break into smaller prompts

### Memory Issues

**Large Prompts:**
- Truncate context intelligently
- Summarize before sending to LLM
- Use prompt compression techniques

---

## Next Steps

1. **Run the example**: See it in action
2. **Modify the prompt**: Experiment with different analysis tasks
3. **Add more workers**: Scale horizontally
4. **Integrate other LLMs**: OpenAI, local models
5. **Add caching**: Improve performance and cost
6. **Monitor metrics**: Track usage and performance

---

## Related Documentation

- `/docs/DESIGN.md` - Full architecture
- `/casehub/src/main/java/io/casehub/examples/README.md` - Examples guide
- [Claude API Documentation](https://docs.anthropic.com/)
- [Quarkus HTTP Client](https://quarkus.io/guides/rest-client)

---

**The LlmReasoningWorker demonstrates production-ready integration of AI capabilities into CaseHub's dual execution model!**
