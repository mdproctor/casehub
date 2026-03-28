# Quarkus Flow / Serverless Workflow Integration Guide

This guide shows how to integrate CaseHub with real workflow engines, specifically **Kogito Serverless Workflow** (the Quarkus workflow solution).

---

## Option 1: Kogito Serverless Workflow (Recommended)

Kogito Serverless Workflow implements the **CNCF Serverless Workflow specification** and is the official Quarkus workflow engine.

### Step 1: Add Dependencies

Update `casehub-flow-worker/pom.xml`:

```xml
<dependencies>
    <!-- Kogito Serverless Workflow -->
    <dependency>
        <groupId>org.kie.kogito</groupId>
        <artifactId>kogito-quarkus-serverless-workflow</artifactId>
        <version>9.99.0.Final</version>
    </dependency>

    <!-- Jobs addon for timers/delays -->
    <dependency>
        <groupId>org.kie.kogito</groupId>
        <artifactId>kogito-addons-quarkus-jobs</artifactId>
        <version>9.99.0.Final</version>
    </dependency>

    <!-- REST support for workflow functions -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
    </dependency>
</dependencies>
```

### Step 2: Create Workflow Definition

Create `src/main/resources/document-processing.sw.json`:

```json
{
  "id": "document-processing",
  "version": "1.0",
  "name": "Document Processing Workflow",
  "description": "Multi-step document processing pipeline",
  "start": "ExtractText",
  "states": [
    {
      "name": "ExtractText",
      "type": "operation",
      "actions": [
        {
          "name": "extractText",
          "functionRef": {
            "refName": "extractTextFunction",
            "arguments": {
              "documentUrl": "${ .documentUrl }"
            }
          }
        }
      ],
      "transition": "RecognizeEntities"
    },
    {
      "name": "RecognizeEntities",
      "type": "operation",
      "actions": [
        {
          "name": "recognizeEntities",
          "functionRef": {
            "refName": "recognizeEntitiesFunction",
            "arguments": {
              "text": "${ .extractedText }"
            }
          }
        }
      ],
      "transition": "AnalyzeSentiment"
    },
    {
      "name": "AnalyzeSentiment",
      "type": "operation",
      "actions": [
        {
          "name": "analyzeSentiment",
          "functionRef": {
            "refName": "analyzeSentimentFunction",
            "arguments": {
              "text": "${ .extractedText }"
            }
          }
        }
      ],
      "end": true
    }
  ],
  "functions": [
    {
      "name": "extractTextFunction",
      "type": "custom",
      "operation": "service:java:com.example.DocumentFunctions::extractText"
    },
    {
      "name": "recognizeEntitiesFunction",
      "type": "custom",
      "operation": "service:java:com.example.DocumentFunctions::recognizeEntities"
    },
    {
      "name": "analyzeSentimentFunction",
      "type": "custom",
      "operation": "service:java:com.example.DocumentFunctions::analyzeSentiment"
    }
  ]
}
```

### Step 3: Implement Workflow Functions

Create `src/main/java/com/example/DocumentFunctions.java`:

```java
package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.*;

@ApplicationScoped
public class DocumentFunctions {

    private static final Logger log = Logger.getLogger(DocumentFunctions.class);

    /**
     * Extract text from document URL.
     * Called by workflow state "ExtractText".
     */
    public Map<String, Object> extractText(Map<String, Object> input) {
        String documentUrl = (String) input.get("documentUrl");
        log.infof("Extracting text from: %s", documentUrl);

        // Call OCR service, PDF parser, etc.
        String extractedText = performOCR(documentUrl);

        return Map.of(
            "extractedText", extractedText,
            "wordCount", countWords(extractedText)
        );
    }

    /**
     * Recognize named entities in text.
     * Called by workflow state "RecognizeEntities".
     */
    public Map<String, Object> recognizeEntities(Map<String, Object> input) {
        String text = (String) input.get("text");
        log.infof("Recognizing entities in %d chars", text.length());

        // Call NER service (spaCy, Stanford NER, etc.)
        List<Map<String, String>> entities = performNER(text);

        return Map.of("entities", entities);
    }

    /**
     * Analyze sentiment of text.
     * Called by workflow state "AnalyzeSentiment".
     */
    public Map<String, Object> analyzeSentiment(Map<String, Object> input) {
        String text = (String) input.get("text");
        log.infof("Analyzing sentiment of %d chars", text.length());

        // Call sentiment analysis service
        Map<String, Object> sentiment = performSentimentAnalysis(text);

        return Map.of("sentiment", sentiment);
    }

    // Implementation methods...
    private String performOCR(String documentUrl) {
        // Implement actual OCR logic
        return "Sample extracted text...";
    }

    private List<Map<String, String>> performNER(String text) {
        // Implement actual NER logic
        return List.of(
            Map.of("name", "Acme Corp", "type", "organization"),
            Map.of("name", "John Smith", "type", "person")
        );
    }

    private Map<String, Object> performSentimentAnalysis(String text) {
        // Implement actual sentiment analysis
        return Map.of("overall", "neutral", "score", 0.5);
    }

    private int countWords(String text) {
        return text.split("\\s+").length;
    }
}
```

