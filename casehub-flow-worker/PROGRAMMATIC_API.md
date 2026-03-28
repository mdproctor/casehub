# Quarkus Flow Programmatic API Implementation

## Overview

The casehub-flow-worker now uses the **Quarkus Flow programmatic API** to define workflows using a fluent Java DSL, rather than JSON/YAML files.

## Implementation

### Workflow Definition

Workflows extend `io.quarkiverse.flow.Flow` and override the `descriptor()` method:

```java
@ApplicationScoped
public class QuarkusFlowDocumentWorkflow extends Flow implements FlowWorkflowDefinition {

    @Inject
    DocumentFunctions documentFunctions;

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("document-processing")
                .tasks(
                        // Task 1: Extract text
                        function("extractText",
                                (Map<String, Object> input) -> documentFunctions.extractText(input))
                                .exportAs("{ extractedText: .extractedText, wordCount: .wordCount }"),

                        // Task 2: Recognize entities
                        function("recognizeEntities",
                                (Map<String, Object> ctx) -> documentFunctions.recognizeEntities(
                                        Map.of("text", ctx.get("extractedText"))))
                                .exportAs("{ entities: .entities }"),

                        // Task 3: Analyze sentiment
                        function("analyzeSentiment",
                                (Map<String, Object> ctx) -> documentFunctions.analyzeSentiment(
                                        Map.of("text", ctx.get("extractedText"))))
                                .exportAs("{ sentiment: .sentiment }"),

                        // Task 4: Generate summary
                        function("generateSummary",
                                (Map<String, Object> ctx) -> documentFunctions.generateSummary(
                                        Map.of(
                                                "text", ctx.get("extractedText"),
                                                "entities", ctx.get("entities"),
                                                "sentiment", ctx.get("sentiment")
                                        )))
                                .exportAs("{ summary: .summary }")
                )
                .build();
    }
}
```

### Key Components

| Component | Purpose |
|-----------|---------|
| **FuncWorkflowBuilder** | Fluent API for building workflows |
| **function()** | Define a task that calls a Java function |
| **.exportAs()** | Transform and merge results back to context |
| **.build()** | Finalize the workflow definition |

### Static Imports

```java
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;
```

This provides access to DSL methods:
- `function()` - Call Java functions
- `set()` - Set context values
- `emitJson()` - Emit events
- `consume()` - Consume events
- `withContext()` - Access workflow context

### Workflow Execution

```java
@Override
public Map<String, Object> execute(FlowExecutionContext context) throws Exception {
    // Extract input from CaseHub context
    String documentUrl = context.getInput("documentUrl", String.class)
            .orElseThrow(() -> new IllegalArgumentException("Missing required input: documentUrl"));

    // Create workflow input
    Map<String, Object> workflowInput = new HashMap<>();
    workflowInput.put("documentUrl", documentUrl);
    workflowInput.put("language", context.getInput("language", String.class).orElse("en"));

    // Execute workflow using Quarkus Flow
    var result = this.startInstance(workflowInput).await().indefinitely();

    // Extract and return results
    return extractWorkflowData(result);
}

private Map<String, Object> extractWorkflowData(WorkflowModel model) {
    return model.asMap().orElse(Map.of());
}
```

## Benefits of Programmatic API

### 1. **Type Safety**
- Java compiler checks function signatures
- No runtime errors from typos in JSON
- IDE autocomplete for all DSL methods

### 2. **Refactoring Support**
- Rename methods with IDE refactoring
- Find usages across workflow definitions
- Navigate directly to function implementations

### 3. **Inline Lambda Support**
```java
function("transform",
        (Map<String, Object> input) -> {
            // Complex transformation logic inline
            return processedData;
        })
```

### 4. **Dynamic Workflow Construction**
```java
@Override
public Workflow descriptor() {
    var builder = FuncWorkflowBuilder.workflow("document-processing");

    // Conditionally add tasks
    if (enableAdvancedProcessing) {
        builder.tasks(
            function("extractText", documentFunctions::extractText),
            function("deepAnalysis", documentFunctions::deepAnalysis)
        );
    } else {
        builder.tasks(
            function("extractText", documentFunctions::extractText),
            function("basicAnalysis", documentFunctions::basicAnalysis)
        );
    }

    return builder.build();
}
```

### 5. **Better Debugging**
- Set breakpoints in workflow definition
- Step through task construction
- Inspect function references

## Data Flow

### Task Input/Output

Each task receives the workflow context and can:
1. **Read from context**: Access data from previous tasks
2. **Execute function**: Call DocumentFunctions methods
3. **Export results**: Merge results back using `.exportAs()`

```java
function("recognizeEntities",
        (Map<String, Object> ctx) -> documentFunctions.recognizeEntities(
                Map.of("text", ctx.get("extractedText"))))  // Read from context
        .exportAs("{ entities: .entities }")  // Export to context
```

### Data Transformation

Use JQ-style expressions in `.exportAs()`:

```java
// Simple field extraction
.exportAs("{ extractedText: .extractedText }")

// Multiple fields
.exportAs("{ extractedText: .extractedText, wordCount: .wordCount }")

// Nested structures
.exportAs("{ result: { data: ., metadata: .info } }")
```

### Java Function Transformations

For complex transformations, use lambda expressions:

