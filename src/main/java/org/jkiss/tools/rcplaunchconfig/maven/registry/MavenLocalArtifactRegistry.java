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
package org.jkiss.tools.rcplaunchconfig.maven.registry;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.tools.rcplaunchconfig.maven.model.MavenDependency;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MavenLocalArtifactRegistry {
    private final Map<ArtifactKey, Path> providedDependencies = new ConcurrentHashMap<>();
    private final Map<ArtifactKey, Path> localThirdPartyDependencies = new ConcurrentHashMap<>();
    private final Map<ArtifactKey, Path> localPoms = new ConcurrentHashMap<>();
    public static final MavenLocalArtifactRegistry INSTANCE = new MavenLocalArtifactRegistry();

    public void addProvidedDependency(@NotNull MavenDependency dependency, @NotNull Path path) {
        addDeterministically(providedDependencies, dependency, path);
    }

    public void addLocalThirdPartyDependency(@NotNull MavenDependency dependency, @NotNull Path pathToJar) {
        addDeterministically(localThirdPartyDependencies, dependency, pathToJar);
    }

    public void addLocalPom(@NotNull MavenDependency dependency, @NotNull Path pathToPom) {
        addDeterministically(localPoms, dependency, pathToPom);
    }


    @Nullable
    public Path getProvidedDependencyPath(@NotNull MavenDependency dependency) {
        return providedDependencies.get(ArtifactKey.of(dependency));
    }

    @Nullable
    public Path getDowloadedDependencyPath(@NotNull MavenDependency dependency) {
        return localThirdPartyDependencies.get(ArtifactKey.of(dependency));
    }

    public boolean isProvided(@NotNull MavenDependency dependency) {
        return providedDependencies.containsKey(ArtifactKey.of(dependency));
    }

    public boolean isLocalThirdParty(@NotNull MavenDependency dependency) {
        return localThirdPartyDependencies.containsKey(ArtifactKey.of(dependency));
    }

    @Nullable
    public Path getLocalPomPath(@NotNull MavenDependency dependency) {
        return localPoms.get(ArtifactKey.of(dependency));
    }

    public boolean isLocalPom(@NotNull MavenDependency dependency) {
        return localPoms.containsKey(ArtifactKey.of(dependency));
    }

    private static void addDeterministically(
        @NotNull Map<ArtifactKey, Path> artifacts,
        @NotNull MavenDependency dependency,
        @NotNull Path path
    ) {
        artifacts.merge(ArtifactKey.of(dependency), path, (first, second) ->
            normalizedPath(first).compareTo(normalizedPath(second)) <= 0 ? first : second
        );
    }

    @NotNull
    private static String normalizedPath(@NotNull Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    public void reset() {
        providedDependencies.clear();
        localThirdPartyDependencies.clear();
        localPoms.clear();
    }

    private MavenLocalArtifactRegistry() {
    }

    private record ArtifactKey(@NotNull String group, @NotNull String name, @NotNull String version) {
        @NotNull
        static ArtifactKey of(@NotNull MavenDependency dependency) {
            return new ArtifactKey(dependency.group(), dependency.name(), dependency.version());
        }
    }
}
