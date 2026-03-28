# CaseHub Real Implementation Example

## DocumentAnalysisApp.java

A **complete, runnable example** using the actual CaseHub implementation to demonstrate all salient architectural features.

### What This Example Demonstrates

Unlike the simplified standalone example, this uses the **real CaseHub implementation**:

| Feature | Implementation |
|---------|----------------|
| **CaseFile** | Uses `io.casehub.core.CaseFile` and `DefaultCaseFile` |
| **TaskDefinition** | Implements `io.casehub.core.TaskDefinition` interface |
| **CaseEngine** | Uses `io.casehub.coordination.CaseEngine` for orchestration |
| **TaskDefinitionRegistry** | CDI-injected registry for TaskDefinition management |
| **Control Loop** | Real asynchronous control loop with CasePlanModel |
| **Data-Driven Activation** | ListenerEvaluator checks entry criteria automatically |
| **Quarkus/CDI** | Full Quarkus application with dependency injection |

### Architecture Overview

```
┌──────────────────────────────────────────────────────┐
│              CaseEngine (Orchestrator)               │
│  - Creates CaseFile + CasePlanModel                  │
│  - Runs control loop asynchronously                  │
│  - Detects changes, evaluates, executes              │
└──────────────────┬───────────────────────────────────┘
                   │
                   ├─> ListenerEvaluator
                   │   (checks TaskDefinition entry criteria)
                   │
                   ├─> PlanningStrategy
                   │   (prioritizes execution order)
                   │
                   └─> TaskDefinitions
                       ├─ TextExtractionTaskDefinition
                       ├─ EntityRecognitionTaskDefinition
                       ├─ RiskAnalysisTaskDefinition
                       └─ SummaryTaskDefinition
                              ↓
                    All write to shared CaseFile
```

### Running the Example

#### Option 1: Quarkus Dev Mode (Recommended)

```bash
cd casehub
mvn quarkus:dev
```

This will:
1. Start Quarkus in dev mode
2. Execute the DocumentAnalysisApp
3. Display real-time output
4. Support hot reload for code changes

#### Option 2: Build and Run

```bash
cd casehub
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```

#### Option 3: Native Build (Advanced)

```bash
cd casehub
mvn package -Pnative
./target/casehub-1.0.0-SNAPSHOT-runner
```

### Expected Output

```
╔════════════════════════════════════════════════════════════╗
║  CaseHub Architecture Demo: Legal Document Analysis       ║
║  Using Real CaseHub Implementation                         ║
╚════════════════════════════════════════════════════════════╝

📋 Registering TaskDefinitions...

  ✓ Registered: Text Extraction
    Needs: [raw_documents]
    Produces: [extracted_text]
  ✓ Registered: Named Entity Recognition
    Needs: [extracted_text]
    Produces: [entities]
  ✓ Registered: Risk Assessment
    Needs: [extracted_text, entities]
    Produces: [risk_assessment]
  ✓ Registered: Executive Summary Generator
    Needs: [entities, risk_assessment]
    Produces: [executive_summary]

📄 Creating Initial State...

  Initial CaseFile contains:
    raw_documents: 2 documents

⚙️  Creating case and starting CaseEngine control loop...

  [EXECUTING] Text Extraction...
    ✓ Extracted text from 2 documents
  [EXECUTING] Named Entity Recognition...
    ✓ Found 2 organizations, 3 dates, 1 amounts
  [EXECUTING] Risk Assessment...
    ✓ Risk Level: HIGH (score: 110/100)
  [EXECUTING] Executive Summary Generation...
    ✓ Executive summary generated, case marked complete

⏳ Waiting for case to complete...

═══════════════════════════════════════════════════════════
📊 FINAL RESULTS

  Case ID: a1b2c3d4-e5f6-7890-abcd-ef1234567890
  Status: COMPLETED
  Created: 2026-03-27T01:23:45.678Z

  CaseFile Contents:
  ─────────────────

  📌 extracted_text:
      doc_0: Contract between Acme Corp and Beta Inc. Effective 2026-01-01. Penalty...
      doc_1: Addendum dated 2026-02-15. Risk: Regulatory compliance required by...

  📌 entities:
      organizations: [Acme Corp, Beta Inc]
      dates: [2026-01-01, 2026-02-15, 2026-06-01]
      monetary_amounts: [$50,000]
      locations: [Delaware]

  📌 risk_assessment:
      risk_score: 110
      risk_level: HIGH
      risk_factors: [Financial penalties specified, Breach clauses present,
                     Regulatory compliance required, Contract termination clauses,
                     Auto-renewal terms present, Indemnification obligations]

  📌 executive_summary:
      EXECUTIVE SUMMARY
      ═════════════════

      Contract Parties:
        • Acme Corp
        • Beta Inc

      Key Dates: 2026-01-01, 2026-02-15, 2026-06-01
      Jurisdiction: Delaware

      RISK ASSESSMENT: HIGH (110/100)
      ─────────────────────────────────
      Identified Risk Factors:
        • Financial penalties specified
        • Breach clauses present
        • Regulatory compliance required
        • Contract termination clauses
        • Auto-renewal terms present
        • Indemnification obligations

      RECOMMENDATIONS:
      ────────────────
        🔴 URGENT: High-risk contract
        • Escalate to senior legal counsel immediately
        • Negotiate penalty cap and compliance timeline
        • Legal review required before execution
        • Archive for compliance audit trail

═══════════════════════════════════════════════════════════
🎯 KEY ARCHITECTURAL FEATURES DEMONSTRATED:

   ✓ Data-Driven Activation
     Each TaskDefinition fired when its entry criteria were met

   ✓ Collaborative Problem-Solving
     4 independent TaskDefinitions built the solution

   ✓ Shared Workspace (CaseFile)
     All TaskDefinitions read/write from common CaseFile

   ✓ Control Loop (CaseEngine)
     Automatic orchestration, evaluation, and execution

   ✓ Real CaseHub Implementation
     Using actual TaskDefinition, CaseFile, CaseEngine APIs

═══════════════════════════════════════════════════════════
```

