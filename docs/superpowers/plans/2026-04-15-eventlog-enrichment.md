# EventLog Enrichment — ContextDiffStrategy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enrich `WORKER_EXECUTION_COMPLETED` EventLog entries with a key-level before/after diff of CaseContext changes, via a pluggable `ContextDiffStrategy` SPI.

**Architecture:** A `ContextDiffStrategy` interface in `api` is injected into `WorkflowExecutionCompletedHandler`. Before applying worker output, the handler snapshots the context; after applying, it calls the strategy and stores the result as `contextChanges` in the EventLog metadata. Three implementations live in `engine`: `TopLevelContextDiffStrategy` (default), `JsonPatchContextDiffStrategy` (@Alternative), and `NoOpContextDiffStrategy` (@Alternative). Users switch via `quarkus.arc.selected-alternatives`.

**Tech Stack:** Java 17, Quarkus 3.31, Panache Reactive, Vert.x EventBus, zjsonpatch (already in engine), Jackson, JUnit 5, AssertJ, Awaitility.

---

## Branch Setup

```bash
cd /Users/mdproctor/dev/casehub-engine
git checkout feat/rename-binding-casedefinition
git checkout -b feat/casehub-engine/eventlog-enrichment
```

This branches independently of the resilience PRs — EventLog enrichment only touches `api/` and `engine/`.

---

## File Map

**New files:**
- `api/src/main/java/io/casehub/api/spi/ContextDiffStrategy.java` — SPI interface
- `engine/src/main/java/io/casehub/engine/internal/diff/TopLevelContextDiffStrategy.java` — default: per-key before/after object
- `engine/src/main/java/io/casehub/engine/internal/diff/JsonPatchContextDiffStrategy.java` — @Alternative: RFC 6902 array
- `engine/src/main/java/io/casehub/engine/internal/diff/NoOpContextDiffStrategy.java` — @Alternative: returns null, omits contextChanges
- `engine/src/test/java/io/casehub/engine/internal/diff/TopLevelContextDiffStrategyTest.java`
- `engine/src/test/java/io/casehub/engine/internal/diff/JsonPatchContextDiffStrategyTest.java`
- `engine/src/test/java/io/casehub/engine/internal/diff/NoOpContextDiffStrategyTest.java`
- `engine/src/test/java/io/casehub/engine/ContextDiffEndToEndTest.java`

**Modified files:**
- `engine/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java` — inject strategy, snapshot before, enrich metadata

---

## Task 1: ContextDiffStrategy SPI + NoOpContextDiffStrategy

The no-op is the simplest implementation — good first red-green cycle.

**Files:**
- Create: `api/src/main/java/io/casehub/api/spi/ContextDiffStrategy.java`
- Create: `engine/src/main/java/io/casehub/engine/internal/diff/NoOpContextDiffStrategy.java`
- Create: `engine/src/test/java/io/casehub/engine/internal/diff/NoOpContextDiffStrategyTest.java`

- [ ] **Step 1: Write the failing test**

