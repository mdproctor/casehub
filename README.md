# CaseHub

**Blackboard Architecture for Quarkus-Based Agentic AI**

CaseHub is a lightweight case management framework implementing the classic Blackboard Architecture pattern (Hayes-Roth 1985) using CMMN (Case Management Model and Notation) terminology. It provides a collaborative problem-solving environment where multiple autonomous agents work together on shared data.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.17.5-blue.svg)](https://quarkus.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

---

## 🌟 Key Features

- **Dual Execution Model**: Collaborative (CaseFile) + Request-Response (Task) working seamlessly
- **Data-Driven Activation**: Tasks fire automatically when their input data becomes available
- **Blackboard Architecture**: Proven pattern for multi-agent collaboration
- **CMMN Terminology**: Industry-standard case management concepts
- **Quarkus Native**: Fast startup, low memory, cloud-native ready
- **Worker Model**: Distributed processing with capability-based routing
- **Observable**: Built-in metrics, structured logging, health checks
- **Resilient**: Retry policies, timeouts, dead-letter queues, poison pill detection
- **Type-Safe**: Versioned CaseFile updates with optimistic concurrency

---

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Quarkus 3.17.5

### Run the Examples

```bash
# Clone the repository
git clone <repository-url>
cd casehub

# Compile all modules
mvn compile

# Run the document analysis examples
cd casehub-examples
mvn quarkus:dev
```

### Simple Conceptual Example

```bash
# No dependencies - runs anywhere!
cd casehub-examples/src/main/java
javac io/casehub/examples/SimpleDocumentAnalysis.java
java io.casehub.examples.SimpleDocumentAnalysis
```

---

## 📖 What is the Blackboard Architecture?

The Blackboard pattern separates complex problem-solving into three components:

```
┌─────────────────────────────┐
│   Shared Workspace          │  ← Data accumulates here
│   (CaseFile/Blackboard)     │
└──────▲──────▲──────▲────────┘
       │      │      │
   ┌───┴──┐ ┌┴───┐ ┌┴────┐
   │Agent │ │Agent│ │Agent│    ← Specialists contribute
   │  1   │ │  2  │ │  3  │      when they can
   └──────┘ └────┘ └─────┘
            ▲  ▲  ▲
            └──┴──┘
               │
      ┌────────┴─────────┐
      │ Control Layer    │    ← Orchestrates execution
      │ (CaseEngine)     │
      └──────────────────┘
```

**Traditional Workflow:**
```
Task A → Task B → Task C → Task D  (rigid, sequential)
```

**Blackboard/CaseHub:**
```
Initial State
    ↓
Which agents can contribute?
    ↓
Execute eligible agents
    ↓
New data added to workspace
    ↓
Which agents can contribute NOW?
    ↓
Repeat until complete
```

**Why This Matters:**
- ✅ **Extensible**: Add new agent → automatic participation
- ✅ **Resilient**: One agent fails → others continue
- ✅ **Opportunistic**: Multiple paths to solution
- ✅ **Dynamic**: Solution discovered at runtime

---

## 🏗️ Architecture

CaseHub implements two complementary execution models:

### 1. CaseFile Model (Collaborative)

Multiple **TaskDefinitions** collaborate on a shared **CaseFile**:

```java
// TaskDefinition declares what it needs and produces
public class RiskAnalysisTaskDefinition implements TaskDefinition {
    @Override
    public Set<String> entryCriteria() {
        return Set.of("entities", "extracted_text");  // I need these
    }

    @Override
    public Set<String> producedKeys() {
        return Set.of("risk_assessment");  // I produce this
    }

    @Override
    public void execute(CaseFile caseFile) {
        // Read from shared workspace
        Map entities = caseFile.get("entities", Map.class).get();
        Map text = caseFile.get("extracted_text", Map.class).get();

        // Analyze
        Map riskAssessment = analyzeRisk(entities, text);

        // Write to shared workspace
        caseFile.put("risk_assessment", riskAssessment);
    }
}
```

**CaseEngine** automatically:
- Evaluates which TaskDefinitions can fire
- Creates PlanItems for eligible TaskDefinitions
- Executes them in priority order
- Re-evaluates after each execution
- Completes when quiescent or explicitly marked complete

### 2. Task Model (Request-Response)

Delegate heavy work to distributed **Workers**:

```java
// TaskBroker accepts task submissions
TaskRequest request = TaskRequest.builder()
    .taskType("llm-analysis")
    .context(Map.of("prompt", prompt, "max_tokens", 3000))
    .requiredCapabilities(Set.of("llm", "reasoning"))
    .build();

TaskHandle handle = taskBroker.submitTask(request);
TaskResult result = handle.awaitResult(Duration.ofMinutes(2));
```

**Workers** with matching capabilities:
- Register with WorkerRegistry
- Claim tasks via polling
- Process and submit results
- Send heartbeats to indicate liveness

### Dual Model Integration

TaskDefinitions can delegate to Workers:

```java
public class LlmAnalysisTaskDefinition implements TaskDefinition {
    public void execute(CaseFile caseFile) {
        // 1. Read from CaseFile
        Map data = caseFile.get("data", Map.class).get();

        // 2. Delegate to Worker
        TaskResult result = taskBroker.submitTask(buildRequest(data))
                                      .awaitResult(Duration.ofMinutes(2));

        // 3. Contribute Worker's result to CaseFile
        caseFile.put("llm_insights", result.getData());
    }
}
```

---

## 📁 Project Structure

CaseHub is a multi-module Maven project:

```
casehub/
├── pom.xml                      # Parent POM
├── casehub-core/                # Core framework
│   └── src/main/java/io/casehub/
│       ├── core/                # CaseFile, TaskDefinition, ListenerEvaluator
│       ├── control/             # CasePlanModel, PlanningStrategy, PlanItem
│       ├── coordination/        # CaseEngine, PropagationContext, LineageService
│       ├── worker/              # Task, Worker, TaskBroker, WorkerRegistry
│       ├── resilience/          # RetryPolicy, Timeout, DeadLetter, Idempotency
│       └── error/               # Exception types, ErrorInfo
├── casehub-examples/            # Working examples
│   └── src/main/java/io/casehub/examples/
│       ├── SimpleDocumentAnalysis.java       # Standalone conceptual demo
│       ├── DocumentAnalysisApp.java          # Real implementation
│       └── workers/
│           ├── LlmReasoningWorker.java       # Claude API integration
│           ├── LlmAnalysisTaskDefinition.java
│           ├── DocumentAnalysisWithLlmApp.java
│           └── AutonomousMonitoringWorker.java
├── casehub-flow-worker/         # Quarkus Flow integration (optional)
│   └── src/main/java/io/casehub/flow/
│       ├── FlowWorker.java      # Worker for Quarkus Flow workflows
│       ├── FlowWorkflowDefinition.java
│       ├── FlowExecutionContext.java
│       └── examples/
│           ├── DocumentProcessingWorkflow.java
│           └── FlowWorkerDemo.java
└── docs/                        # Documentation
```

---

## 💡 Examples

### Quick Demo (5 minutes)

```bash
cd casehub/src/main/java
javac io/casehub/examples/SimpleDocumentAnalysis.java
java io.casehub.examples.SimpleDocumentAnalysis
```

Shows core concepts with no dependencies.

### Real Implementation (10 minutes)

```bash
cd casehub-examples
mvn quarkus:dev
```

Demonstrates actual CaseHub APIs with Quarkus.

### LLM Worker Integration (15 minutes)

```bash
export ANTHROPIC_API_KEY=sk-ant-your-key
cd casehub-examples
mvn quarkus:dev
```

Shows dual execution model with Claude API Worker.

**See**: [`casehub-examples/src/main/java/io/casehub/examples/README.md`](casehub-examples/src/main/java/io/casehub/examples/README.md)

---

## 🎯 Use Cases

### ✅ Excellent Fit

- **Multi-Agent AI**: Multiple LLMs/agents collaborating on complex problems
- **Document Analysis**: OCR → NER → Classification → Risk → Summary
- **Fraud Detection**: Multiple detection algorithms working together
- **Medical Diagnosis**: Symptoms → Tests → Analysis → Diagnosis → Treatment
- **Scientific Workflows**: Experiment → Analysis → Validation → Report
- **Business Process Automation**: Dynamic workflows based on data

### ⚠️ Less Ideal

- Simple request-response (use Task model directly)
- Fixed linear pipelines (traditional workflow engine simpler)
- Real-time streaming (different architecture needed)
- Stateless processing (no shared workspace needed)

---

## 🔧 Configuration

Configuration via `application.properties`:

```properties
# Timeout configuration
casehub.timeout.check-interval=5s
casehub.timeout.default=5m

# Retry configuration
casehub.retry.ks.default.max-attempts=3
casehub.retry.ks.default.backoff=exponential
casehub.retry.ks.default.initial-delay=1s

# Worker configuration
casehub.worker.heartbeat-timeout=30s
casehub.worker.claim-timeout=10s

# Dead letter configuration
casehub.dead-letter.retention-days=7
```

See: [`casehub-core/src/main/resources/application.properties`](casehub-core/src/main/resources/application.properties)

---

## 📚 Documentation

- **[Design Document](design/CaseHub_Design_Document.md)**: Complete architecture specification
- **[Examples Guide](casehub/src/main/java/io/casehub/examples/README.md)**: Working examples and tutorials
- **[Worker Guide](casehub/src/main/java/io/casehub/examples/workers/README.md)**: LLM integration and Worker patterns
- **[Build Guide](design/CLAUDE.md)**: Development instructions and terminology
- **[Contributing](CONTRIBUTING.md)**: How to contribute

---

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

### Quick Links

- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Issue Tracker](../../issues)
- [Pull Requests](../../pulls)
- [Discussions](../../discussions)

