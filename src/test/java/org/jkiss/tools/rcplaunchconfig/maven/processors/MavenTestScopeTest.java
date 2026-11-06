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
import org.jkiss.tools.rcplaunchconfig.maven.download.MavenArtifactDownloader;
import org.jkiss.tools.rcplaunchconfig.maven.model.MavenDependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenTestScopeTest {
    @TempDir
    Path repository;

    @Test
    void includesTransitiveTestDependencies() throws Exception {
        artifact("api", "");
        artifact("engine", """
            <dependencies><dependency>
                <groupId>test.scope</groupId><artifactId>api</artifactId><version>1</version>
            </dependency></dependencies>
            """);
        var dependencies = MavenArtifactDownloader.resolve(List.of(
            new MavenDependency("test.scope", "engine", "1", List.of(), "test")
        ), List.of(new MavenDependency("test.scope", "api", "1", List.of())),
            Set.of("compile", "runtime", "test"), repository.toString(), List.of(), false);
        assertEquals(2, dependencies.size());
        assertTrue(dependencies.stream().allMatch(it -> it.getFirst().isTestScope()), dependencies.toString());
    }

    private void artifact(@NotNull String name, @NotNull String dependencies) throws Exception {
        Path directory = Files.createDirectories(repository.resolve("test/scope/" + name + "/1"));
        Files.write(directory.resolve(name + "-1.jar"), new byte[0]);
        Files.writeString(directory.resolve(name + "-1.pom"), """
            <project><modelVersion>4.0.0</modelVersion>
                <groupId>test.scope</groupId><artifactId>%s</artifactId><version>1</version>
                %s
            </project>
            """.formatted(name, dependencies));
    }
}