Create `engine/src/test/java/io/casehub/engine/internal/diff/NoOpContextDiffStrategyTest.java`:

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.diff;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class NoOpContextDiffStrategyTest {

  private final NoOpContextDiffStrategy strategy = new NoOpContextDiffStrategy();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void alwaysReturnsNull_forEmptyNodes() {
    assertThat(strategy.compute(MAPPER.createObjectNode(), MAPPER.createObjectNode())).isNull();
  }

  @Test
  void alwaysReturnsNull_forNonEmptyNodes() {
    ObjectNode before = MAPPER.createObjectNode();
    before.put("status", "processing");
    ObjectNode after = MAPPER.createObjectNode();
    after.put("status", "done");

    assertThat(strategy.compute(before, after)).isNull();
  }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
cd /Users/mdproctor/dev/casehub-engine
/opt/homebrew/bin/mvn test -pl engine -Dtest=NoOpContextDiffStrategyTest 2>&1 | grep -E "ERROR|cannot find"
```

Expected: `cannot find symbol` for `NoOpContextDiffStrategy`.

- [ ] **Step 3: Create the SPI interface**

Create `api/src/main/java/io/casehub/api/spi/ContextDiffStrategy.java`:

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.api.spi;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * SPI: computes the diff between CaseContext state before and after a worker execution.
 *
 * <p>The result is stored as {@code contextChanges} in the {@code WORKER_EXECUTION_COMPLETED}
 * EventLog metadata. Return {@code null} to omit {@code contextChanges} entirely.
 *
 * <p>Three implementations are provided in the engine module:
 * <ul>
 *   <li>{@code TopLevelContextDiffStrategy} — default; per-key before/after object</li>
 *   <li>{@code JsonPatchContextDiffStrategy} — @Alternative; RFC 6902 array</li>
 *   <li>{@code NoOpContextDiffStrategy} — @Alternative; returns null, no overhead</li>
 * </ul>
 *
 * Switch via {@code quarkus.arc.selected-alternatives} in {@code application.properties}.
 */
public interface ContextDiffStrategy {

  /**
   * Computes the diff between the CaseContext before and after a worker execution.
   *
   * @param before CaseContext state snapshotted before {@code setAll(output)} was called
   * @param after  CaseContext state after {@code setAll(output)} was called
   * @return diff node to store as {@code contextChanges}, or {@code null} to omit the field
   */
  JsonNode compute(JsonNode before, JsonNode after);
}
```

- [ ] **Step 4: Create NoOpContextDiffStrategy**

Create `engine/src/main/java/io/casehub/engine/internal/diff/NoOpContextDiffStrategy.java`:

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.diff;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.api.spi.ContextDiffStrategy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * No-op {@link ContextDiffStrategy}: skips diff computation entirely. {@code contextChanges} is
 * omitted from {@code WORKER_EXECUTION_COMPLETED} metadata. Use when diff overhead is unwanted.
 *
 * <p>Activate via:
 * <pre>quarkus.arc.selected-alternatives=io.casehub.engine.internal.diff.NoOpContextDiffStrategy</pre>
 */
@Alternative
@Priority(10)
@ApplicationScoped
public class NoOpContextDiffStrategy implements ContextDiffStrategy {

  @Override
  public JsonNode compute(JsonNode before, JsonNode after) {
    return null;
  }
}
```

- [ ] **Step 5: Install api, run test — expect GREEN**

```bash
/opt/homebrew/bin/mvn install -pl api -q && \
/opt/homebrew/bin/mvn test -pl engine -Dtest=NoOpContextDiffStrategyTest 2>&1 | grep -E "Tests run|BUILD"
```

Expected: `Tests run: 2, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add api/src/main/java/io/casehub/api/spi/ContextDiffStrategy.java \
        engine/src/main/java/io/casehub/engine/internal/diff/NoOpContextDiffStrategy.java \
        engine/src/test/java/io/casehub/engine/internal/diff/NoOpContextDiffStrategyTest.java
git commit -m "feat(engine): add ContextDiffStrategy SPI + NoOpContextDiffStrategy"
```

---

## Task 2: TopLevelContextDiffStrategy

Default implementation. Per changed top-level key: `{ "before": X, "after": Y }`. Added keys omit `before`; removed keys omit `after`. Unchanged keys are absent.

**Files:**
- Create: `engine/src/main/java/io/casehub/engine/internal/diff/TopLevelContextDiffStrategy.java`
- Create: `engine/src/test/java/io/casehub/engine/internal/diff/TopLevelContextDiffStrategyTest.java`

- [ ] **Step 1: Write all failing unit tests**

Create `engine/src/test/java/io/casehub/engine/internal/diff/TopLevelContextDiffStrategyTest.java`:

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.diff;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

/** Pure unit tests — no CDI, no Quarkus. TopLevelContextDiffStrategy is a stateless utility. */
class TopLevelContextDiffStrategyTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final TopLevelContextDiffStrategy strategy = new TopLevelContextDiffStrategy();

  // ---- Added keys -----------------------------------------------------------

  @Test
  void addedKey_hasAfterOnly_noBeforeField() {
    ObjectNode after = MAPPER.createObjectNode();
    after.put("status", "done");

    JsonNode diff = strategy.compute(MAPPER.createObjectNode(), after);

    assertThat(diff.has("status")).isTrue();
    assertThat(diff.get("status").get("after").asText()).isEqualTo("done");
    assertThat(diff.get("status").has("before")).isFalse();
  }

  @Test
  void addedObjectKey_capturesFullValue() {
    ObjectNode afterDoc = MAPPER.createObjectNode();
    afterDoc.put("id", "doc-1");
    afterDoc.put("title", "Test");
    ObjectNode after = MAPPER.createObjectNode();
    after.set("doc", afterDoc);

    JsonNode diff = strategy.compute(MAPPER.createObjectNode(), after);

    assertThat(diff.get("doc").get("after").get("id").asText()).isEqualTo("doc-1");
    assertThat(diff.get("doc").has("before")).isFalse();
  }

  // ---- Updated keys ---------------------------------------------------------

  @Test
  void updatedKey_hasBeforeAndAfter() {
    ObjectNode before = MAPPER.createObjectNode();
    before.put("status", "processing");
    ObjectNode after = MAPPER.createObjectNode();
    after.put("status", "done");

    JsonNode diff = strategy.compute(before, after);

    assertThat(diff.get("status").get("before").asText()).isEqualTo("processing");
    assertThat(diff.get("status").get("after").asText()).isEqualTo("done");
  }

  @Test
  void updatedNumericKey_capturesNumericValues() {
    ObjectNode before = MAPPER.createObjectNode();
    before.put("score", 10);
    ObjectNode after = MAPPER.createObjectNode();
    after.put("score", 99);

    JsonNode diff = strategy.compute(before, after);

    assertThat(diff.get("score").get("before").asInt()).isEqualTo(10);
    assertThat(diff.get("score").get("after").asInt()).isEqualTo(99);
  }

  // ---- Removed keys ---------------------------------------------------------

  @Test
  void removedKey_hasBeforeOnly_noAfterField() {
    ObjectNode before = MAPPER.createObjectNode();
    before.put("tempKey", "old-value");

    JsonNode diff = strategy.compute(before, MAPPER.createObjectNode());

    assertThat(diff.has("tempKey")).isTrue();
    assertThat(diff.get("tempKey").get("before").asText()).isEqualTo("old-value");
    assertThat(diff.get("tempKey").has("after")).isFalse();
  }

  // ---- Unchanged keys -------------------------------------------------------

  @Test
  void unchangedKey_isOmittedFromDiff() {
    ObjectNode before = MAPPER.createObjectNode();
    before.put("status", "done");
    ObjectNode after = MAPPER.createObjectNode();
    after.put("status", "done");

    JsonNode diff = strategy.compute(before, after);

    assertThat(diff.has("status")).isFalse();
  }

  @Test
  void mixedContext_onlyChangedKeysAppear() {
    ObjectNode before = MAPPER.createObjectNode();
    before.put("status", "processing");
    before.put("unchanged", "same");

    ObjectNode after = MAPPER.createObjectNode();
    after.put("status", "done");
    after.put("unchanged", "same");

    JsonNode diff = strategy.compute(before, after);

    assertThat(diff.has("status")).isTrue();
    assertThat(diff.has("unchanged")).isFalse();
  }

  // ---- No changes -----------------------------------------------------------

  @Test
  void noChanges_returnsEmptyObject() {
    ObjectNode both = MAPPER.createObjectNode();
    both.put("status", "done");
    both.put("result", "ok");

    JsonNode diff = strategy.compute(both, both);

    assertThat(diff.size()).isZero();
    assertThat(diff.isObject()).isTrue();
  }

  @Test
  void bothEmpty_returnsEmptyObject() {
    JsonNode diff = strategy.compute(MAPPER.createObjectNode(), MAPPER.createObjectNode());

    assertThat(diff.size()).isZero();
  }

  // ---- Multiple simultaneous changes ----------------------------------------

  @Test
  void multipleChanges_allCaptured() {
    ObjectNode before = MAPPER.createObjectNode();
    before.put("status", "processing");
    before.put("toRemove", "bye");

    ObjectNode after = MAPPER.createObjectNode();
    after.put("status", "done");
    after.put("newKey", "hello");

    JsonNode diff = strategy.compute(before, after);

    // updated
    assertThat(diff.get("status").get("before").asText()).isEqualTo("processing");
    assertThat(diff.get("status").get("after").asText()).isEqualTo("done");
    // added
    assertThat(diff.get("newKey").get("after").asText()).isEqualTo("hello");
    assertThat(diff.get("newKey").has("before")).isFalse();
    // removed
    assertThat(diff.get("toRemove").get("before").asText()).isEqualTo("bye");
    assertThat(diff.get("toRemove").has("after")).isFalse();
  }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

```bash
/opt/homebrew/bin/mvn test -pl engine -Dtest=TopLevelContextDiffStrategyTest 2>&1 | grep -E "ERROR|cannot find"
```

Expected: `cannot find symbol` for `TopLevelContextDiffStrategy`.

- [ ] **Step 3: Implement TopLevelContextDiffStrategy**

Create `engine/src/main/java/io/casehub/engine/internal/diff/TopLevelContextDiffStrategy.java`:

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.diff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.api.spi.ContextDiffStrategy;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Default {@link ContextDiffStrategy}: records a before/after entry per changed top-level key.
 *
 * <p>Output format per key:
 * <ul>
 *   <li>Added: {@code { "after": value }} — no {@code before} field</li>
 *   <li>Updated: {@code { "before": oldValue, "after": newValue }}</li>
 *   <li>Removed: {@code { "before": oldValue }} — no {@code after} field</li>
 *   <li>Unchanged: omitted entirely</li>
 * </ul>
 *
 * <p>Operates on top-level keys only. Nested changes within an object value are captured as
 * a whole-object before/after, not path-by-path. Use {@link JsonPatchContextDiffStrategy} for
 * full path precision.
 */
@ApplicationScoped
public class TopLevelContextDiffStrategy implements ContextDiffStrategy {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public JsonNode compute(JsonNode before, JsonNode after) {
    ObjectNode changes = MAPPER.createObjectNode();

    // Added or updated keys — present in `after`
    after.fieldNames()
        .forEachRemaining(
            key -> {
              JsonNode afterVal = after.get(key);
              JsonNode beforeVal = before.get(key);
              if (!afterVal.equals(beforeVal)) {
                ObjectNode change = MAPPER.createObjectNode();
                if (beforeVal != null && !beforeVal.isMissingNode()) {
                  change.set("before", beforeVal);
                }
                change.set("after", afterVal);
                changes.set(key, change);
              }
            });

    // Removed keys — present in `before` but absent in `after`
    before.fieldNames()
        .forEachRemaining(
            key -> {
              if (!after.has(key)) {
                ObjectNode change = MAPPER.createObjectNode();
                change.set("before", before.get(key));
                changes.set(key, change);
              }
            });

    return changes;
  }
}
```

- [ ] **Step 4: Run tests — expect GREEN**

```bash
/opt/homebrew/bin/mvn test -pl engine -Dtest=TopLevelContextDiffStrategyTest 2>&1 | grep -E "Tests run|BUILD"
```

Expected: `Tests run: 10, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add engine/src/main/java/io/casehub/engine/internal/diff/TopLevelContextDiffStrategy.java \
        engine/src/test/java/io/casehub/engine/internal/diff/TopLevelContextDiffStrategyTest.java
git commit -m "feat(engine): add TopLevelContextDiffStrategy — default per-key before/after diff"
```

---

## Task 3: JsonPatchContextDiffStrategy

RFC 6902 JSON Patch output using `zjsonpatch` (already on the engine classpath).

**Files:**
- Create: `engine/src/main/java/io/casehub/engine/internal/diff/JsonPatchContextDiffStrategy.java`
- Create: `engine/src/test/java/io/casehub/engine/internal/diff/JsonPatchContextDiffStrategyTest.java`

- [ ] **Step 1: Write the failing tests**

Create `engine/src/test/java/io/casehub/engine/internal/diff/JsonPatchContextDiffStrategyTest.java`:

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.diff;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure unit tests — no CDI, no Quarkus. */
class JsonPatchContextDiffStrategyTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final JsonPatchContextDiffStrategy strategy = new JsonPatchContextDiffStrategy();

  @Test
  void addedKey_producesAddOperation() {
    ObjectNode after = MAPPER.createObjectNode();
    after.put("status", "done");

    JsonNode diff = strategy.compute(MAPPER.createObjectNode(), after);

    assertThat(diff.isArray()).isTrue();
    assertThat(findOp(diff, "add", "/status")).isNotNull();
    assertThat(findOp(diff, "add", "/status").get("value").asText()).isEqualTo("done");
  }

  @Test
  void updatedKey_producesReplaceOperation() {
    ObjectNode before = MAPPER.createObjectNode();
    before.put("status", "processing");
    ObjectNode after = MAPPER.createObjectNode();
    after.put("status", "done");

    JsonNode diff = strategy.compute(before, after);

    assertThat(findOp(diff, "replace", "/status")).isNotNull();
    assertThat(findOp(diff, "replace", "/status").get("value").asText()).isEqualTo("done");
  }

  @Test
  void removedKey_producesRemoveOperation() {
    ObjectNode before = MAPPER.createObjectNode();
    before.put("tempKey", "bye");

    JsonNode diff = strategy.compute(before, MAPPER.createObjectNode());

    assertThat(findOp(diff, "remove", "/tempKey")).isNotNull();
  }

  @Test
  void noChanges_producesEmptyArray() {
    ObjectNode both = MAPPER.createObjectNode();
    both.put("status", "done");

    JsonNode diff = strategy.compute(both, both);

    assertThat(diff.isArray()).isTrue();
    assertThat(diff.size()).isZero();
  }

  @Test
  void bothEmpty_producesEmptyArray() {
    JsonNode diff = strategy.compute(MAPPER.createObjectNode(), MAPPER.createObjectNode());

    assertThat(diff.isArray()).isTrue();
    assertThat(diff.size()).isZero();
  }

  @Test
  void nestedChange_producesPathWithSlashSeparator() {
    ObjectNode beforeDoc = MAPPER.createObjectNode();
    beforeDoc.put("status", "draft");
    ObjectNode before = MAPPER.createObjectNode();
    before.set("doc", beforeDoc);

    ObjectNode afterDoc = MAPPER.createObjectNode();
    afterDoc.put("status", "published");
    ObjectNode after = MAPPER.createObjectNode();
    after.set("doc", afterDoc);

    JsonNode diff = strategy.compute(before, after);

    // JSON Patch records /doc/status as the changed path
    assertThat(findOp(diff, "replace", "/doc/status")).isNotNull();
  }

  /** Finds the first operation matching op type and path, or null. */
  private JsonNode findOp(JsonNode patch, String op, String path) {
    for (JsonNode node : patch) {
      if (op.equals(node.path("op").asText()) && path.equals(node.path("path").asText())) {
        return node;
      }
    }
    return null;
  }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

```bash
/opt/homebrew/bin/mvn test -pl engine -Dtest=JsonPatchContextDiffStrategyTest 2>&1 | grep -E "ERROR|cannot find"
```

Expected: `cannot find symbol` for `JsonPatchContextDiffStrategy`.

- [ ] **Step 3: Implement JsonPatchContextDiffStrategy**

Create `engine/src/main/java/io/casehub/engine/internal/diff/JsonPatchContextDiffStrategy.java`:

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.diff;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.api.spi.ContextDiffStrategy;
import io.fabric8.zjsonpatch.JsonDiff;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * RFC 6902 JSON Patch {@link ContextDiffStrategy} using {@code zjsonpatch}.
 *
 * <p>Produces a JSON array of patch operations ({@code add}, {@code replace}, {@code remove}).
 * Records changes at every affected path, including nested keys — more precise than
 * {@link TopLevelContextDiffStrategy} but less readable for humans.
 *
 * <p>Useful as a foundation for future replay support (issues #10–#13).
 *
 * <p>Activate via:
 * <pre>quarkus.arc.selected-alternatives=io.casehub.engine.internal.diff.JsonPatchContextDiffStrategy</pre>
 */
@Alternative
@Priority(10)
@ApplicationScoped
public class JsonPatchContextDiffStrategy implements ContextDiffStrategy {

  @Override
  public JsonNode compute(JsonNode before, JsonNode after) {
    return JsonDiff.asJson(before, after);
  }
}
```

- [ ] **Step 4: Run tests — expect GREEN**

```bash
/opt/homebrew/bin/mvn test -pl engine -Dtest=JsonPatchContextDiffStrategyTest 2>&1 | grep -E "Tests run|BUILD"
```

Expected: `Tests run: 6, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add engine/src/main/java/io/casehub/engine/internal/diff/JsonPatchContextDiffStrategy.java \
        engine/src/test/java/io/casehub/engine/internal/diff/JsonPatchContextDiffStrategyTest.java
git commit -m "feat(engine): add JsonPatchContextDiffStrategy — RFC 6902 @Alternative"
```

---

## Task 4: Wire Strategy into WorkflowExecutionCompletedHandler + E2E Test

Modify the handler to snapshot before, compute diff after, and enrich metadata. Write an E2E test that starts a real case and asserts the EventLog entry contains the expected `contextChanges`.

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java`
- Create: `engine/src/test/java/io/casehub/engine/ContextDiffEndToEndTest.java`

- [ ] **Step 1: Write the failing E2E test**

Create `engine/src/test/java/io/casehub/engine/ContextDiffEndToEndTest.java`:

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.casehub.api.engine.CaseHub;
import io.casehub.api.model.Binding;
import io.casehub.api.model.Capability;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.CaseStatus;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.Goal;
import io.casehub.api.model.GoalExpression;
import io.casehub.api.model.GoalKind;
import io.casehub.api.model.Worker;
import io.casehub.engine.internal.engine.cache.CaseInstanceCache;
import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventLog;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test: verifies that WORKER_EXECUTION_COMPLETED EventLog entries contain a
 * {@code contextChanges} field reflecting the before/after state of CaseContext keys modified
 * by the worker.
 */
@QuarkusTest
class ContextDiffEndToEndTest {

  @Inject DiffCaseHub diffCase;
  @Inject UpdateCaseHub updateCase;
  @Inject CaseInstanceCache caseInstanceCache;
  @Inject Vertx vertx;

  /**
   * Happy path: initial context has "status"="start". Worker writes "status"="done" and
   * adds "result"="ok". EventLog must record status as updated, result as added.
   */
  @Test
  void workerOutput_isReflectedInContextChanges() {
    AtomicReference<UUID> caseIdRef = new AtomicReference<>();

    diffCase.startCase(Map.of("status", "start"))
        .thenAccept(caseIdRef::set)
        .toCompletableFuture()
        .join();

    UUID caseId = caseIdRef.get();

    // Wait for COMPLETED
    await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
      var instance = caseInstanceCache.get(caseId);
      assertThat(instance).isNotNull();
      assertThat(instance.getState()).isEqualTo(CaseStatus.COMPLETED);
    });

    // Query the WORKER_EXECUTION_COMPLETED EventLog entry
    EventLog completedEvent = fetchCompletedEvent(caseId);
    assertThat(completedEvent).isNotNull();

    var metadata = completedEvent.getMetadata();
    assertThat(metadata.has("contextChanges")).isTrue();

    var changes = metadata.get("contextChanges");

    // "status" was updated: before="start", after="done"
    assertThat(changes.has("status")).isTrue();
    assertThat(changes.get("status").get("before").asText()).isEqualTo("start");
    assertThat(changes.get("status").get("after").asText()).isEqualTo("done");

    // "result" was added: no before, after="ok"
    assertThat(changes.has("result")).isTrue();
    assertThat(changes.get("result").has("before")).isFalse();
    assertThat(changes.get("result").get("after").asText()).isEqualTo("ok");
  }

  /**
   * Keys present in initial context that the worker does NOT touch must be absent from
   * contextChanges.
   */
  @Test
  void unchangedKeys_areAbsentFromContextChanges() {
    AtomicReference<UUID> caseIdRef = new AtomicReference<>();

    // Start with two keys; worker only touches "status"
    updateCase.startCase(Map.of("status", "start", "unchanged", "keep-me"))
        .thenAccept(caseIdRef::set)
        .toCompletableFuture()
        .join();

    UUID caseId = caseIdRef.get();

    await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
      var instance = caseInstanceCache.get(caseId);
      assertThat(instance).isNotNull();
      assertThat(instance.getState()).isEqualTo(CaseStatus.COMPLETED);
    });

    EventLog completedEvent = fetchCompletedEvent(caseId);
    var changes = completedEvent.getMetadata().get("contextChanges");

    assertThat(changes.has("status")).isTrue();
    assertThat(changes.has("unchanged")).isFalse();
  }

  /**
   * The inputDataHash must still be present alongside contextChanges.
   */
  @Test
  void inputDataHash_remainsPresentAlongsideContextChanges() {
    AtomicReference<UUID> caseIdRef = new AtomicReference<>();
    diffCase.startCase(Map.of("status", "start"))
        .thenAccept(caseIdRef::set)
        .toCompletableFuture()
        .join();

    await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
      var instance = caseInstanceCache.get(caseIdRef.get());
      assertThat(instance).isNotNull();
      assertThat(instance.getState()).isEqualTo(CaseStatus.COMPLETED);
    });

    EventLog completedEvent = fetchCompletedEvent(caseIdRef.get());
    var metadata = completedEvent.getMetadata();

    assertThat(metadata.has("inputDataHash")).isTrue();
    assertThat(metadata.get("inputDataHash").asText()).isNotBlank();
    assertThat(metadata.has("contextChanges")).isTrue();
  }

  // ---- Helper ---------------------------------------------------------------

  private EventLog fetchCompletedEvent(UUID caseId) {
    return io.casehub.engine.internal.util.ReactiveUtils.runOnSafeVertxContext(
            vertx,
            () ->
                Panache.withSession(
                    () ->
                        EventLog.<EventLog>find(
                                "caseId = ?1 and eventType = ?2",
                                caseId,
                                CaseHubEventType.WORKER_EXECUTION_COMPLETED)
                            .firstResult()))
        .await()
        .atMost(java.time.Duration.ofSeconds(5));
  }

  // ---- CaseHub beans --------------------------------------------------------

  /** Worker writes status="done" and adds result="ok". */
  @ApplicationScoped
  public static class DiffCaseHub extends CaseHub {

    private final Capability capability =
        Capability.builder()
            .name("doWork")
            .inputSchema("{ status: .status }")
            .outputSchema("{ status: .status, result: .result }")
            .build();

    private final Goal goal =
        Goal.builder().name("done").condition(".status == \"done\"").kind(GoalKind.SUCCESS).build();

    @Override
    public CaseDefinition getDefinition() {
      return CaseDefinition.builder()
          .namespace("test-context-diff")
          .name("Context Diff Case")
          .version("1.0.0")
          .capabilities(capability)
          .workers(
              Worker.builder()
                  .name("diff-worker")
                  .capabilities(capability)
                  .function(input -> Map.of("status", "done", "result", "ok"))
                  .build())
          .bindings(
              Binding.builder()
                  .name("trigger")
                  .capability(capability)
                  .on(new ContextChangeTrigger(".status == \"start\""))
                  .build())
          .goals(goal)
          .completion(GoalExpression.allOf(goal))
          .build();
    }
  }

  /** Worker writes only status="done"; leaves "unchanged" key untouched. */
  @ApplicationScoped
  public static class UpdateCaseHub extends CaseHub {

    private final Capability capability =
        Capability.builder()
            .name("partialUpdate")
            .inputSchema("{ status: .status }")
            .outputSchema("{ status: .status }")
            .build();

    private final Goal goal =
        Goal.builder().name("done").condition(".status == \"done\"").kind(GoalKind.SUCCESS).build();

    @Override
    public CaseDefinition getDefinition() {
      return CaseDefinition.builder()
          .namespace("test-context-diff-partial")
          .name("Partial Update Case")
          .version("1.0.0")
          .capabilities(capability)
          .workers(
              Worker.builder()
                  .name("partial-worker")
                  .capabilities(capability)
                  .function(input -> Map.of("status", "done"))
                  .build())
          .bindings(
              Binding.builder()
                  .name("trigger")
                  .capability(capability)
                  .on(new ContextChangeTrigger(".status == \"start\""))
                  .build())
          .goals(goal)
          .completion(GoalExpression.allOf(goal))
          .build();
    }
  }
}
```

- [ ] **Step 2: Run test — expect it fails (contextChanges not yet in metadata)**

```bash
/opt/homebrew/bin/mvn test -pl engine -Dtest=ContextDiffEndToEndTest 2>&1 | grep -E "Tests run|FAIL|BUILD"
```

Expected: `Failures: 3` — `contextChanges` field not found in metadata.

- [ ] **Step 3: Modify WorkflowExecutionCompletedHandler**

Replace the full file content of `engine/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java`:

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.engine.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.api.model.Worker;
import io.casehub.api.spi.ContextDiffStrategy;
import io.casehub.engine.internal.event.CaseContextChangedEvent;
import io.casehub.engine.internal.event.EventBusAddresses;
import io.casehub.engine.internal.event.WorkflowExecutionCompleted;
import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.internal.history.EventStreamType;
import io.casehub.engine.internal.model.CaseInstance;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WorkflowExecutionCompletedHandler {

  @Inject EventBus eventBus;
  @Inject ContextDiffStrategy contextDiffStrategy;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Logger LOG = Logger.getLogger(CaseStartedEventHandler.class);

  @ConsumeEvent(value = EventBusAddresses.WORKER_EXECUTION_FINISHED)
  public Uni<Void> onWorkflowExecutionCompletedHandler(WorkflowExecutionCompleted event) {
    final CaseInstance caseInstance = event.caseInstance();
    final Worker worker = event.worker();
    final Map<String, Object> rawOutput = event.output() == null ? Map.of() : event.output();
    final Instant now = Instant.now();

    // Snapshot context BEFORE applying worker output — used for diff computation
    final JsonNode contextBefore = caseInstance.getCaseContext().asJsonNode();

    return Panache.withTransaction(
            () -> {
              caseInstance.getCaseContext().setAll(rawOutput);
              JsonNode contextAfter = caseInstance.getCaseContext().asJsonNode();

              JsonNode diff = contextDiffStrategy.compute(contextBefore, contextAfter);
              EventLog eventLog = buildEventLog(caseInstance, worker, rawOutput, event.idempotency(), now, diff);

              return eventLog.persist().replaceWith(contextAfter);
            })
        .invoke(
            contextSnapshot ->
                eventBus.publish(
                    EventBusAddresses.CONTEXT_CHANGED,
                    new CaseContextChangedEvent(caseInstance, contextSnapshot)))
        .replaceWithVoid()
        .onFailure()
        .invoke(
            t ->
                LOG.error(
                    "Failed to handle WorkflowExecutionCompleted event for caseId: "
                        + caseInstance.getUuid(),
                    t));
  }

  private EventLog buildEventLog(
      CaseInstance caseInstance,
      Worker worker,
      Map<String, Object> output,
      String idempotency,
      Instant timestamp,
      JsonNode contextDiff) {
    final EventLog eventLog = new EventLog();
    eventLog.setCaseId(caseInstance.getUuid());
    eventLog.setWorkerId(worker.getName());
    eventLog.setStreamType(EventStreamType.CASE);
    eventLog.setTimestamp(timestamp);
    eventLog.setEventType(CaseHubEventType.WORKER_EXECUTION_COMPLETED);
    eventLog.setPayload(OBJECT_MAPPER.valueToTree(output == null ? Map.of() : output));
    eventLog.setMetadata(buildMetadata(idempotency, contextDiff));
    return eventLog;
  }

  private JsonNode buildMetadata(String idempotency, JsonNode contextDiff) {
    ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
    metadata.put("inputDataHash", idempotency);
    if (contextDiff != null) {
      metadata.set("contextChanges", contextDiff);
    }
    return metadata;
  }
}
```