### Step 4: Create FlowWorkflowDefinition Bridge

Create `src/main/java/io/casehub/flow/kogito/KogitoWorkflowBridge.java`:

```java
package io.casehub.flow.kogito;

import io.casehub.flow.FlowExecutionContext;
import io.casehub.flow.FlowWorkflowDefinition;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;

import java.util.Map;
import java.util.Set;

/**
 * Bridge between CaseHub FlowWorker and Kogito Serverless Workflow.
 */
public class KogitoWorkflowBridge implements FlowWorkflowDefinition {

    private static final Logger log = Logger.getLogger(KogitoWorkflowBridge.class);

    @Inject
    Process<DocumentProcessingModel> documentProcessingWorkflow;

    @Override
    public String getWorkflowId() {
        return "document-processing";
    }

    @Override
    public Set<String> getRequiredCapabilities() {
        return Set.of("flow", "serverless-workflow", "kogito");
    }

    @Override
    public Map<String, Object> execute(FlowExecutionContext context) throws Exception {
        log.infof("Starting Kogito workflow (trace: %s)", context.getTraceId());

        // Extract input from CaseHub task context
        String documentUrl = context.getInput("documentUrl", String.class)
                .orElseThrow(() -> new IllegalArgumentException("Missing documentUrl"));

        // Create workflow model
        DocumentProcessingModel model = new DocumentProcessingModel();
        model.setDocumentUrl(documentUrl);

        // Start workflow
        ProcessInstance<DocumentProcessingModel> instance =
            documentProcessingWorkflow.createInstance(model);
        instance.start();

        // Wait for completion
        instance.waitForCompletion();

        // Extract results
        DocumentProcessingModel result = instance.variables();

        return Map.of(
            "extractedText", result.getExtractedText(),
            "entities", result.getEntities(),
            "sentiment", result.getSentiment(),
            "workflowInstanceId", instance.id()
        );
    }
}

/**
 * Model class for workflow variables.
 */
class DocumentProcessingModel {
    private String documentUrl;
    private String extractedText;
    private List<Map<String, String>> entities;
    private Map<String, Object> sentiment;

    // Getters and setters...
    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
    public List<Map<String, String>> getEntities() { return entities; }
    public void setEntities(List<Map<String, String>> entities) { this.entities = entities; }
    public Map<String, Object> getSentiment() { return sentiment; }
    public void setSentiment(Map<String, Object> sentiment) { this.sentiment = sentiment; }
}
```

### Step 5: Register and Use

```java
@ApplicationScoped
public class WorkflowSetup {

    @Inject
    FlowWorkflowRegistry workflowRegistry;

    @Inject
    KogitoWorkflowBridge kogitoWorkflowBridge;

    void onStart(@Observes StartupEvent event) {
        // Register Kogito workflow bridge
        workflowRegistry.register(kogitoWorkflowBridge);
    }
}
```

### Step 6: Submit Tasks

```java
@Inject
TaskBroker taskBroker;

TaskRequest request = TaskRequest.builder()
    .taskType("document-processing")  // Matches workflow ID
    .context(Map.of("documentUrl", "https://example.com/doc.pdf"))
    .requiredCapabilities(Set.of("flow"))
    .build();

TaskResult result = taskBroker.submitTask(request)
                              .awaitResult(Duration.ofMinutes(2));

Map<String, Object> workflowOutput = result.getData();
String extractedText = (String) workflowOutput.get("extractedText");
```

