/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.tools.rcplaunchconfig.util;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.utils.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class DependencyGraph {

    public abstract void setCurrentNode(@Nullable DependencyNode root);

    public abstract DependencyNode traverseIntoNode(@NotNull String nodeName);

    public abstract DependencyNode getCurrentNode();

    public abstract void printDependencyTree(DependencyNode name) throws IOException;

    @Nullable
    public abstract DependencyNode addCurrentNodeDependencyAndTraverse(@NotNull String dependencyName);

    public abstract void addCurrentNodeDependency(@NotNull String dependencyName);

    public abstract void addDependency(@NotNull String from, @NotNull String to);

    public abstract void addImportDependency(@NotNull String from, @NotNull String to);

    public static class DependencyNode {
        private final String name;
        private final List<Pair<DependencyNode, DependencyType>> dependencies;
        private boolean visited;

        public DependencyNode(@NotNull String name) {
            this.name = name;
            this.dependencies = new ArrayList<>();
            this.visited = false;
        }

        public String getName() {
            return name;
        }

        public List<Pair<DependencyNode, DependencyType>> getDependencies() {
            return dependencies;
        }

        public void addBundleDependency(@NotNull DependencyNode dependency) {
            if (dependencies.stream().anyMatch(it -> it.getFirst().getName().equals(dependency.getName()))) {
                return;
            }
            this.dependencies.add(new Pair<>(dependency, DependencyType.DIRECT_DEPENDENCY));
        }

        public void addImportDependency(@NotNull DependencyNode dependency) {
            if (dependencies.stream().anyMatch(it -> it.getFirst().getName().equals(dependency.getName()))) {
                return;
            }
            this.dependencies.add(new Pair<>(dependency, DependencyType.BUNDLE_IMPORT));
        }

        public boolean isVisited() {
            return visited;
        }

        public void setImported(boolean visited) {
            this.visited = visited;
        }

        public enum DependencyType {
            DIRECT_DEPENDENCY,
            BUNDLE_IMPORT
        }
    }
}
