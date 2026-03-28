# Running FlowWorker Demos

The casehub-flow-worker module provides **two demo applications** showing different approaches to Quarkus Flow integration with CaseHub.

## Demo Versions

### 1. FlowWorkerDemo (Standalone)
**Uses:** `DocumentProcessingWorkflow` (simple implementation)
**Runtime:** No Quarkus runtime required
**Execution:** Direct Java execution via Maven exec plugin

```bash
./run-demo.sh
```

Or directly:
```bash
mvn exec:java
```

**Pros:**
- Quick startup (~2 seconds)
- Simple execution
- No Quarkus dependency at runtime
- Easy debugging

**Cons:**
- No CDI features
- No Quarkus ecosystem integration
- Mock implementation only

---

### 2. FlowWorkerQuarkusDemo (Quarkus Runtime) ⚠️
**Status:** Code complete, but version compatibility issue
**Uses:** `QuarkusFlowDocumentWorkflow` (extends `io.quarkiverse.flow.Flow`)
**Runtime:** Full Quarkus runtime with CDI
**Execution:** Quarkus dev mode or native executable

⚠️ **Known Issue:** quarkus-flow 0.7.1 is incompatible with Quarkus 3.17.5
- Error: `ClassNotFoundException: io.quarkus.deployment.IsLocalDevelopment`
- **Workaround:** Use standalone demo (FlowWorkerDemo) which works perfectly
- **Alternative:** Downgrade to Quarkus 3.8.x or wait for quarkus-flow update

```bash
# Currently not working due to version mismatch
./run-quarkus-demo.sh
```

**Pros (when working):**
- Full CDI dependency injection
- Quarkus dev mode with hot reload
- Can compile to native executable
- Production-ready architecture
- Metrics, health checks, observability

**Cons:**
- Version compatibility issues with current Quarkus
- Slower startup (~5-10 seconds in dev mode)
- More complex setup
- Requires Quarkus runtime

---

## Quick Start

### Standalone Demo (Fast)
```bash
cd casehub-flow-worker
./run-demo.sh
```

Expected output:
```
╔════════════════════════════════════════════════════════════╗
║  CaseHub: Quarkus Flow Worker Demo                        ║
║  Programmatic API Execution                               ║
╚════════════════════════════════════════════════════════════╝

✓ Registered workflow: document-processing-flow
📤 Executing workflow...
  [1/4] Extracting text... ✓ Extracted 84 words
  [2/4] Recognizing entities... ✓ Found 7 entities
  [3/4] Analyzing sentiment... ✓ Sentiment: neutral
  [4/4] Generating summary... ✓ Summary generated
✓ Document processing workflow completed (duration: 1423ms)
```

### Quarkus Demo (Full Runtime)
```bash
cd casehub-flow-worker
./run-quarkus-demo.sh
```

Expected output:
```
╔════════════════════════════════════════════════════════════╗
║  CaseHub: Quarkus Flow Worker Demo (Quarkus Runtime)     ║
║  Full CDI Integration with @ApplicationScoped Beans       ║
╚════════════════════════════════════════════════════════════╝

🔧 Quarkus runtime initialized
✓ Injected QuarkusFlowDocumentWorkflow (extends Flow)
✓ Injected DocumentFunctions (CDI bean)
📤 Executing workflow with Quarkus Flow engine...
```

---

## What the Demos Show

Both demos execute a 4-step document processing workflow:

1. **Extract Text** - Extract text from document URL
2. **Recognize Entities** - Identify organizations, people, dates, money
3. **Analyze Sentiment** - Determine document sentiment (positive/neutral/negative)
4. **Generate Summary** - Create executive summary with key points

**Sample Output:**
- 📄 Extracted text: 84 words from contract
- 🏷️ Entities: 7 found (Acme Corporation, Global Tech Solutions, John Smith, etc.)
- 😊 Sentiment: neutral (score: 0.50, confidence: 0.85)
- 📋 Summary: Contract summary with key terms
- ℹ️ Metadata: Task ID, workflow ID, trace ID, processing time

---

## Implementation Comparison

| Feature | FlowWorkerDemo | FlowWorkerQuarkusDemo |
|---------|----------------|----------------------|
| **Workflow Class** | DocumentProcessingWorkflow | QuarkusFlowDocumentWorkflow |
| **Extends Flow** | ❌ No | ✅ Yes |
| **CDI Injection** | ❌ No | ✅ Yes (@Inject) |
| **Quarkus Runtime** | ❌ Not required | ✅ Required |
| **Startup Time** | ~2 seconds | ~5-10 seconds (dev mode) |
| **Hot Reload** | ❌ No | ✅ Yes (quarkus:dev) |
| **Native Build** | ❌ No | ✅ Yes |
| **Execution** | mvn exec:java | mvn quarkus:dev |
| **Use Case** | Quick testing | Production deployment |

---

## Manual Run

### Standalone Demo
If you prefer to build first:

