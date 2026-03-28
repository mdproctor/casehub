# CaseHub Architecture Examples

This directory contains two comprehensive examples demonstrating CaseHub's Blackboard Architecture.

## Examples Overview

### 1. SimpleDocumentAnalysis.java ⭐ Start Here

**Self-contained conceptual demonstration** - No dependencies, runs anywhere

```bash
cd casehub/src/main/java
javac io/casehub/examples/SimpleDocumentAnalysis.java
java io.casehub.examples.SimpleDocumentAnalysis
```

**Best for:**
- Understanding core concepts quickly
- Learning the Blackboard Architecture pattern
- Seeing data-driven activation clearly
- No setup required

**What it demonstrates:**
- ✓ Shared workspace pattern
- ✓ Data-driven activation
- ✓ Collaborative problem-solving
- ✓ Opportunistic execution

📖 [Full Documentation](./README.md) - Original detailed guide

---

### 2. DocumentAnalysisApp.java 🚀 Real Implementation

**Production-ready example** - Uses actual CaseHub implementation with Quarkus

```bash
cd casehub
mvn quarkus:dev
```

**Best for:**
- Seeing the real CaseHub API in action
- Understanding how to build actual applications
- Quarkus/CDI integration
- Production patterns and practices

**What it demonstrates:**
- ✓ Real `CaseFile`, `TaskDefinition`, `CaseEngine` interfaces
- ✓ Asynchronous control loop
- ✓ CDI dependency injection
- ✓ TaskDefinitionRegistry
- ✓ Lifecycle management (CaseStatus)
- ✓ Optimistic concurrency
- ✓ Change listeners and events

📖 [Full Documentation](./README_REAL_IMPLEMENTATION.md) - Implementation guide

---

## Quick Comparison

| Aspect | SimpleDocumentAnalysis | DocumentAnalysisApp |
|--------|----------------------|---------------------|
| **Purpose** | Learning & concepts | Production usage |
| **Dependencies** | None | Quarkus, CaseHub |
| **Complexity** | 320 lines | 480 lines |
| **Setup** | `javac` + `java` | `mvn quarkus:dev` |
| **CaseFile** | Mock implementation | Real `io.casehub.core.CaseFile` |
| **Control Loop** | Explicit in code | Async in `CaseEngine` |
| **Best for** | Understanding | Building |

## The Scenario: Legal Document Analysis

Both examples analyze legal contracts through the same workflow:

```
┌──────────────┐
│raw_documents │  (2 contract documents)
└──────┬───────┘
       │
       ▼
┌──────────────────┐
│ Text Extraction  │  OCR/text extraction
└─────┬────────────┘
      │
      ├─> extracted_text
      │
      ▼
┌──────────────────────┐
│ Entity Recognition   │  NER (organizations, dates, amounts)
└─────┬────────────────┘
      │
      ├─> entities
      │
      ▼
┌──────────────────────┐         ┌─────────────────┐
│ Risk Assessment      │ ◄───────┤ extracted_text  │
└─────┬────────────────┘         └─────────────────┘
      │                                  ▲
      ├─> risk_assessment                │
      │                                   │
      ▼                          ┌────────┴────────┐
┌──────────────────────┐         │    entities     │
│ Executive Summary    │ ◄───────┴─────────────────┘
└─────┬────────────────┘
      │
      └─> executive_summary
          (FINAL OUTPUT)
```

**Key Points:**
- No hardcoded execution order
- Each step fires when its inputs are available
- Solution emerges from collaborative contributions
- Demonstrates data-driven activation clearly

---

## Sample Output (Both Examples)

```
╔════════════════════════════════════════════════════════════╗
║  CaseHub Architecture Demo: Legal Document Analysis       ║
╚════════════════════════════════════════════════════════════╝

📋 Registered Specialist Tasks/TaskDefinitions

⚙️  CaseEngine Control Loop

   ─────────── Iteration 1 ───────────
   ✓ Text Extraction is eligible
     [EXECUTING] Extracting text from documents...
     └─> Contributed: [extracted_text]

   ✓ Named Entity Recognition is eligible
     [EXECUTING] Extracting named entities...
     └─> Contributed: [entities]

   ✓ Risk Assessment is eligible
     [EXECUTING] Analyzing risk factors...
     └─> Contributed: [risk_assessment]

   ✓ Executive Summary is eligible
     [EXECUTING] Generating executive summary...
     └─> Contributed: [executive_summary]

📊 FINAL RESULTS

   📌 executive_summary:
      EXECUTIVE SUMMARY
      ═════════════════

      Contract Parties:
        • Acme Corp
        • Beta Inc

      RISK ASSESSMENT: HIGH (110/100)

      Identified Risk Factors:
        • Financial penalties specified
        • Breach clauses present
        • Regulatory compliance required
        • Contract termination clauses
        • Auto-renewal terms present
        • Indemnification obligations

      RECOMMENDATIONS:
        🔴 URGENT: High-risk contract
        • Escalate to senior legal counsel immediately
        • Negotiate penalty cap and compliance timeline
        • Legal review required before execution

🎯 KEY ARCHITECTURAL FEATURES DEMONSTRATED

   ✓ Data-Driven Activation
   ✓ Collaborative Problem-Solving
   ✓ Shared Workspace Pattern
   ✓ Opportunistic Execution
   ✓ Blackboard Architecture
```

