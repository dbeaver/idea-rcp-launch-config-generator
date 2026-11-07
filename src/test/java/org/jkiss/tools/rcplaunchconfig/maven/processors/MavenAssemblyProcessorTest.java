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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenAssemblyProcessorTest {
    @TempDir
    Path module;

    @Test
    void restoresDescriptorAndManifestLibrariesWithoutPackaging() throws Exception {
        Files.createDirectories(module.resolve("META-INF"));
        Files.writeString(module.resolve("META-INF/MANIFEST.MF"), "Manifest-Version: 1.0\nBundle-ClassPath: .,lib/transitive.jar\n\n");
        Files.writeString(module.resolve("descriptor.xml"), """
            <assembly><dependencySets><dependencySet>
              <includes><include>test:direct</include></includes>
              <excludes><exclude>test:excluded</exclude></excludes>
            </dependencySet></dependencySets></assembly>
            """);
        List<MavenDependency> dependencies = List.of(
            dependency("direct"), dependency("transitive"), dependency("excluded"), dependency("unused")
        );
        for (MavenDependency dependency : dependencies) {
            Path jar = module.resolve(dependency.name() + "-1.jar");
            Files.write(jar, new byte[]{1});
            MavenLocalArtifactRegistry.INSTANCE.addLocalThirdPartyDependency(dependency, jar);
        }

        MavenAssemblyProcessor.prepare(module, dependencies);

        assertTrue(Files.isRegularFile(module.resolve("lib/direct.jar")));
        assertTrue(Files.isRegularFile(module.resolve("lib/transitive.jar")));
        assertFalse(Files.exists(module.resolve("lib/excluded.jar")));
        assertFalse(Files.exists(module.resolve("lib/unused.jar")));
    }

    private static @NotNull MavenDependency dependency(@NotNull String name) {
        return new MavenDependency("test", name, "1", List.of());
    }
}
