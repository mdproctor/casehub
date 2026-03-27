# CaseHub Architecture Example - Summary

## What Was Created

A complete, working example demonstrating all salient features of the CaseHub architecture through a **Legal Document Analysis** scenario.

### Files

1. **SimpleDocumentAnalysis.java** - Runnable demonstration (362 lines)
2. **README.md** - Comprehensive documentation explaining the architecture

## How to Run

```bash
cd casehub/src/main/java
javac io/casehub/examples/SimpleDocumentAnalysis.java
java io.casehub.examples.SimpleDocumentAnalysis
```

## What It Demonstrates

### 1. **Blackboard Architecture Pattern** (Hayes-Roth 1985)

The fundamental pattern separating:
- **Workspace**: Shared data repository (CaseFile)
- **Knowledge Sources**: Independent specialists (TaskDefinitions)
- **Control**: Orchestration layer (CaseEngine)

### 2. **Data-Driven Activation**

Tasks fire automatically when their required data is present:

```
Iteration 1:
  ✓ Text Extraction (needs: raw_documents) → produces: extracted_text
  ✓ NER (needs: extracted_text) → produces: entities
  ✓ Risk Analysis (needs: extracted_text + entities) → produces: risk_assessment
  ✓ Summary (needs: entities + risk_assessment) → produces: executive_summary

Iteration 2:
  ⏸ Quiescent (no more eligible tasks)
```

### 3. **Collaborative Problem-Solving**

Four independent specialists work together without direct communication:
- **Text Extractor**: Processes raw documents
- **Entity Recognizer**: Finds organizations, dates, amounts
- **Risk Analyzer**: Evaluates contract risk
- **Summarizer**: Generates executive summary

Each only knows what inputs it needs and what outputs it produces.

### 4. **Opportunistic Execution**

No hardcoded workflow. Execution order emerges from data dependencies:
- If `extracted_text` appears in the workspace → NER can fire
- If both `entities` and `risk_assessment` exist → Summary can fire
- System explores solution space dynamically

### 5. **Quiescence Detection**

System recognizes when no more progress can be made:
- All tasks have fired OR
- No remaining tasks have their preconditions satisfied

### 6. **Shared Workspace**

All specialists read/write from common data space:

```
Workspace State Evolution:

Initial:
  raw_documents: [doc1, doc2]

After Text Extraction:
  raw_documents: [doc1, doc2]
  extracted_text: {doc_0: "...", doc_1: "..."}

After NER:
  raw_documents: [doc1, doc2]
  extracted_text: {doc_0: "...", doc_1: "..."}
  entities: {organizations: [...], dates: [...], amounts: [...]}

After Risk Analysis:
  raw_documents: [doc1, doc2]
  extracted_text: {doc_0: "...", doc_1: "..."}
  entities: {organizations: [...], dates: [...], amounts: [...]}
  risk_assessment: {risk_score: 100, risk_level: "HIGH", risk_factors: [...]}

After Summary:
  raw_documents: [doc1, doc2]
  extracted_text: {doc_0: "...", doc_1: "..."}
  entities: {organizations: [...], dates: [...], amounts: [...]}
  risk_assessment: {risk_score: 100, risk_level: "HIGH", risk_factors: [...]}
  executive_summary: "EXECUTIVE SUMMARY\n..."
```

## Sample Output

```
╔════════════════════════════════════════════════════════════╗
║  CaseHub Architecture Demo: Legal Document Analysis       ║
╚════════════════════════════════════════════════════════════╝

📋 Registered Specialist Tasks:
   Text Extraction
      Needs: [raw_documents]
      Produces: [extracted_text]
   ...

📄 Initial State:
   raw_documents: 2 documents loaded

⚙️  CaseEngine Control Loop:
   ─────────── Iteration 1 ───────────
   ✓ Text Extraction is eligible
     [EXECUTING] Extracting text from documents...
     └─> Contributed: [extracted_text]
   ...

📊 FINAL WORKSPACE STATE:
   📌 executive_summary:
      EXECUTIVE SUMMARY
      ─────────────────

      Parties: Beta Inc and Acme Corp
      Risk Assessment: HIGH (100/100)

      Identified Risk Factors:
        • Financial penalties specified
        • Breach clauses present
        • Regulatory compliance required

      Recommendations:
        • URGENT: Legal review required before execution
        • Escalate to senior counsel

🎯 KEY ARCHITECTURAL FEATURES DEMONSTRATED:
   ✓ Data-Driven Activation
   ✓ Collaborative Problem-Solving
   ✓ Shared Workspace Pattern
   ✓ Opportunistic Execution
   ✓ Blackboard Architecture
```