---

## Architecture Concepts

### The Blackboard Pattern (Hayes-Roth 1985)

Three core components working together:

```
┌─────────────────────────────────────────────────┐
│           BLACKBOARD (Shared Workspace)         │
│  ┌──────┬────────┬──────────┬──────────┐      │
│  │ text │entities│risk_score│ summary  │      │
│  └──────┴────────┴──────────┴──────────┘      │
└────────▲──────────▲──────────▲─────────────────┘
         │          │          │
    ┌────┴───┐  ┌──┴────┐  ┌──┴────┐
    │Specialist│  │Specialist│  │Specialist│
    │   (KS)  │  │   (KS)  │  │   (KS)  │
    └─────────┘  └────────┘  └────────┘
              ▲       ▲       ▲
              └───────┴───────┘
                      │
              ┌───────┴────────┐
              │  Control Layer │
              │  (Orchestrator)│
              └────────────────┘
```

**Three Components:**
1. **Blackboard**: Shared data repository (CaseFile)
2. **Knowledge Sources**: Independent specialists (TaskDefinitions)
3. **Control**: Orchestrator (CaseEngine)

### Data-Driven Activation

**Traditional Workflow:**
```
Step 1 → Step 2 → Step 3 → Step 4  (rigid)
```

**Blackboard Pattern:**
```
Initial State
    ↓
[Which specialists can contribute?]
    ↓
Execute eligible specialists
    ↓
New data added to workspace
    ↓
[Which specialists can contribute now?]
    ↓
Repeat until complete/quiescent
```

### Why This Matters for Agentic AI

**Extensibility**: Add new specialist → automatic participation
**Resilience**: Partial failures don't block entire workflow
**Opportunistic**: Multiple paths to solution
**Discoverable**: System explores solution space dynamically

---

## Recommended Learning Path

1. **Start with SimpleDocumentAnalysis** (10 minutes)
   - Run it: `java SimpleDocumentAnalysis`
   - Read the output, understand the flow
   - Review the code: see how it's all conceptual

2. **Understand the concepts** (20 minutes)
   - Read: [Blackboard Architecture explained](./README.md#the-blackboard-architecture-pattern)
   - Read: [Data-driven activation](./README.md#data-driven-activation-in-detail)
   - Read: [Why for agentic AI](./README.md#why-blackboard-for-agentic-ai)

3. **Try DocumentAnalysisApp** (30 minutes)
   - Run it: `mvn quarkus:dev`
   - See real CaseHub implementation in action
   - Review code: see actual `TaskDefinition` implementations

4. **Modify and experiment** (60 minutes)
   - Add your own TaskDefinition
   - Change the workflow
   - See data-driven activation dynamically adapt

5. **Read the design document**
   - `/design/CaseHub_Design_Document.md` - comprehensive architecture
   - Understand the full system design

6. **Build something real**
   - Use DocumentAnalysisApp as template
   - Implement your own use case
   - Leverage Workers for distributed execution

---

## Use Cases

The Blackboard pattern (CaseHub) is ideal for:

### ✅ Good Fit

- **Multi-agent collaboration**: Multiple AI agents working on shared problem
- **Dynamic workflows**: Execution path depends on data/context
- **Incremental solutions**: Solution built step-by-step
- **Opportunistic execution**: Multiple ways to achieve same goal
- **Complex analysis**: Document analysis, fraud detection, diagnostics

### ❌ Less Ideal

- **Simple request-response**: Use Task model instead
- **Fixed pipelines**: Traditional workflow engine may be simpler
- **Real-time streaming**: Different architecture needed
- **Stateless processing**: No shared workspace needed

---

## Further Reading

- **CaseHub Design Document**: `/design/CaseHub_Design_Document.md`
- **CMMN Specification**: [OMG CMMN](https://www.omg.org/cmmn/)
- **Blackboard Architecture**: Hayes-Roth 1985 paper
- **Build Instructions**: `/CLAUDE.md`
- **Quarkus Guide**: [quarkus.io](https://quarkus.io)

---

## Contributing

Want to add more examples? Great!

**Ideas:**
- Medical diagnosis workflow
- Fraud detection pipeline
- Multi-modal content analysis
- Scientific experiment automation
- Business process automation

See the existing examples as templates.

---

## Support

- **Issues**: Report at the main project repository
- **Questions**: See `/CLAUDE.md` for documentation
- **Design**: Read `/design/CaseHub_Design_Document.md`

---

**Start with `SimpleDocumentAnalysis.java` to learn concepts, then move to `DocumentAnalysisApp.java` for production usage.**