### Code Walkthrough

#### 1. TaskDefinition Implementation

Each TaskDefinition implements the `io.casehub.core.TaskDefinition` interface:

```java
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
        // This TaskDefinition fires when "raw_documents" exists
        return Set.of("raw_documents");
    }

    @Override
    public Set<String> producedKeys() {
        // It produces "extracted_text"
        return Set.of("extracted_text");
    }

    @Override
    public void execute(CaseFile caseFile) {
        // Read from CaseFile
        Optional<List> docsOpt = caseFile.get("raw_documents", List.class);

        // Process data
        Map<String, String> extracted = extractText(docsOpt.get());

        // Write to CaseFile
        caseFile.put("extracted_text", extracted);
    }
}
```

#### 2. Registration with CaseEngine

TaskDefinitions are registered with the `TaskDefinitionRegistry`:

```java
@Inject
TaskDefinitionRegistry registry;

void registerTaskDefinitions() {
    String caseType = "legal-document-analysis";
    Set<String> caseTypes = Set.of(caseType);

    TaskDefinition textExtractor = new TextExtractionTaskDefinition();
    registry.register(textExtractor, caseTypes);

    // Register other TaskDefinitions...
}
```

The registry:
- Validates no circular dependencies
- Associates TaskDefinitions with case types
- Makes TaskDefinitions available to CaseEngine

#### 3. Creating and Solving a Case

```java
@Inject
CaseEngine caseEngine;

void runExample() {
    // Create initial state
    Map<String, Object> initialState = Map.of(
        "raw_documents", List.of("doc1.txt", "doc2.txt")
    );

    // Create case and start control loop
    CaseFile caseFile = caseEngine.createAndSolve(
        "legal-document-analysis",
        initialState
    );

    // Wait for completion (optional)
    caseEngine.awaitCompletion(caseFile, Duration.ofMinutes(1));

    // Access results
    Optional<String> summary = caseFile.get("executive_summary", String.class);
}
```

#### 4. CaseFile API Usage

The real `CaseFile` interface provides:

```java
// Read data (type-safe)
Optional<Map> entities = caseFile.get("entities", Map.class);

// Check existence
if (caseFile.contains("extracted_text")) {
    // ...
}

// Write data (triggers change events)
caseFile.put("risk_assessment", assessmentData);

// Optimistic concurrency
caseFile.putIfVersion("key", value, expectedVersion);

// Lifecycle management
caseFile.complete();  // Mark as complete
caseFile.fail(errorInfo);  // Mark as failed

// Get all keys
Set<String> keys = caseFile.keys();

// Snapshot entire state
Map<String, Object> snapshot = caseFile.snapshot();
```

### What Happens Under the Hood

1. **CaseEngine.createAndSolve()**
   - Creates a `DefaultCaseFile` with initial state
   - Creates a `DefaultCasePlanModel` for control
   - Starts async control loop in background thread

2. **Control Loop** (runs continuously)
   ```
   Loop:
     1. ListenerEvaluator checks which TaskDefinitions can fire
     2. Creates PlanItems for eligible TaskDefinitions
     3. PlanningStrategies prioritize PlanItems
     4. Execute top-priority PlanItem
     5. Re-evaluate with new CaseFile state
     6. Repeat until COMPLETED, FAULTED, or WAITING (quiescent)
   ```

3. **Data-Driven Activation**
   - `ListenerEvaluator` checks: `caseFile.contains(criterion)` for each criterion
   - If ALL entry criteria are met → create `PlanItem`
   - `PlanItem` added to `CasePlanModel` agenda

