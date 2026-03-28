# CaseHub Flow Worker

Worker module for executing **Quarkus Flow / Serverless Workflow** workflows from CaseHub tasks.

## Overview

The Flow Worker bridges CaseHub's task coordination with workflow engine capabilities, enabling:

- **Workflow-as-Tasks**: Execute complex workflows as CaseHub tasks
- **Multiple Engines**: Support for Kogito Serverless Workflow, Quarkus Flow, or custom engines
- **Dual Execution Model**: Works with both broker-allocated and autonomous patterns
- **Full Integration**: PropagationContext lineage, retry policies, timeout enforcement
- **Isolated Dependencies**: Workflow engine dependencies isolated to this module only

## Architecture

```
CaseHub Task → FlowWorker → FlowWorkflowDefinition → Quarkus Flow
    ↓              ↓               ↓                      ↓
Task context → FlowExecutionContext → Workflow steps → Results
    ↑                                                      ↓
    └──────────── TaskResult ← Result mapping ←──────────┘
```

### Key Components

| Component | Purpose |
|-----------|---------|
| **FlowWorker** | Worker that claims tasks and executes workflows |
| **FlowWorkflowDefinition** | Interface for defining workflows |
| **FlowExecutionContext** | Context passed to workflows (input, metadata, PropagationContext) |
| **FlowWorkflowRegistry** | Registry for workflow definitions |

## Quick Start

### 1. Add Dependency

In your project POM:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-flow-worker</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Define a Workflow

Using Quarkus Flow programmatic API:

```java
@ApplicationScoped
public class MyWorkflow extends Flow implements FlowWorkflowDefinition {

    @Inject
    MyFunctions functions;

    @Override
    public String getWorkflowId() {
        return "my-workflow";
    }

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("my-workflow")
                .tasks(
                        function("step1", functions::processStep1)
                                .exportAs("{ result1: . }"),

                        function("step2",
                                (Map<String, Object> ctx) -> functions.processStep2(
                                        (String) ctx.get("result1")))
                                .exportAs("{ output: . }")
                )
                .build();
    }

    @Override
    public Map<String, Object> execute(FlowExecutionContext context) throws Exception {
        Map<String, Object> input = context.getAllInputs();
        var result = this.startInstance(input).await().indefinitely();
        return result.asMap().orElse(Map.of());
    }
}
```

**Static imports:**
```java
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;
```

### 3. Register and Start Worker

```java
@Inject
FlowWorker flowWorker;

@Inject
FlowWorkflowRegistry workflowRegistry;

// Register workflows
workflowRegistry.register(new MyWorkflow());
workflowRegistry.register(new AnotherWorkflow());

// Start worker
new Thread(flowWorker).start();
```

### 4. Submit Tasks

```java
@Inject
TaskBroker taskBroker;

TaskRequest request = TaskRequest.builder()
    .taskType("my-workflow")  // Matches workflow ID
    .context(Map.of("param1", "value"))
    .requiredCapabilities(Set.of("flow"))
    .build();

TaskHandle handle = taskBroker.submitTask(request);
TaskResult result = handle.awaitResult(Duration.ofMinutes(2));
```

## Example Workflow

See [`QuarkusFlowDocumentWorkflow.java`](src/main/java/io/casehub/flow/examples/QuarkusFlowDocumentWorkflow.java) for a complete example using the **programmatic API**:

- **Multi-step processing**: Extract → Entities → Sentiment → Summary
- **Programmatic definition**: Using FuncWorkflowBuilder fluent API
- **Type-safe functions**: CDI-injected DocumentFunctions bean
- **Data transformation**: Using `.exportAs()` for context updates
- **Error handling**: Proper exception management
- **Result aggregation**: Structured output with metadata
- **PropagationContext**: Trace ID and lineage tracking

See [`PROGRAMMATIC_API.md`](PROGRAMMATIC_API.md) for detailed documentation of the programmatic workflow definition approach.

## Running the Demo

### Compile

```bash
cd casehub-flow-worker
mvn compile
```

### Run Demo Application

```bash
mvn exec:java -Dexec.mainClass="io.casehub.flow.examples.FlowWorkerDemo"
```

**Expected Output:**

