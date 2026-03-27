package io.casehub.coordination;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Immutable, hierarchical context that flows from parent to child when Boards spawn child Boards
 * or KnowledgeSources submit Tasks. Carries a trace ID (correlating the entire hierarchy), a span
 * ID (identifying this node), a parent span ID, an ordered lineage path of ancestors, and inherited
 * attributes such as security principal and tenant ID. Also carries an optional resource budget
 * (deadline and remaining time); children cannot exceed their parent's budget. See section 5.2.
 */
public class PropagationContext {
    private final String traceId;
    private final String spanId;
    private final Optional<String> parentSpanId;
    private final List<String> lineagePath;
    private final Map<String, String> inheritedAttributes;
    private final Optional<Instant> deadline;
    private final Optional<Duration> remainingBudget;
    private final Instant createdAt;

    private PropagationContext(String traceId, String spanId, Optional<String> parentSpanId,
                               List<String> lineagePath, Map<String, String> inheritedAttributes,
                               Optional<Instant> deadline, Optional<Duration> remainingBudget) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.lineagePath = Collections.unmodifiableList(lineagePath);
        this.inheritedAttributes = Collections.unmodifiableMap(inheritedAttributes);
        this.deadline = deadline;
        this.remainingBudget = remainingBudget;
        this.createdAt = Instant.now();
    }

    public static PropagationContext createRoot() {
        return createRoot(Map.of());
    }

    public static PropagationContext createRoot(Map<String, String> attributes) {
        String traceId = UUID.randomUUID().toString();
        String spanId = UUID.randomUUID().toString();
        return new PropagationContext(traceId, spanId, Optional.empty(),
                List.of(), new HashMap<>(attributes), Optional.empty(), Optional.empty());
    }

    public static PropagationContext createRoot(Map<String, String> attributes, Duration budget) {
        String traceId = UUID.randomUUID().toString();
        String spanId = UUID.randomUUID().toString();
        Instant deadline = Instant.now().plus(budget);
        return new PropagationContext(traceId, spanId, Optional.empty(),
                List.of(), new HashMap<>(attributes), Optional.of(deadline), Optional.of(budget));
    }

    public PropagationContext createChild() {
        return createChild(Map.of());
    }

    public PropagationContext createChild(Map<String, String> additionalAttributes) {
        String childSpanId = UUID.randomUUID().toString();

        List<String> childLineage = new ArrayList<>(this.lineagePath);
        childLineage.add(this.spanId);

        Map<String, String> childAttributes = new HashMap<>(this.inheritedAttributes);
        childAttributes.putAll(additionalAttributes);

        Optional<Duration> childBudget = this.remainingBudget.map(budget -> {
            Duration elapsed = Duration.between(this.createdAt, Instant.now());
            Duration remaining = budget.minus(elapsed);
            return remaining.isNegative() ? Duration.ZERO : remaining;
        });

        return new PropagationContext(this.traceId, childSpanId, Optional.of(this.spanId),
                childLineage, childAttributes, this.deadline, childBudget);
    }

    public boolean isRoot() {
        return parentSpanId.isEmpty();
    }

    public int getDepth() {
        return lineagePath.size();
    }

    public boolean isBudgetExhausted() {
        if (deadline.isPresent() && Instant.now().isAfter(deadline.get())) {
            return true;
        }
        return remainingBudget.map(b -> b.isZero() || b.isNegative()).orElse(false);
    }

    public Optional<String> getAttribute(String key) {
        return Optional.ofNullable(inheritedAttributes.get(key));
    }

    // Getters
    public String getTraceId() { return traceId; }
    public String getSpanId() { return spanId; }
    public Optional<String> getParentSpanId() { return parentSpanId; }
    public List<String> getLineagePath() { return lineagePath; }
    public Map<String, String> getInheritedAttributes() { return inheritedAttributes; }
    public Optional<Instant> getDeadline() { return deadline; }
    public Optional<Duration> getRemainingBudget() { return remainingBudget; }
    public Instant getCreatedAt() { return createdAt; }
}
