/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp
 *
 * All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of DBeaver Corp and its suppliers, if any.
 * The intellectual and technical concepts contained
 * herein are proprietary to DBeaver Corp and its suppliers
 * and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from DBeaver Corp.
 */
package org.jkiss.tools.rcplaunchconfig;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class BundleInfo {

    public static String currentOS;
    public static String currentWS;
    public static String currentArch;

    static {
        String osName = System.getProperty("os.name", "generic");
        if (osName == null) {
            osName = "Windows";
        }
        osName = osName.toLowerCase(Locale.ENGLISH);

        String arch = System.getProperty("os.arch");
        if (arch == null || arch.equals("amd64")) {
            arch = "x86_64";
        } else if (arch.equals("arm64")) {
            arch = "aarch64";
        }

        BundleInfo.currentArch = arch;
        if ((osName.contains("mac")) || (osName.contains("darwin"))) {
            BundleInfo.currentOS = "macosx";
            BundleInfo.currentWS = "cocoa";
        } else if (osName.contains("win")) {
            BundleInfo.currentOS = "win32";
            BundleInfo.currentWS = "win32";
        } else {
            BundleInfo.currentOS = "linux";
            BundleInfo.currentWS = "gtk";
        }
    }

    private final Path path;
    private final String bundleName;
    private final String bundleVersion;
    private final List<String> classpathLibs;
    private final List<String> requireBundles;
    private final Set<String> exportPackages;
    private final Set<String> importPackages;
    private final Integer startLevel;
    // Additional versions is an exceptional case
    // E.g. jakarta.annotation-api of different versions (1.x and 2.x) are completely different and export different packages
    // Thus we need all versions
    private String additionalVersions;

    public BundleInfo(
        @Nonnull Path path,
        @Nonnull String bundleName,
        @Nonnull String bundleVersion,
        @Nonnull List<String> classpathLibs,
        @Nonnull List<String> requireBundles,
        @Nonnull Set<String> exportPackages,
        @Nonnull Set<String> importPackages,
        @Nullable Integer startLevel
    ) {
        this.path = path;
        this.bundleName = bundleName;
        this.bundleVersion = bundleVersion;
        this.classpathLibs = classpathLibs;
        this.requireBundles = requireBundles;
        this.exportPackages = exportPackages;
        this.importPackages = importPackages;
        this.startLevel = startLevel;
    }

    public @Nonnull Path getPath() {
        return path;
    }

    public @Nonnull String getBundleName() {
        return bundleName;
    }

    public @Nonnull String getBundleVersion() {
        return bundleVersion;
    }

    public @Nonnull List<String> getClasspathLibs() {
        return classpathLibs;
    }

    public @Nonnull List<String> getRequireBundles() {
        return requireBundles;
    }

    public @Nonnull Set<String> getExportPackages() {
        return exportPackages;
    }

    public @Nonnull Set<String> getImportPackages() {
        return importPackages;
    }

    public @Nullable Integer getStartLevel() {
        return startLevel;
    }

    @Nullable
    public String getAdditionalVersions() {
        return additionalVersions;
    }

    public void addAdditionalVersion(@Nonnull String version) {
        if (additionalVersions == null) {
            additionalVersions = version;
        } else {
            if (!additionalVersions.contains(version)) {
                additionalVersions += "," + version;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BundleInfo that = (BundleInfo) o;
        return Objects.equals(getPath(), that.getPath()) &&
            Objects.equals(getBundleName(), that.getBundleName()) &&
            Objects.equals(getBundleVersion(), that.getBundleVersion()) &&
            Objects.equals(getClasspathLibs(), that.getClasspathLibs()) &&
            Objects.equals(getRequireBundles(), that.getRequireBundles()) &&
            Objects.equals(getExportPackages(), that.getExportPackages()) &&
            Objects.equals(getImportPackages(), that.getImportPackages()) &&
            Objects.equals(getStartLevel(), that.getStartLevel());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPath(), getBundleName(), getBundleVersion(), getClasspathLibs(), getRequireBundles(), getExportPackages(), getImportPackages(), getStartLevel());
    }

    @Override
    public String toString() {
        return "BundleInfo[" + bundleName + "]";
    }
}