---

## Option 2: Quarkiverse Quarkus Flow

If using the Quarkiverse Quarkus Flow extension:

### Step 1: Add Repository

```xml
<repositories>
    <repository>
        <id>quarkiverse</id>
        <url>https://repo.quarkiverse.io/releases/</url>
    </repository>
</repositories>
```

### Step 2: Add Dependency

```xml
<dependency>
    <groupId>io.quarkiverse.quarkus-flow</groupId>
    <artifactId>quarkus-flow</artifactId>
    <version>${quarkus-flow.version}</version>
</dependency>
```

Check https://github.com/quarkiverse/quarkus-flow for latest version.

### Step 3: Follow Similar Pattern

Use the same `FlowWorkflowDefinition` pattern to bridge Quarkus Flow with CaseHub tasks.

---

## Running the Example

### 1. Build

```bash
cd casehub-flow-worker
mvn clean package
```

### 2. Run in Dev Mode

```bash
mvn quarkus:dev
```

### 3. Submit Workflow Task

```bash
curl -X POST http://localhost:8080/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "taskType": "document-processing",
    "context": {
      "documentUrl": "https://example.com/document.pdf"
    },
    "requiredCapabilities": ["flow"]
  }'
```

### 4. Check Results

The workflow will execute through states defined in `.sw.json`, calling your Java functions, and return the final result.

---

## Workflow Features

### Sequential States

```json
{
  "states": [
    { "name": "Step1", "transition": "Step2" },
    { "name": "Step2", "transition": "Step3" },
    { "name": "Step3", "end": true }
  ]
}
```

### Parallel States

```json
{
  "name": "ParallelProcessing",
  "type": "parallel",
  "branches": [
    { "name": "ProcessA", "actions": [...] },
    { "name": "ProcessB", "actions": [...] }
  ],
  "completionType": "allOf",
  "transition": "NextState"
}
```

### Conditional Branching

```json
{
  "name": "CheckCondition",
  "type": "switch",
  "dataConditions": [
    {
      "condition": "${ .score > 0.8 }",
      "transition": "HighScore"
    },
    {
      "condition": "${ .score > 0.5 }",
      "transition": "MediumScore"
    }
  ],
  "defaultCondition": {
    "transition": "LowScore"
  }
}
```

### Event-Driven

```json
{
  "name": "WaitForApproval",
  "type": "event",
  "onEvents": [
    {
      "eventRefs": ["ApprovalEvent"],
      "actions": [...]
    }
  ]
}
```

---

## Best Practices

### 1. **Keep Functions Focused**

Each workflow function should do one thing:
```java
// Good
public Map<String, Object> extractText(Map<String, Object> input) {
    return Map.of("text", performOCR(...));
}

// Avoid
public Map<String, Object> processEverything(Map<String, Object> input) {
    // Too much in one function
}
```

### 2. **Use PropagationContext**

Pass trace IDs through workflow:
```java
public Map<String, Object> execute(FlowExecutionContext context) {
    model.setTraceId(context.getTraceId());
    model.setSpanId(context.getSpanId());
    // Workflow functions can access these for logging
}
```

### 3. **Handle Errors**

Define error handling in workflow:
```json
{
  "name": "RiskyOperation",
  "type": "operation",
  "onErrors": [
    {
      "errorRef": "ValidationError",
      "transition": "HandleValidationError"
    }
  ]
}
```

### 4. **Monitor Workflows**

Kogito provides metrics and tracing:
```properties
# application.properties
quarkus.kogito.devservices.enabled=true
kogito.service.url=http://localhost:8080
```

---

## Resources

- **Kogito Documentation**: https://kogito.kie.org/
- **Serverless Workflow Spec**: https://serverlessworkflow.io/
- **Quarkus Flow**: https://github.com/quarkiverse/quarkus-flow
- **CaseHub Flow Worker**: See `README.md` in this module

---

## Example Workflows

See `src/main/resources/workflows/` for example workflow definitions:

- `document-processing.sw.json` - Sequential processing
- `parallel-analysis.sw.json` - Parallel branches
- `approval-workflow.sw.json` - Event-driven with human tasks
- `fraud-detection.sw.json` - Conditional branching

---

**Integration complete!** You can now execute CNCF Serverless Workflows as CaseHub tasks with full PropagationContext lineage tracking.
