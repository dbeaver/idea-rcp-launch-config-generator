package org.jkiss.tools.rcplaunchconfig.maven.model;

import java.util.Objects;

public record MavenDependency(String group, String name, String version) {
    public static MavenDependency fromCoordinates(String coordinates) {
        String[] parts = coordinates.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid Maven coordinates: " + coordinates);
        }
        return new MavenDependency(parts[0], parts[1], parts[2]);
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
}