## Architecture Diagram

```
┌─────────────────────────────────────────────────────┐
│              SHARED WORKSPACE                       │
│                                                     │
│  raw_docs → extracted_text → entities              │
│                           ↘                         │
│                            risk_assessment          │
│                           ↗                         │
│  extracted_text ──────────┘                        │
│                                                     │
│  entities + risk_assessment → executive_summary    │
└──────────▲─────────▲────────▲────────▲─────────────┘
           │         │        │        │
      ┌────┴───┬────┴──┬────┴──┬────┴────┐
      │  Text  │  NER  │ Risk  │Summary  │
      │Extract │       │Analyze│         │
      └────────┴───────┴───────┴─────────┘
                      ▲
                      │
           ┌──────────┴─────────────┐
           │   Control Loop         │
           │  - Check eligibility   │
           │  - Execute specialists │
           │  - Repeat until done   │
           └────────────────────────┘
```

## Why This Matters for Agentic AI

### Traditional Workflow Engines

```
Task A → Task B → Task C → Task D
```

- Rigid, sequential execution
- Adding new capability requires workflow changes
- Single path to solution
- Brittle to failures

### Blackboard Architecture

```
Initial State → [Evaluate all specialists]
                      ↓
                Execute eligible specialists
                      ↓
                Workspace updated
                      ↓
                [Re-evaluate all specialists]
                      ↓
                Repeat...
```

- Dynamic, parallel execution
- Adding new specialist requires no workflow changes
- Multiple paths to solution (opportunistic)
- Resilient to partial failures

### Real-World Benefits

1. **Extensibility**: Add a new specialist (e.g., "Compliance Checker") by registering it. If it produces a key needed by existing specialists, they automatically use it.

2. **Alternative Paths**: Have two specialists produce `extracted_text` (fast API and accurate OCR). Whichever completes first unblocks downstream work.

3. **Partial Results**: If OCR fails but text extraction succeeds, NER/Risk/Summary can still run.

4. **Agent Composition**: Different agents (LLMs, rule engines, APIs, humans) can all be specialists contributing to the workspace.

5. **Observable**: At any point, inspect the workspace to see current solution state.

## Mapping to Real CaseHub Components

| Example Component | Real CaseHub Component | Purpose |
|------------------|----------------------|---------|
| `SharedWorkspace` | `CaseFile` | Shared key-value workspace |
| `SpecialistTask` | `TaskDefinition` | Independent domain specialist |
| Control loop (main method) | `CaseEngine` | Orchestration and activation |
| `requiredKeys` | `entryCriteria()` | Data dependencies |
| `producedKeys` | `producedKeys()` | Contribution specification |
| - | `PlanningStrategy` | Control reasoning (which specialist next?) |
| - | `Worker` | External executor for heavy work |
| - | `TaskBroker` | Request-response task submission |
| - | `PropagationContext` | Hierarchical tracing |

## Next Steps

1. **Read the Full Design**: See `CaseHub_Design_Document.md` for complete architecture
2. **Explore the Code**: `casehub/src/main/java/io/casehub/` for actual implementation
3. **Build It**: `mvn compile` from `casehub/` directory
4. **Modify Example**: Try adding your own specialist to `SimpleDocumentAnalysis.java`

## Key Takeaways

✅ **CaseHub implements the Blackboard Architecture pattern** for agentic AI coordination

✅ **Data-driven activation** replaces rigid workflows with dynamic, opportunistic execution

✅ **Specialists are independent** - they don't know about each other, only data dependencies

✅ **Solution emerges collaboratively** from multiple independent contributions

✅ **Extensible by design** - add new specialists without changing existing code

✅ **Observable and traceable** - workspace state shows solution progress at any time

✅ **Dual execution model** - collaborative (CaseFile) + request-response (Task) working together

---

*This example demonstrates the core architectural principles. The real CaseHub implementation adds resilience (retry, timeout, dead-letter), observability (metrics, tracing), security (RBAC, API keys), and scalability (distributed Workers, pluggable storage).*
