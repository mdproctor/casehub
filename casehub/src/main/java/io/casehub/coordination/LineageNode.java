package io.casehub.coordination;

import java.time.Instant;
import java.util.Optional;

/**
 * Represents a single node (CaseFile or Task) in the lineage hierarchy. Each node carries span and
 * trace IDs for correlation, a {@link LineageNodeType} distinguishing CaseFiles from Tasks, the
 * underlying entity ID and type name, creation and completion timestamps, and current status.
 * Used by {@link LineageService} for hierarchy traversal. See section 5.3.
 */
public class LineageNode {
    private String spanId;
    private Optional<String> parentSpanId;
    private String traceId;
    private LineageNodeType type;
    private String typeId;
    private String typeName;
    private Instant createdAt;
    private Optional<Instant> completedAt;
    private Object status;

    /** Distinguishes the two kinds of nodes that can appear in a lineage hierarchy. */
    public enum LineageNodeType {
        CASE_FILE, TASK
    }

    public LineageNode() {
        this.parentSpanId = Optional.empty();
        this.completedAt = Optional.empty();
    }

    public LineageNode(String spanId, String traceId, LineageNodeType type,
                       String typeId, String typeName) {
        this();
        this.spanId = spanId;
        this.traceId = traceId;
        this.type = type;
        this.typeId = typeId;
        this.typeName = typeName;
        this.createdAt = Instant.now();
    }

    public String getSpanId() { return spanId; }
    public void setSpanId(String spanId) { this.spanId = spanId; }
    public Optional<String> getParentSpanId() { return parentSpanId; }
    public void setParentSpanId(Optional<String> parentSpanId) { this.parentSpanId = parentSpanId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public LineageNodeType getType() { return type; }
    public void setType(LineageNodeType type) { this.type = type; }
    public String getTypeId() { return typeId; }
    public void setTypeId(String typeId) { this.typeId = typeId; }
    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Optional<Instant> getCompletedAt() { return completedAt; }
    public void setCompletedAt(Optional<Instant> completedAt) { this.completedAt = completedAt; }
    public Object getStatus() { return status; }
    public void setStatus(Object status) { this.status = status; }
}
