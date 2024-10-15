package org.jkiss.tools.rcplaunchconfig.util;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

import java.io.IOException;

public class DependencyGraphStub extends DependencyGraph {
    @Override
    public void setCurrentNode(DependencyNode root) {

    }

    @Override
    public DependencyNode traverseIntoNode(@NotNull String nodeName) {
        return null;
    }

    @Override
    public DependencyNode getCurrentNode() {
        return null;
    }

    @Override
    public void printDependencyTree(@Nullable DependencyNode name) throws IOException {

    }

    @Override
    @Nullable
    public DependencyNode addCurrentNodeDependencyAndTraverse(@NotNull String bundleName) {
        return null;
    }

    @Override
    public void addCurrentNodeDependency(@NotNull String bundleName) {

    }

    @Override
    public void addDependency(@NotNull String from, @NotNull String to) {

    }

    @Override
    public void addImportDependency(@NotNull String from, @NotNull String to) {

    }
}
