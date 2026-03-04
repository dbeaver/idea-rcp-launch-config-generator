package org.jkiss.tools.rcplaunchconfig.maven.model;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record MavenDependency(
    @NotNull String group,
    @NotNull String name,
    @Nullable String version,
    @NotNull List<MavenDependency> exclusions
) {
    public MavenDependency {
        exclusions = List.copyOf(exclusions);
    }

    public static MavenDependency fromCoordinates(String coordinates) {
        String[] parts = coordinates.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid Maven coordinates: " + coordinates);
        }
        return new MavenDependency(parts[0], parts[1], parts[2], new ArrayList<>());
    }

    public String getCoordinates() {
        return group + ":" + name + ":" + version;
    }

    @NotNull
    public List<MavenDependency> getExclusions() {
        return Collections.unmodifiableList(exclusions);
    }

    @NotNull
    @Override
    public String toString() {
        return "MavenDependency[" +
            "group=" + group + ", " +
            "name=" + name + ", " +
            "version=" + version + ", " +
            "exclusions=" + exclusions + ']';
    }

}
