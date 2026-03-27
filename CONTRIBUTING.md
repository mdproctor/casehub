# Contributing to CaseHub

First off, thank you for considering contributing to CaseHub! It's people like you that make CaseHub such a great tool for building agentic AI systems.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Documentation](#documentation)

---

## Code of Conduct

This project and everyone participating in it is governed by the [CaseHub Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

---

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check the existing issues as you might find that you don't need to create one. When you are creating a bug report, please include as many details as possible:

**Bug Report Template:**

```markdown
**Describe the bug**
A clear and concise description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Create a CaseFile with '...'
2. Register TaskDefinition '....'
3. Execute '....'
4. See error

**Expected behavior**
A clear and concise description of what you expected to happen.

**Environment:**
 - OS: [e.g. macOS, Linux, Windows]
 - Java Version: [e.g. 21]
 - Quarkus Version: [e.g. 3.17.5]
 - CaseHub Version: [e.g. 1.0.0]

**Additional context**
Add any other context about the problem here.
```

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, please include:

- **Use case**: Why is this enhancement useful?
- **Proposed solution**: How should it work?
- **Alternatives considered**: What other approaches did you consider?
- **Impact**: What components would this affect?

### Your First Code Contribution

Unsure where to begin? You can start by looking through issues labeled:

- `good-first-issue` - Issues which should only require a few lines of code
- `help-wanted` - Issues which need more involvement

### Pull Requests

1. Fork the repo and create your branch from `main`
2. If you've added code, add tests
3. If you've changed APIs, update the documentation
4. Ensure the test suite passes
5. Make sure your code follows the coding standards
6. Submit your pull request!

---

## Development Setup

### Prerequisites

- Java 21 or higher
- Maven 3.9+
- Git

### Clone and Build

```bash
# Clone your fork
git clone https://github.com/your-username/casehub.git
cd casehub

# Build
mvn clean compile

# Run tests
mvn test

# Run in dev mode
mvn quarkus:dev
```

### Project Structure

```
casehub/
├── src/main/java/io/casehub/
│   ├── core/           # Core CaseFile, TaskDefinition logic
│   ├── control/        # CasePlanModel, PlanningStrategy
│   ├── coordination/   # CaseEngine orchestration
│   ├── worker/         # Task model, Workers
│   ├── resilience/     # Retry, timeout, dead-letter
│   └── examples/       # Working examples
├── src/test/java/      # Tests
└── docs/               # Documentation
```

---

## Pull Request Process

1. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**
   - Write clear, focused commits
   - Follow the coding standards
   - Add/update tests
   - Update documentation

3. **Test thoroughly**
   ```bash
   mvn clean test
   mvn verify
   ```

4. **Commit with meaningful messages**
   ```bash
   git commit -m "Add support for custom PlanningStrategy

   - Implements PlanningStrategy interface
   - Adds unit tests for priority-based selection
   - Updates documentation in design doc

   Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
   ```

5. **Push to your fork**
   ```bash
   git push origin feature/your-feature-name
   ```

6. **Create Pull Request**
   - Use the PR template
   - Reference any related issues
   - Provide context for reviewers
   - Add screenshots/examples if applicable

7. **Address review feedback**
   - Make requested changes
   - Push updates to the same branch
   - Respond to comments

8. **Merge**
   - Once approved, a maintainer will merge your PR
   - Your branch will be deleted after merge

---

## Coding Standards

### Java Style

We follow standard Java conventions with some specific guidelines:

**Formatting:**
- Indentation: 4 spaces (no tabs)
- Line length: 120 characters max
- Opening braces on same line
- One statement per line

**Naming:**
- Classes: `PascalCase`
- Methods/Variables: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Packages: lowercase

**Example:**
```java
public class DefaultCaseFile implements CaseFile {
    private static final int DEFAULT_VERSION = 0;
    private final String caseFileId;

    public void put(String key, Object value) {
        // Implementation
    }
}
```

### CMMN Terminology

**Always use CMMN terms (not original Blackboard terms):**

| ✅ Use | ❌ Don't Use |
|--------|--------------|
| CaseFile | Board |
| TaskDefinition | KnowledgeSource |
| CasePlanModel | ControlBoard |
| PlanItem | KSAR |
| Worker | Executor |

### Comments and Documentation

**JavaDoc for public APIs:**
```java
/**
 * Creates a new CaseFile with the given initial state.
 *
 * @param caseType The type of case (e.g., "fraud-detection")
 * @param initialState Initial key-value pairs for the CaseFile
 * @return The newly created CaseFile
 * @throws CaseCreationException if creation fails
 */
public CaseFile createAndSolve(String caseType, Map<String, Object> initialState)
        throws CaseCreationException {
    // Implementation
}
```

**Inline comments for complex logic:**
```java
// Try to assign a worker based on capabilities
Optional<Worker> worker = taskScheduler.selectWorker(task);
if (worker.isPresent()) {
    // Worker found - assign and update status
    task.setAssignedWorkerId(worker.get().getWorkerId());
    taskRegistry.updateStatus(task.getTaskId(), TaskStatus.ASSIGNED);
}
```

---

## Testing Guidelines

### Test Organization

```
src/test/java/io/casehub/
├── core/               # Unit tests for core components
├── integration/        # Integration tests
└── examples/           # Example tests
```

### Writing Tests

**Unit Test Example:**
```java
@QuarkusTest
class CaseFileTest {

    @Test
    void shouldStoreDAndRetrieveValue() {
        CaseFile caseFile = new DefaultCaseFile("test-case", "test-type", Map.of(), null);

        caseFile.put("key", "value");

        Optional<String> result = caseFile.get("key", String.class);
        assertTrue(result.isPresent());
        assertEquals("value", result.get());
    }

    @Test
    void shouldDetectVersionConflict() {
        CaseFile caseFile = new DefaultCaseFile("test-case", "test-type", Map.of(), null);

        caseFile.put("key", "value");
        long version = caseFile.getVersion();

        // Concurrent update
        caseFile.put("key", "new-value");

        // This should fail due to version mismatch
        assertThrows(StaleVersionException.class, () ->
            caseFile.putIfVersion("key", "conflicting-value", version)
        );
    }
}
```

**Integration Test Example:**
```java
@QuarkusTest
class DocumentAnalysisIntegrationTest {

    @Inject
    CaseEngine caseEngine;

    @Inject
    TaskDefinitionRegistry registry;

    @Test
    void shouldCompleteDocumentAnalysisWorkflow() {
        // Register TaskDefinitions
        registry.register(new TextExtractionTaskDefinition(), Set.of("doc-analysis"));
        registry.register(new EntityRecognitionTaskDefinition(), Set.of("doc-analysis"));
        registry.register(new RiskAnalysisTaskDefinition(), Set.of("doc-analysis"));

        // Create case
        Map<String, Object> initialState = Map.of(
            "raw_documents", List.of("Test document")
        );
        CaseFile caseFile = caseEngine.createAndSolve("doc-analysis", initialState);

        // Wait for completion
        caseEngine.awaitCompletion(caseFile, Duration.ofMinutes(1));

        // Verify results
        assertTrue(caseFile.contains("extracted_text"));
        assertTrue(caseFile.contains("entities"));
        assertTrue(caseFile.contains("risk_assessment"));
        assertEquals(CaseStatus.WAITING, caseFile.getStatus());
    }
}
```

### Test Coverage

- Aim for >80% code coverage
- All public APIs must have tests
- Critical paths must have integration tests
- Edge cases and error conditions must be tested

### Running Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=CaseFileTest

# Integration tests only
mvn verify -Pintegration-tests

# With coverage
mvn clean test jacoco:report
```

---

## Documentation

### Code Documentation

- All public classes, interfaces, and methods must have JavaDoc
- Complex algorithms should have explanatory comments
- Use `@see`, `@link` for cross-references

### User Documentation

When adding features:

1. **Update design document**: `CaseHub_Design_Document.md`
2. **Update examples**: Add/modify examples if applicable
3. **Update README**: If changing public APIs or adding major features
4. **Add example code**: Show how to use new features

### Documentation Style

```markdown
# Feature Name

Brief description of the feature.

## Use Case

When and why would you use this?

## Example

\```java
// Clear, runnable example
CaseFile caseFile = caseEngine.createAndSolve(...);
\```

## Configuration

Any configuration options.

## Limitations

Known limitations or caveats.
```

---

## Additional Notes

### Commit Messages

Good commit messages help understand changes:

```
Short summary (50 chars or less)

More detailed explanatory text, if necessary. Wrap at 72 characters.
The blank line separating the summary from the body is critical.

- Bullet points are fine
- Use imperative mood ("Add feature" not "Added feature")
- Reference issues/PRs where appropriate (#123)

Co-Authored-By: Name <email>
```

### Branch Naming

- `feature/description` - New features
- `fix/description` - Bug fixes
- `docs/description` - Documentation only
- `refactor/description` - Code refactoring
- `test/description` - Adding/fixing tests

---

## Questions?

Don't hesitate to ask questions:

- **GitHub Discussions**: For general questions
- **GitHub Issues**: For specific bugs/features
- **Pull Request Comments**: For code-specific questions

---

## Recognition

Contributors are recognized in:
- GitHub contributors page
- Release notes
- Special thanks in documentation

Thank you for contributing to CaseHub! 🎉
