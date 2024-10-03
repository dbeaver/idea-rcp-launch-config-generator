package org.jkiss.tools.rcplaunchconfig.util;

import org.jkiss.code.Nullable;
import org.jkiss.utils.Pair;

public class VersionRange extends Pair<Version, Version> {
    private final boolean includingFirst;
    private final boolean includingSecond;

    public VersionRange(Version first, Version second, boolean includingFirst, boolean includingSecond) {
        super(first, second);
        this.includingFirst = includingFirst;
        this.includingSecond = includingSecond;
    }
    @Nullable
    public static VersionRange fromString(String range) {
        if (range == null || "0.0.0".equals(range)) {
            return null;
        }
        if (range.contains("(") || range.contains("[")) {
            boolean includingFirst = range.startsWith("[");
            boolean includingSecond = range.endsWith("]");
            String[] versions = range.substring(1, range.length() - 1).split(",");
            Version first = null, second = null;
            if (!versions[0].trim().isEmpty()) {
                first = new Version(versions[0].trim());
            }
            if (!versions[1].trim().isEmpty()) {
                second = new Version(versions[1].trim());
            }
            return new VersionRange(first, second, includingFirst, includingSecond);
        } else {
            Version version = new Version(range);
            return new VersionRange(version, null, true, true);
        }
    }

    public boolean versionIsSuitable(Version version) {
        boolean isValid = true;
        if (getFirst() != null) {
            int comparisonWithFirst = version.compareTo(getFirst());
            isValid &= comparisonWithFirst > 0 | (includingFirst && comparisonWithFirst == 0);
        }
        if (getSecond() != null) {
            int comparisonWithSecond = version.compareTo(getSecond());
            isValid &= comparisonWithSecond < 0 | (includingSecond && comparisonWithSecond == 0);
        }
        return isValid;
    }

    public static boolean isVersionsCompatible(VersionRange versionRange, Version version) {
        if (versionRange != null && version != null) {
            return versionRange.versionIsSuitable(version);
        } else {
            return true;
        }
    }
}