```bash
# From project root
mvn clean compile -pl casehub-core,casehub-flow-worker

# Run the demo
cd casehub-flow-worker
mvn exec:java
```

### Quarkus Demo
```bash
# From project root
mvn clean compile -pl casehub-core,casehub-flow-worker

# Run with Quarkus
cd casehub-flow-worker
mvn quarkus:dev
```

---

## Production Deployment

### Quarkus JVM Mode
```bash
# Build JVM application
mvn clean package

# Run
java -jar target/quarkus-app/quarkus-run.jar
```

### Quarkus Native Mode
```bash
# Build native executable (requires GraalVM)
mvn clean package -Pnative

# Run
./target/casehub-flow-worker-1.0.0-SNAPSHOT-runner
```

---

## Troubleshooting

### Build Issues

If you get dependency resolution errors, make sure casehub-core is built first:

```bash
mvn clean install -pl casehub-core -DskipTests
```

### "Unsatisfied dependency" Errors

This means CDI beans are not being discovered. Solution:
1. Make sure `jandex-maven-plugin` is in casehub-core/pom.xml
2. Rebuild: `mvn clean compile -pl casehub-core`

### Quarkus Dev Mode Not Starting

Make sure only one @QuarkusMain exists. Check:
```bash
grep -r "@QuarkusMain" src/
```

Should show only `FlowWorkerQuarkusDemo.java`.

### ⚠️ quarkus-flow Version Compatibility Issue

**Problem:**
```
java.lang.ClassNotFoundException: io.quarkus.deployment.IsLocalDevelopment
```

**Root Cause:**
- quarkus-flow 0.7.1 (latest) is built for Quarkus 3.8.x
- casehub uses Quarkus 3.17.5
- Quarkus deployment API changed between versions

**Solutions:**

**Option 1: Use Standalone Demo (Recommended)**
```bash
./run-demo.sh
```
The standalone demo works perfectly and demonstrates the same programmatic API concepts.

**Option 2: Downgrade Quarkus Version**
Edit `pom.xml` (root):
```xml
<quarkus.platform.version>3.8.6</quarkus.platform.version>
```
Then rebuild and run:
```bash
mvn clean compile -pl casehub-core,casehub-flow-worker
./run-quarkus-demo.sh
```

**Option 3: Wait for quarkus-flow Update**
Monitor https://github.com/quarkiverse/quarkus-flow for compatibility updates.

**Why FlowWorkerQuarkusDemo Exists:**
Even though it doesn't currently run due to version issues, the code demonstrates:
1. How to structure a Quarkus application using QuarkusFlowDocumentWorkflow
2. CDI injection patterns for Flow workflows
3. @QuarkusMain integration
4. How production deployments would be structured

The standalone demo (FlowWorkerDemo) provides the same workflow execution without version constraints.

---

## Files

| File | Purpose | Runtime |
|------|---------|---------|
| **FlowWorkerDemo.java** | Standalone demo main class | None |
| **FlowWorkerQuarkusDemo.java** | Quarkus demo main class (@QuarkusMain) | Quarkus |
| **DocumentProcessingWorkflow.java** | Simple workflow (no Flow extension) | None |
| **QuarkusFlowDocumentWorkflow.java** | Production workflow (extends Flow) | Quarkus |
| **DocumentFunctions.java** | CDI bean with step implementations | Both |
| **run-demo.sh** | Run standalone demo | None |
| **run-quarkus-demo.sh** | Run Quarkus demo | Quarkus |

---

## Architecture Details

### Standalone (FlowWorkerDemo)
```
FlowWorkerDemo.main()
  └─> new DocumentProcessingWorkflow()
      └─> workflow.execute(context)
          └─> Manual function calls
              └─> extractText() → entities() → sentiment() → summary()
```

### Quarkus (FlowWorkerQuarkusDemo)
```
@QuarkusMain FlowWorkerQuarkusDemo.run()
  └─> @Inject QuarkusFlowDocumentWorkflow
      └─> extends io.quarkiverse.flow.Flow
          └─> descriptor() → FuncWorkflowBuilder
              └─> function("extractText", lambda).exportAs("...")
                  └─> @Inject DocumentFunctions
                      └─> Quarkus Flow execution engine
```

---

## Next Steps

**For Development:**
- Use standalone demo for quick testing
- Use Quarkus demo to test CDI integration

**For Production:**
- Implement real document processing functions
- Replace mock data with actual API calls
- Configure persistence (database for storage providers)
- Add authentication and authorization
- Set up metrics and monitoring
- Build native executable for optimal performance

**See Also:**
- [PROGRAMMATIC_API.md](PROGRAMMATIC_API.md) - Detailed API guide
- [PROGRAMMATIC_API_SUMMARY.md](PROGRAMMATIC_API_SUMMARY.md) - Migration summary
- [DEMO_SUCCESS.md](DEMO_SUCCESS.md) - What was fixed to get demos working