---

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **Blackboard Architecture**: Hayes-Roth, Barbara (1985). "A Blackboard Architecture for Control." *Artificial Intelligence* 26.3
- **CMMN**: [OMG Case Management Model and Notation](https://www.omg.org/cmmn/)
- **CNCF Serverless Workflow**: Lifecycle states (PENDING, RUNNING, WAITING, etc.)
- **Quarkus**: [Supersonic Subatomic Java](https://quarkus.io/)

---

## 📞 Contact

- **Issues**: [GitHub Issues](../../issues)
- **Discussions**: [GitHub Discussions](../../discussions)
- **Email**: [Your contact email]

---

## 🗺️ Roadmap

- [x] Core CaseFile implementation
- [x] TaskDefinition registry and activation
- [x] CaseEngine control loop
- [x] Worker model with capability routing
- [x] Dual execution model integration
- [x] LLM Worker example (Claude API)
- [ ] Redis storage provider
- [ ] PostgreSQL storage provider
- [ ] Distributed lineage service
- [ ] Advanced PlanningStrategies
- [ ] WebSocket notifications
- [ ] REST API layer
- [ ] Admin UI

---

## ⭐ Star History

If you find CaseHub useful, please consider giving it a star! ⭐

---

**Built with ❤️ using Quarkus and the Blackboard Architecture pattern**
