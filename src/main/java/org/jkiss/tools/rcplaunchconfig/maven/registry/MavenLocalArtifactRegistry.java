package org.jkiss.tools.rcplaunchconfig.maven.registry;

import org.jkiss.tools.rcplaunchconfig.maven.model.MavenDependency;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MavenLocalArtifactRegistry {
    private final Map<MavenDependency, Path> providedDependencies = new HashMap<>();
    private final Map<MavenDependency, Path> localThirdPartyDependencies = new HashMap<>();
    public static final MavenLocalArtifactRegistry INSTANCE = new MavenLocalArtifactRegistry();

    public void addProvidedDependency(MavenDependency dependency, Path path) {
        providedDependencies.put(dependency, path);
    }

    public void addLocalThirdPartyDependency(MavenDependency dependency, Path pathToJar) {
        localThirdPartyDependencies.put(dependency, pathToJar);
    }


    public Path getProvidedDependencyPath(MavenDependency dependency) {
        return providedDependencies.get(dependency);
    }

    public Path getDowloadedDependencyPath(MavenDependency dependency) {
        return localThirdPartyDependencies.get(dependency);
    }

    public boolean isProvidedOrDownloaded(MavenDependency dependency) {
        return providedDependencies.containsKey(dependency) || localThirdPartyDependencies.containsKey(dependency);
    }

    private MavenLocalArtifactRegistry() {
    }
}