```java
.exportAs((result, context) -> {
    Map<String, Object> transformed = new HashMap<>(context);
    transformed.put("processedResult", processResult(result));
    transformed.put("timestamp", Instant.now());
    return transformed;
}, ResultType.class)
```

## Workflow Features

### Sequential Execution

Tasks execute in order:

```java
.tasks(
    function("step1", service::step1),
    function("step2", service::step2),
    function("step3", service::step3)
)
```

### Context Access

Functions receive the full workflow context:

```java
function("analyze",
        (Map<String, Object> ctx) -> {
            String text = (String) ctx.get("extractedText");
            List entities = (List) ctx.get("entities");
            return analyzer.analyze(text, entities);
        })
```

### Error Handling

Exceptions propagate to CaseHub's resilience layer:

```java
function("processDocument",
        (Map<String, Object> input) -> {
            try {
                return documentFunctions.process(input);
            } catch (ValidationException e) {
                log.error("Validation failed", e);
                throw new RuntimeException("Document validation failed", e);
            }
        })
```

## Comparison: Programmatic vs JSON

| Aspect | Programmatic API | JSON/YAML |
|--------|------------------|-----------|
| **Type Safety** | ✅ Compile-time | ❌ Runtime only |
| **IDE Support** | ✅ Full autocomplete | ⚠️ Limited |
| **Refactoring** | ✅ Automated | ❌ Manual |
| **Debugging** | ✅ Breakpoints | ⚠️ Limited |
| **Dynamic Construction** | ✅ Yes | ❌ Static |
| **Version Control** | ✅ Code diffs | ⚠️ Large JSON diffs |
| **Readability** | ✅ For developers | ✅ For non-developers |
| **Portability** | ⚠️ Quarkus Flow | ✅ Any SW spec runtime |

## Advanced Patterns

### Conditional Tasks

```java
function("conditionalStep",
        (Map<String, Object> ctx) -> {
            if (shouldProcess(ctx)) {
                return processor.process(ctx);
            } else {
                return Map.of("skipped", true);
            }
        })
```

### Loops (Conceptual)

While the current implementation uses sequential tasks, Quarkus Flow supports loops:

```java
// Note: This requires additional DSL support
forLoop("processItems",
        ctx -> (List) ctx.get("items"),
        (item, ctx) -> processor.processItem(item))
```

### Parallel Execution (Future Enhancement)

```java
// Conceptual - would require DSL support
parallel(
    function("branch1", service::operation1),
    function("branch2", service::operation2)
)
```

## Testing

### Unit Test Workflow Definition

```java
@QuarkusTest
class QuarkusFlowDocumentWorkflowTest {

    @Inject
    QuarkusFlowDocumentWorkflow workflow;

    @Test
    void testDescriptor() {
        Workflow descriptor = workflow.descriptor();

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.id()).isEqualTo("document-processing");
    }

    @Test
    void testWorkflowExecution() {
        Map<String, Object> input = Map.of(
            "documentUrl", "https://example.com/doc.pdf",
            "language", "en"
        );

        var result = workflow.startInstance(input).await().indefinitely();

        Map<String, Object> data = result.asMap().orElseThrow();
        assertThat(data).containsKeys("extractedText", "entities", "sentiment", "summary");
    }
}
```

### Mock Functions

```java
@QuarkusTest
class WorkflowWithMocksTest {

    @InjectMock
    DocumentFunctions documentFunctions;

    @Inject
    QuarkusFlowDocumentWorkflow workflow;

    @Test
    void testWithMockedFunctions() {
        when(documentFunctions.extractText(any()))
                .thenReturn(Map.of("extractedText", "test", "wordCount", 1));

        var result = workflow.startInstance(Map.of("documentUrl", "test")).await().indefinitely();

        verify(documentFunctions).extractText(any());
    }
}
```

## CaseHub Integration

The workflow integrates seamlessly with CaseHub:

```java
// Extends Flow for Quarkus Flow
// Implements FlowWorkflowDefinition for CaseHub
public class QuarkusFlowDocumentWorkflow extends Flow implements FlowWorkflowDefinition {

    @Override
    public String getWorkflowId() {
        return "quarkus-flow-document-processing";
    }

    @Override
    public Workflow descriptor() {
        // Quarkus Flow programmatic definition
    }

    @Override
    public Map<String, Object> execute(FlowExecutionContext context) {
        // CaseHub execution bridge
        var result = this.startInstance(input).await().indefinitely();
        return extractWorkflowData(result);
    }
}
```

## Resources

- [Quarkus Flow Documentation](https://docs.quarkiverse.io/quarkus-flow/dev/index.html)
- [Quarkus Flow DSL Cheatsheet](https://docs.quarkiverse.io/quarkus-flow/dev/dsl-cheatsheet.html)
- [Quarkus Flow GitHub](https://github.com/quarkiverse/quarkus-flow)
- [CNCF Serverless Workflow Spec](https://serverlessworkflow.io/)

## Summary

The programmatic API provides:
- ✅ **Type-safe** workflow definitions
- ✅ **Refactoring-friendly** with IDE support
- ✅ **Debuggable** with breakpoints
- ✅ **Dynamic** workflow construction
- ✅ **Integrated** with CaseHub task coordination

This approach is ideal for developers who prefer code-first workflow definitions with full Java tooling support.
