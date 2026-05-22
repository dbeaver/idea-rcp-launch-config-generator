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

import org.jkiss.tools.rcplaunchconfig.maven.model.MavenDependency;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MavenLocalArtifactRegistry {
    private final Map<ArtifactKey, Path> providedDependencies = new HashMap<>();
    private final Map<ArtifactKey, Path> localThirdPartyDependencies = new HashMap<>();
    public static final MavenLocalArtifactRegistry INSTANCE = new MavenLocalArtifactRegistry();

    public void addProvidedDependency(MavenDependency dependency, Path path) {
        providedDependencies.put(ArtifactKey.of(dependency), path);
    }

    public void addLocalThirdPartyDependency(MavenDependency dependency, Path pathToJar) {
        localThirdPartyDependencies.put(ArtifactKey.of(dependency), pathToJar);
    }


    public Path getProvidedDependencyPath(MavenDependency dependency) {
        return providedDependencies.get(ArtifactKey.of(dependency));
    }

    public Path getDowloadedDependencyPath(MavenDependency dependency) {
        return localThirdPartyDependencies.get(ArtifactKey.of(dependency));
    }

    public boolean isProvided(MavenDependency dependency) {
        return providedDependencies.containsKey(ArtifactKey.of(dependency));
    }

    public boolean isLocalThirdParty(MavenDependency dependency) {
        return localThirdPartyDependencies.containsKey(ArtifactKey.of(dependency));
    }

    private MavenLocalArtifactRegistry() {
    }

    private record ArtifactKey(String group, String name, String version) {
        static ArtifactKey of(MavenDependency dependency) {
            return new ArtifactKey(dependency.group(), dependency.name(), dependency.version());
        }
    }
}