- [ ] **Step 4: Run E2E tests — expect GREEN**

```bash
/opt/homebrew/bin/mvn install -pl api -q && \
/opt/homebrew/bin/mvn test -pl engine -Dtest=ContextDiffEndToEndTest 2>&1 | grep -E "Tests run|BUILD"
```

Expected: `Tests run: 3, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 5: Run full engine test suite — all existing tests must still pass**

```bash
/opt/homebrew/bin/mvn test -pl engine 2>&1 | grep -E "Tests run|BUILD" | tail -5
```

Expected: `Tests run: 331+, Failures: 0` and `BUILD SUCCESS`. (Count increases by the new unit tests from Tasks 1–3, which also run here.)

- [ ] **Step 6: Commit**

```bash
git add engine/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java \
        engine/src/test/java/io/casehub/engine/ContextDiffEndToEndTest.java
git commit -m "feat(engine): enrich WORKER_EXECUTION_COMPLETED with contextChanges diff"
```

---

## Task 5: Create GitHub Issue + Push PR

- [ ] **Step 1: Create GitHub issue**

```bash
gh issue create \
  --title "Add ContextDiffStrategy SPI — enrich WORKER_EXECUTION_COMPLETED with key-level diff" \
  --label "enhancement" \
  --repo casehubio/engine \
  --body "$(cat <<'EOF'
