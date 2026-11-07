/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2026 DBeaver Corp and others
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
package org.jkiss.tools.rcplaunchconfig.maven.processors;

import org.jkiss.code.NotNull;
import org.jkiss.tools.rcplaunchconfig.maven.model.MavenDependency;
import org.jkiss.tools.rcplaunchconfig.maven.registry.MavenLocalArtifactRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MavenPomProcessorInheritanceTest {
    @TempDir
    Path tempDir;

    @Test
    void opensEachPomOnlyOnceDuringGeneration() throws Exception {
        Path parentPom = tempDir.resolve("pom.xml");
        Files.writeString(parentPom, "<project><groupId>test</groupId><artifactId>parent</artifactId><version>1</version></project>");
        Path child = childPom("child", "");
        MavenPomProcessor.startGeneration();
        try {
            MavenPomProcessor.collectArtifacts(child);
            MavenPomProcessor.collectArtifacts(child);
            MavenPomProcessor.processDependencies(child);
            MavenPomProcessor.processDependencies(child);
            assertEquals(1, MavenPomProcessor.getPomOpenCount(child.resolve("pom.xml")));
            assertEquals(1, MavenPomProcessor.getPomOpenCount(parentPom));
        } finally {
            MavenPomProcessor.finishGeneration();
        }
    }

    @Test
    void inheritsSameTestFrameworkWithAndWithoutDirectTestDependencies() throws Exception {
        MavenDependency junit = providedDependency("junit-jupiter-engine", "1", "test");
        MavenDependency sqlite = providedDependency("sqlite-jdbc", "1", "test");
        Files.writeString(tempDir.resolve("pom.xml"), """
            <project>
                <properties><test.version>1</test.version></properties>
                <dependencyManagement><dependencies>
                    <dependency><groupId>test.inheritance</groupId><artifactId>junit-jupiter-engine</artifactId>
                        <version>${test.version}</version></dependency>
                </dependencies></dependencyManagement>
                <dependencies>
                    <dependency><groupId>test.inheritance</groupId><artifactId>junit-jupiter-engine</artifactId>
                        <scope>test</scope></dependency>
                </dependencies>
            </project>
            """);
        Path libsql = childPom("libsql", "");
        Path d1 = childPom("d1", """
            <dependencies>
                <dependency><groupId>test.inheritance</groupId><artifactId>sqlite-jdbc</artifactId>
                    <version>1</version><scope>test</scope></dependency>
            </dependencies>
            """);

        assertEquals(List.of(junit), MavenPomProcessor.processDependencies(libsql));
        assertEquals(List.of(sqlite, junit), MavenPomProcessor.processDependencies(d1));
    }

    @Test
    void childDependencyOverridesInheritedDependency() throws Exception {
        providedDependency("override", "1", "test");
        MavenDependency override = providedDependency("override", "2", "compile");
        Files.writeString(tempDir.resolve("pom.xml"), """
            <project><dependencies>
                <dependency><groupId>test.inheritance</groupId><artifactId>override</artifactId>
                    <version>1</version><scope>test</scope></dependency>
            </dependencies></project>
            """);
        Path child = childPom("child", """
            <dependencies>
                <dependency><groupId>test.inheritance</groupId><artifactId>override</artifactId>
                    <version>2</version><scope>compile</scope></dependency>
            </dependencies>
            """);
        assertEquals(List.of(override), MavenPomProcessor.processDependencies(child));
    }

    @NotNull
    private Path childPom(@NotNull String name, @NotNull String content) throws Exception {
        Path child = Files.createDirectories(tempDir.resolve(name));
        Files.writeString(child.resolve("pom.xml"), """
            <project>
                <parent><groupId>test.inheritance</groupId><artifactId>parent</artifactId>
                    <version>1</version><relativePath>../pom.xml</relativePath></parent>
                %s
            </project>
            """.formatted(content));
        return child;
    }

    @NotNull
    private MavenDependency providedDependency(@NotNull String name, @NotNull String version, @NotNull String scope) {
        MavenDependency dependency = new MavenDependency("test.inheritance", name, version, List.of(), scope);
        MavenLocalArtifactRegistry.INSTANCE.addProvidedDependency(dependency, tempDir.resolve(name));
        return dependency;
    }
}
