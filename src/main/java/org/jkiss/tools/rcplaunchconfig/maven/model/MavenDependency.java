package org.jkiss.tools.rcplaunchconfig.maven.model;

import org.jkiss.code.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MavenDependency {
    private final String group;
    private final String name;
    private final String version;
    private final List<MavenDependency> exclusions = new ArrayList<>();

    public MavenDependency(@NotNull String group, @NotNull String name, String version) {
        this.group = group;
        this.name = name;
        this.version = version;
    }

    public static MavenDependency fromCoordinates(String coordinates) {
        String[] parts = coordinates.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid Maven coordinates: " + coordinates);
        }
        return new MavenDependency(parts[0], parts[1], parts[2]);
    }

    public void addExclusion(@NotNull MavenDependency exclusion) {
        exclusions.add(exclusion);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MavenDependency that = (MavenDependency) o;
        return Objects.equals(group, that.group) && Objects.equals(name, that.name) && Objects.equals(
            version,
            that.version
        );
    }


    public String getCoordinates() {
        return group + ":" + name + ":" + version;
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, name, version);
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public List<MavenDependency> getExclusions() {
        return exclusions;
    }

    @Override
    public String toString() {
        return "MavenDependency[" +
            "group=" + group + ", " +
            "name=" + name + ", " +
            "version=" + version + ", " +
            "exclusions=" + exclusions + ']';
    }

}
