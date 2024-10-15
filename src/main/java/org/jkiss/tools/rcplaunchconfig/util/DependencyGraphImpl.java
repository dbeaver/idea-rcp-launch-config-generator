package org.jkiss.tools.rcplaunchconfig.util;

import org.jkiss.code.NotNull;
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.producers.DevPropertiesProducer;
import org.jkiss.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public class DependencyGraphImpl extends DependencyGraph {
    private static final int BUFFER_THRESHOLD = 10_000; // Define a threshold for when to flush the buffer

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphImpl.class);
    private final Map<String, DependencyNode> nodes;
    private DependencyNode currentNode;

    public DependencyGraphImpl() {
        this.nodes = new HashMap<>();
    }

    // Add a node to the graph
    public DependencyNode addNode(String name) {
        return nodes.computeIfAbsent(name, DependencyNode::new);
    }

    public DependencyNode getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(DependencyNode currentNode) {
        this.currentNode = currentNode;
    }

    public DependencyNode traverseIntoNode(@NotNull String currentNode) {
        DependencyNode previousNode = this.currentNode;
        this.currentNode = addNode(currentNode);
        return previousNode;
    }

    // Add a dependency between two nodes
    public void addDependency(@NotNull String from, @NotNull String to) {
        DependencyNode fromNode = addNode(from);
        DependencyNode toNode = addNode(to);
        fromNode.addBundleDependency(toNode);
    }

    public void addImportDependency(@NotNull String from, @NotNull String to) {
        DependencyNode fromNode = addNode(from);
        DependencyNode toNode = addNode(to);
        fromNode.addImportDependency(toNode);
    }

    public DependencyNode addCurrentNodeDependencyAndTraverse(@NotNull String to) {
        addCurrentNodeDependency(to);
        return traverseIntoNode(to);
    }
    public void addCurrentNodeDependency(@NotNull String to) {
        DependencyNode node = addNode(to);
        currentNode.addBundleDependency(node);
    }

    // Depth-first traversal to print the dependency tree
    public void printDependencyTree(@NotNull DependencyNode startNode) throws IOException {
        log.info("Generating dependency tree for %s".formatted(startNode.getName()));
        Path treeOutputFolder = PathsManager.INSTANCE.getTreeOutputFolder();
        Files.createDirectories(treeOutputFolder);
        StringBuffer buffer = new StringBuffer();
        Path file = treeOutputFolder.resolve(startNode.getName().replace(".", "_").replace("/", "_").replace("\\", "_") + ".txt");
        if (Files.exists(file)) {
            Files.delete(file);
        }
        printNode(
            startNode,
            "",
            "",
            buffer,
            file
        );
        flushRemainingBuffer(buffer, file);  // Write any leftover content in the buffer
        log.info("Generation for %s complete".formatted(startNode));
    }

    // Recursive method to print each node and its dependencies
    private void printNode(DependencyNode node, String indent, String type, StringBuffer buffer, Path outputFile) throws IOException {
        if (node.isVisited()) {
            writeToBuffer(buffer, indent + type + node.getName() + " (already imported)\n", outputFile);
            return;
        }
        writeToBuffer(buffer, indent + type + node.getName() + "\n", outputFile);
        node.setImported(true);

        for (Pair<DependencyNode, DependencyNode.DependencyType> dep : node.getDependencies()) {
            String childType = dep.getSecond().equals(DependencyNode.DependencyType.DIRECT_DEPENDENCY) ? "  -> " : " -> (import)";
            if (DevPropertiesProducer.isBundleAcceptable(dep.getFirst().getName())) {
                printNode(dep.getFirst(), indent + " ", childType, buffer, outputFile);
            } else {
                writeToBuffer(buffer, indent + " " + childType + dep.getFirst().getName() + "\n", outputFile);
            }
        }
        node.setImported(false);
    }

    private void writeToBuffer(StringBuffer outputBuffer, String content, Path outputFile) throws IOException {
        outputBuffer.append(content);
        if (outputBuffer.length() > BUFFER_THRESHOLD) {
            flushBuffer(outputBuffer, outputFile);
        }
    }

    private void flushBuffer(StringBuffer outputBuffer, Path outputFile) throws IOException {
        Files.writeString(outputFile, outputBuffer.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        outputBuffer.setLength(0); // Clear the buffer after flushing
    }

    private void flushRemainingBuffer(StringBuffer outputBuffer, Path outputFile) throws IOException {
        if (!outputBuffer.isEmpty()) {
            flushBuffer(outputBuffer, outputFile); // Write any remaining content in the buffer
        }
    }

    // Clear visited flag for all nodes (for re-running traversal)
    public void clearVisited() {
        for (DependencyNode node : nodes.values()) {
            node.setImported(false);
        }
    }

}
