package io.casehub.coordination;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive tree structure representing the full hierarchy of Boards and Tasks sharing a common
 * trace ID. Provides depth calculation, total node count, and leaf node retrieval. Built by
 * {@link LineageService} from {@link io.casehub.core.spi.PropagationStorageProvider} data.
 * See section 5.3.
 */
public class LineageTree {
    private LineageNode root;
    private List<LineageTree> children;

    public LineageTree() {
        this.children = new ArrayList<>();
    }

    public LineageTree(LineageNode root) {
        this();
        this.root = root;
    }

    public int getDepth() {
        if (children.isEmpty()) {
            return 0;
        }
        return 1 + children.stream()
                .mapToInt(LineageTree::getDepth)
                .max()
                .orElse(0);
    }

    public int getTotalNodes() {
        return 1 + children.stream()
                .mapToInt(LineageTree::getTotalNodes)
                .sum();
    }

    public List<LineageNode> getLeaves() {
        List<LineageNode> leaves = new ArrayList<>();
        if (children.isEmpty()) {
            leaves.add(root);
        } else {
            for (LineageTree child : children) {
                leaves.addAll(child.getLeaves());
            }
        }
        return leaves;
    }

    public LineageNode getRoot() { return root; }
    public void setRoot(LineageNode root) { this.root = root; }
    public List<LineageTree> getChildren() { return children; }
    public void addChild(LineageTree child) { this.children.add(child); }
}