4. **Execution**
   - Top `PlanItem` selected from agenda
   - Corresponding `TaskDefinition.execute(caseFile)` called
   - TaskDefinition writes to CaseFile
   - Change events trigger re-evaluation

5. **Completion**
   - TaskDefinition calls `caseFile.complete()`
   - OR all PlanItems exhausted (quiescent → WAITING)
   - Control loop exits
   - Future completes, `awaitCompletion()` returns

### Differences from SimpleDocumentAnalysis

| Aspect | SimpleDocumentAnalysis | DocumentAnalysisApp |
|--------|----------------------|---------------------|
| **CaseFile** | Mock `SharedWorkspace` | Real `CaseFile` interface |
| **TaskDefinition** | POJO with `requiredKeys` | Implements `TaskDefinition` |
| **Control Loop** | Explicit in `main()` | Async in `CaseEngine` |
| **Activation** | Manual `canExecute()` check | Automatic via `ListenerEvaluator` |
| **Registration** | List in local variable | CDI `TaskDefinitionRegistry` |
| **Execution** | Sequential in one thread | Async, potentially parallel |
| **Lifecycle** | No formal states | `CaseStatus` (PENDING → RUNNING → WAITING/COMPLETED) |
| **Observability** | `System.out` only | Logging, metrics, events |
| **Dependencies** | None (standalone) | Quarkus, CDI, full CaseHub |

### Adding Your Own TaskDefinition

```java
// 1. Implement TaskDefinition
static class ComplianceCheckTaskDefinition implements TaskDefinition {
    @Override
    public String getId() { return "compliance-checker"; }

    @Override
    public String getName() { return "Compliance Verification"; }

    @Override
    public Set<String> entryCriteria() {
        return Set.of("entities", "risk_assessment");
    }

    @Override
    public Set<String> producedKeys() {
        return Set.of("compliance_report");
    }

    @Override
    public void execute(CaseFile caseFile) {
        // Read inputs
        Optional<Map> entities = caseFile.get("entities", Map.class);
        Optional<Map> risk = caseFile.get("risk_assessment", Map.class);

        // Process
        Map<String, Object> report = checkCompliance(entities.get(), risk.get());

        // Write output
        caseFile.put("compliance_report", report);
    }

    private Map<String, Object> checkCompliance(Map entities, Map risk) {
        // Your compliance logic here
        return Map.of("compliant", true, "issues", List.of());
    }
}

// 2. Register it
registry.register(new ComplianceCheckTaskDefinition(), Set.of("legal-document-analysis"));

// 3. That's it!
// It will automatically fire when "entities" and "risk_assessment" are available
// Any downstream TaskDefinition needing "compliance_report" will fire after it
```

### Extending to Use Workers (Task Model)

See `DocumentAnalysisWithWorkers.java` (coming next) for an example showing:
- TaskDefinition delegating work to external Workers
- Dual execution model (CaseFile + Task)
- Worker registration, task claiming, result submission
- Propagation context flowing across models

### Debugging Tips

#### Enable Debug Logging

Add to `application.properties`:
```properties
quarkus.log.category."io.casehub".level=DEBUG
```

#### Inspect CaseFile State

```java
// Get snapshot at any point
Map<String, Object> snapshot = caseFile.snapshot();
System.out.println("Current state: " + snapshot);

// Check status
CaseStatus status = caseFile.getStatus();
System.out.println("Status: " + status);

// Get all keys
Set<String> keys = caseFile.keys();
System.out.println("Available keys: " + keys);
```

#### Monitor Control Loop

```java
CasePlanModel planModel = caseEngine.getCasePlanModel(caseFile);
List<PlanItem> agenda = planModel.getAgenda();
System.out.println("Pending PlanItems: " + agenda.size());
```

### Performance Considerations

The real CaseHub implementation:

- **Async by default**: Control loop runs in background thread pool
- **Thread-safe**: CaseFile uses `ConcurrentHashMap`, atomic operations
- **Optimistic concurrency**: `putIfVersion()` for conflict detection
- **Change listeners**: Only re-evaluate affected TaskDefinitions
- **Lazy evaluation**: TaskDefinitions only execute when needed

For high-throughput scenarios, consider:
- Custom `PlanningStrategy` for parallelization
- Storage provider (Redis) instead of in-memory
- Worker delegation for heavy computation

### Next Steps

1. **Run the example**: `mvn quarkus:dev`
2. **Modify a TaskDefinition**: Add your own logic
3. **Add a TaskDefinition**: Create a new specialist
4. **Explore Worker integration**: See `DocumentAnalysisWithWorkers.java`
5. **Read the design doc**: `/docs/DESIGN.md`

---

**This example demonstrates production-ready CaseHub usage with the actual implementation.**
