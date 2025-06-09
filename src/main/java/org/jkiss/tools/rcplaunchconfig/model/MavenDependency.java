package org.jkiss.tools.rcplaunchconfig.model;

import java.util.Objects;

public record MavenDependency(String group, String name, String version) {
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

    @Override
    public int hashCode() {
        return Objects.hash(group, name, version);
    }
}
