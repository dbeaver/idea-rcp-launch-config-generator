package org.jkiss.tools.rcplaunchconfig.registry;

import com.dbeaver.osgi.dependency.processing.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.model.MavenDependency;

import java.util.HashMap;
import java.util.Map;

public class MavenLocalArtifactRegistry {
    private final Map<MavenDependency, BundleInfo> providedDependencies = new HashMap<>();
    public static final MavenLocalArtifactRegistry INSTANCE = new MavenLocalArtifactRegistry();

    public void addProvidedDependency(MavenDependency dependency, BundleInfo path) {
        providedDependencies.put(dependency, path);
    }

    public BundleInfo getProvidedDependencyPath(MavenDependency dependency) {
        return providedDependencies.get(dependency);
    }

    public boolean isProvided(MavenDependency dependency) {
        return providedDependencies.containsKey(dependency);
    }

    private MavenLocalArtifactRegistry() {
    }
}
