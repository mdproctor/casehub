package io.casehub.coordination;

import io.casehub.core.spi.PropagationStorageProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Query API for navigating the CaseFile/Task lineage hierarchy. Supports ancestor traversal
 * ({@link #getLineage}), descendant traversal ({@link #getDescendants}), full tree construction
 * ({@link #getFullTree}), and trace-based lookup ({@link #findByTraceId}). Uses
 * {@link io.casehub.core.spi.PropagationStorageProvider} for persistence. See section 5.3.
 */
@ApplicationScoped
public class LineageService {

    @Inject
    PropagationStorageProvider propagationStorage;

    public List<LineageNode> getLineage(String spanId) {
        List<LineageNode> ancestors = new ArrayList<>();
        String currentSpanId = spanId;

        while (currentSpanId != null) {
            Optional<PropagationContext> ctx = propagationStorage.retrieveContext(currentSpanId);
            if (ctx.isEmpty()) {
                break;
            }
            ancestors.add(toLineageNode(ctx.get()));
            currentSpanId = ctx.get().getParentSpanId().orElse(null);
        }

        Collections.reverse(ancestors);
        return ancestors;
    }

    public List<LineageNode> getDescendants(String spanId) {
        List<LineageNode> descendants = new ArrayList<>();
        List<PropagationContext> children = propagationStorage.findByParentSpanId(spanId);

        for (PropagationContext child : children) {
            descendants.add(toLineageNode(child));
            descendants.addAll(getDescendants(child.getSpanId()));
        }

        return descendants;
    }

    public LineageTree getFullTree(String traceId) {
        List<PropagationContext> allContexts = propagationStorage.findByTraceId(traceId);

        Map<String, List<PropagationContext>> childrenByParent = allContexts.stream()
                .filter(ctx -> ctx.getParentSpanId().isPresent())
                .collect(Collectors.groupingBy(ctx -> ctx.getParentSpanId().get()));

        PropagationContext root = allContexts.stream()
                .filter(PropagationContext::isRoot)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No root context found for traceId: " + traceId));

        return buildSubtree(root, childrenByParent);
    }

    public List<LineageNode> findByTraceId(String traceId) {
        return propagationStorage.findByTraceId(traceId).stream()
                .map(this::toLineageNode)
                .collect(Collectors.toList());
    }

    private LineageTree buildSubtree(PropagationContext ctx, Map<String, List<PropagationContext>> childrenByParent) {
        LineageTree tree = new LineageTree(toLineageNode(ctx));
        List<PropagationContext> children = childrenByParent.getOrDefault(ctx.getSpanId(), List.of());

        for (PropagationContext child : children) {
            tree.addChild(buildSubtree(child, childrenByParent));
        }

        return tree;
    }

    private LineageNode toLineageNode(PropagationContext ctx) {
        LineageNode node = new LineageNode(
                ctx.getSpanId(),
                ctx.getTraceId(),
                LineageNode.LineageNodeType.CASE_FILE,
                ctx.getSpanId(),
                "unknown"
        );
        node.setParentSpanId(ctx.getParentSpanId());
        node.setCreatedAt(ctx.getCreatedAt());
        return node;
    }
}
