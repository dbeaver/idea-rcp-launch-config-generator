package org.jkiss.tools.rcplaunchconfig.maven.model;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record MavenDependency(@NotNull String group, @NotNull String name, @Nullable String version, @NotNull List<MavenDependency> exclusions) {

    public static MavenDependency fromCoordinates(String coordinates) {
        String[] parts = coordinates.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid Maven coordinates: " + coordinates);
        }
        return new MavenDependency(parts[0], parts[1], parts[2], new ArrayList<>());
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

    @NotNull
    public String getGroup() {
        return group;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @Nullable
    public String getVersion() {
        return version;
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