## Context

Part of Phase 2 EventLog enrichment. When a worker completes, the engine currently records only
the raw output payload in WORKER_EXECUTION_COMPLETED. There is no record of which CaseContext
keys changed, or what values they held before.

## What

Add a \`ContextDiffStrategy\` SPI (api module) injected into \`WorkflowExecutionCompletedHandler\`.
Before applying worker output, snapshot the context; after applying, compute and store the diff
as \`contextChanges\` in the EventLog metadata.

Three implementations in engine:
- \`TopLevelContextDiffStrategy\` — default; per-key { before, after } object
- \`JsonPatchContextDiffStrategy\` — @Alternative; RFC 6902 array (foundation for future replay)
- \`NoOpContextDiffStrategy\` — @Alternative; omits contextChanges (zero overhead)

## Acceptance Criteria
- [ ] contextChanges appears in WORKER_EXECUTION_COMPLETED metadata for every worker completion
- [ ] Added keys: after only; updated: before + after; removed: before only; unchanged: omitted
- [ ] inputDataHash still present alongside contextChanges
- [ ] NoOpContextDiffStrategy omits contextChanges entirely
- [ ] JsonPatchContextDiffStrategy produces RFC 6902 array
- [ ] All existing 331 engine tests still pass
EOF
)"
```