```
╔════════════════════════════════════════════════════════════╗
║  CaseHub: Flow Worker Demo                                ║
║  Quarkus Flow Integration                                 ║
╚════════════════════════════════════════════════════════════╝

🔧 Setting up infrastructure...

📋 Registering Flow workflows...
✓ Registered Flow workflow: document-processing-flow
  ✓ Registered 1 workflow(s)

🤖 Starting FlowWorker...
✓ FlowWorker registered: flow-worker-xxx

📤 Submitting document processing task...
  ✓ Task submitted: xxx
  ⏳ Waiting for workflow execution...
  📢 Task xxx: PENDING → ASSIGNED
  🔄 Starting document processing workflow
  [1/4] Extracting text...
    ✓ Extracted 123 words
  [2/4] Recognizing entities...
    ✓ Found 7 entities
  [3/4] Analyzing sentiment...
    ✓ Sentiment: neutral
  [4/4] Generating summary...
    ✓ Summary generated

╔════════════════════════════════════════════════════════════╗
║  WORKFLOW RESULTS                                          ║
╚════════════════════════════════════════════════════════════╝

📄 EXTRACTED TEXT:
CONFIDENTIAL CONTRACT AGREEMENT...

🏷️  ENTITIES:
  • Acme Corporation (organization)
  • Global Tech Solutions (organization)
  • John Smith (person)
  ...

😊 SENTIMENT:
  Overall: neutral (score: 0.50)

📋 SUMMARY:
  CONTRACT SUMMARY: Agreement between Acme Corporation and...

ℹ️  METADATA:
  Processing time: 1420ms
  Trace ID: xxx
```

## Integration with CaseFile Model

Workflows can be used from TaskDefinitions:

```java
public class DocumentAnalysisTaskDefinition implements TaskDefinition {
    @Inject
    TaskBroker taskBroker;

    @Override
    public void execute(CaseFile caseFile) {
        // Read from CaseFile
        String docUrl = caseFile.get("document_url", String.class).get();

        // Delegate to Flow workflow
        TaskRequest request = TaskRequest.builder()
            .taskType("document-processing-flow")
            .context(Map.of("document_url", docUrl))
            .requiredCapabilities(Set.of("flow"))
            .build();

        TaskResult result = taskBroker.submitTask(request)
                                      .awaitResult(Duration.ofMinutes(2));

        // Contribute results to CaseFile
        caseFile.put("processed_document", result.getData());
    }
}
```

## Configuration

In `application.properties`:

```properties
# Worker configuration
casehub.flow.worker.name=flow-worker-1
casehub.flow.worker.poll-interval=5000
casehub.flow.worker.heartbeat-interval=20000

# Workflow timeouts
casehub.flow.workflow.default-timeout=300000
```

## Advanced Features

### PropagationContext Access

Workflows have full access to PropagationContext:

```java
@Override
public Map<String, Object> execute(FlowExecutionContext context) {
    // Access trace ID for logging
    String traceId = context.getTraceId();
    log.infof("Processing in trace: %s", traceId);

    // Check execution depth
    int depth = context.getDepth();
    if (depth > 5) {
        throw new RuntimeException("Max workflow nesting exceeded");
    }

    // Check budget/deadline
    if (context.isBudgetExhausted()) {
        throw new RuntimeException("Execution budget exhausted");
    }

    // Create child context for sub-workflow
    PropagationContext childContext = context.createChildContext(
        Map.of("sub_workflow", "true")
    );

    // Execute workflow...
}
```

### Error Handling

```java
@Override
public Map<String, Object> execute(FlowExecutionContext context) throws Exception {
    try {
        // Workflow steps...
        return results;
    } catch (ValidationException e) {
        // Non-retryable error
        throw new RuntimeException("Validation failed: " + e.getMessage(), e);
    } catch (TransientException e) {
        // Retryable error - will trigger retry policy
        throw e;
    }
}
```

### Multiple Workers

Scale horizontally by running multiple FlowWorker instances:

```java
// Worker 1
FlowWorker worker1 = new FlowWorker(registry, workflows, "flow-worker-1");
new Thread(worker1).start();

// Worker 2
FlowWorker worker2 = new FlowWorker(registry, workflows, "flow-worker-2");
new Thread(worker2).start();

// Tasks distributed by TaskScheduler based on availability
```

## Use Cases

### ✅ Excellent Fit

- **Multi-step data processing**: ETL pipelines, data transformation
- **Document workflows**: OCR → NER → Classification → Storage
- **Business process automation**: Approval workflows, order processing
- **Integration workflows**: Orchestrate multiple service calls
- **State machine workflows**: Complex decision trees