- [ ] **Step 2: Push branch and create PR**

```bash
git push upstream feat/casehub-engine/eventlog-enrichment
git push origin feat/casehub-engine/eventlog-enrichment

gh pr create \
  --title "feat(engine): enrich WORKER_EXECUTION_COMPLETED with ContextDiffStrategy" \
  --base feat/rename-binding-casedefinition \
  --head feat/casehub-engine/eventlog-enrichment \
  --repo casehubio/engine \
  --body "$(cat <<'EOF'
## Summary

Closes #<ISSUE_NUMBER>

- **`ContextDiffStrategy` SPI** (api) — pluggable diff computation injected into `WorkflowExecutionCompletedHandler`
- **`TopLevelContextDiffStrategy`** — default; records `{ "before": X, "after": Y }` per changed top-level key; added keys omit `before`, removed keys omit `after`
- **`JsonPatchContextDiffStrategy`** — @Alternative; RFC 6902 JSON Patch array via zjsonpatch (already on classpath); foundation for future replay (#10–#13)
- **`NoOpContextDiffStrategy`** — @Alternative; returns null, omits `contextChanges` entirely

Switch strategy via `quarkus.arc.selected-alternatives` — same pattern as `PoisonPillWorkerExecutionGuard`.

## Test plan

- [ ] Unit tests: `TopLevelContextDiffStrategyTest` (10 cases: add/update/remove/unchanged/multi/empty)
- [ ] Unit tests: `JsonPatchContextDiffStrategyTest` (6 cases: add/replace/remove/empty/nested)
- [ ] Unit tests: `NoOpContextDiffStrategyTest` (2 cases: always null)
- [ ] E2E `@QuarkusTest`: real case run → WORKER_EXECUTION_COMPLETED has expected contextChanges
- [ ] All 331 existing engine tests pass
EOF
)"
```

---

## Self-Review

**Spec coverage:**
- ✅ `ContextDiffStrategy` SPI in `api` — Task 1
- ✅ `TopLevelContextDiffStrategy` default — Task 2
- ✅ `JsonPatchContextDiffStrategy` @Alternative — Task 3
- ✅ `NoOpContextDiffStrategy` @Alternative — Task 1
- ✅ Handler wired: snapshot before, diff after, enrich metadata — Task 4
- ✅ `inputDataHash` preserved alongside `contextChanges` — Task 4 test
- ✅ Removed key: `before` only, no `after` — Task 2 test + spec rules
- ✅ No schema migration — confirmed (metadata is JSONB, flexible)
- ✅ Unit + integration + E2E coverage — Tasks 1–4

**Type consistency:** `ContextDiffStrategy.compute(JsonNode, JsonNode)` used consistently across all tasks. `contextDiff` parameter flows from handler call through `buildMetadata`. No naming mismatches.

**No placeholders:** All code is complete and runnable.