### ⚠️ Less Ideal

- Simple single-step tasks (use TaskDefinition directly)
- Real-time event streaming (different architecture)
- CPU-intensive compute (consider dedicated compute workers)

## API Reference

### FlowWorkflowDefinition

```java
public interface FlowWorkflowDefinition {
    String getWorkflowId();                           // Required
    Map<String, Object> execute(FlowExecutionContext); // Required
    Set<String> getRequiredCapabilities();            // Optional
    String getDescription();                          // Optional
    long getEstimatedDurationMs();                    // Optional
}
```

### FlowExecutionContext

```java
// Input access
Optional<Object> getInput(String key)
<T> Optional<T> getInput(String key, Class<T> type)
Map<String, Object> getAllInputs()

// Task metadata
String getTaskId()
String getTaskType()
String getWorkerId()
Optional<String> getCaseFileId()

// PropagationContext
String getTraceId()
String getSpanId()
int getDepth()
boolean isBudgetExhausted()
PropagationContext createChildContext(Map<String, String>)
```

### FlowWorker

```java
// Constructor
FlowWorker(WorkerRegistry, FlowWorkflowRegistry)
FlowWorker(WorkerRegistry, FlowWorkflowRegistry, String workerName)

// Lifecycle
void run()           // Start worker loop
void shutdown()      // Graceful shutdown
boolean isRunning()
```

## Workflow Engine Integration

This module uses **Quarkus Flow** with its programmatic API for workflow definitions.

### Quarkus Flow (Programmatic API)

**Current Implementation:**

```xml
<dependency>
    <groupId>io.quarkiverse.flow</groupId>
    <artifactId>quarkus-flow</artifactId>
    <version>0.7.1</version>
</dependency>
```

**Features:**
- **Programmatic**: Define workflows using fluent Java DSL
- **Type-safe**: Compile-time checking of workflow definitions
- **Refactorable**: IDE support for renaming, navigation, autocomplete
- **Debuggable**: Set breakpoints in workflow definitions
- **Dynamic**: Construct workflows programmatically at runtime
- **CNCF-aligned**: Maps to Serverless Workflow concepts

**Example:**
```java
@Override
public Workflow descriptor() {
    return FuncWorkflowBuilder.workflow("document-processing")
            .tasks(
                    function("extractText", functions::extractText)
                            .exportAs("{ extractedText: .extractedText }"),
                    function("analyze", functions::analyze)
                            .exportAs("{ result: . }")
            )
            .build();
}
```

Check https://github.com/quarkiverse/quarkus-flow for more information.

### Alternative: Kogito Serverless Workflow (JSON/YAML)

For declarative workflow definitions:

```xml
<dependency>
    <groupId>org.kie.kogito</groupId>
    <artifactId>kogito-quarkus-serverless-workflow</artifactId>
    <version>9.99.0.Final</version>
</dependency>
```

- **Declarative**: Define workflows in .sw.json/.sw.yaml files
- **Standard**: CNCF Serverless Workflow specification
- **Visual**: Can be designed with workflow tools
- **Portable**: Works with other Serverless Workflow runtimes

### Alternative: Custom Workflow Engine

Implement `FlowWorkflowDefinition` to integrate any workflow engine:
- Temporal
- Apache Camel
- Camunda
- Custom implementation

Other modules (casehub-core, casehub-examples) remain **unaffected** and do not depend on any workflow engine.

## Related Documentation

- **[Programmatic API Guide](PROGRAMMATIC_API.md)** - Detailed guide on using the programmatic workflow API
- [CaseHub Design Document](../design/CaseHub_Design_Document.md)
- [Worker Pattern](../casehub-examples/src/main/java/io/casehub/examples/workers/README.md)
- [Quarkus Flow Documentation](https://docs.quarkiverse.io/quarkus-flow/dev/index.html)
- [Quarkus Flow DSL Cheatsheet](https://docs.quarkiverse.io/quarkus-flow/dev/dsl-cheatsheet.html)
- [Quarkus Flow GitHub](https://github.com/quarkiverse/quarkus-flow)
- [CNCF Serverless Workflow Spec](https://serverlessworkflow.io/)

## Contributing

See [CONTRIBUTING.md](../CONTRIBUTING.md) for guidelines.

## License

Apache License 2.0 - See [LICENSE](../LICENSE) for details.
