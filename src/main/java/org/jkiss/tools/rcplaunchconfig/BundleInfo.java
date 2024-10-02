/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
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
package org.jkiss.tools.rcplaunchconfig;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jkiss.code.NotNull;
import org.jkiss.tools.rcplaunchconfig.util.Version;
import org.jkiss.tools.rcplaunchconfig.util.VersionRange;
import org.jkiss.utils.Pair;

import java.nio.file.Path;
import java.util.*;

public class BundleInfo implements ModuleInfo {

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

    private final List<String> requireFragments;

    protected Set<String> reexportedBundles;
    protected Pair<String, VersionRange> fragmentHost;
    @NotNull
    protected Path path;
    private final String bundleName;
    private final String bundleVersion;
    protected List<String> classpathLibs;
    private final List<Pair<String, VersionRange>> requireBundles;
    private final Set<Pair<String, Version>> exportPackages;
    private final Set<Pair<String, VersionRange>> importPackages;
    private final Integer startLevel;
    // Additional versions is an exceptional case
    // E.g. jakarta.annotation-api of different versions (1.x and 2.x) are completely different and export different packages
    // Thus we need all versions
    private String additionalVersions;
    private Set<BundleInfo> fragments = new HashSet<>();

    public BundleInfo(
        @Nullable Path path,
        @Nonnull String bundleName,
        @Nonnull String bundleVersion,
        @Nonnull List<String> classpathLibs,
        @Nonnull List<Pair<String, VersionRange>> requireBundles,
        @Nonnull Set<String> reexportedBundles,
        @Nonnull Set<Pair<String, Version>> exportPackages,
        @Nonnull Set<Pair<String, VersionRange>> importPackages,
        @Nonnull List<String> requiredFragments,
        @Nullable Pair<String, VersionRange> fragmentHost,
        @Nullable Integer startLevel) {
        this.path = path;
        this.bundleName = bundleName;
        this.bundleVersion = bundleVersion;
        this.classpathLibs = classpathLibs;
        this.requireBundles = requireBundles;
        this.reexportedBundles = reexportedBundles;
        this.exportPackages = exportPackages;
        this.importPackages = importPackages;
        this.startLevel = startLevel;
        this.fragmentHost = fragmentHost;
        this.requireFragments = requiredFragments;
    }

    public @Nullable Path getPath() {
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

    public @Nonnull List<Pair<String, VersionRange>> getRequireBundles() {
        return requireBundles;
    }

    public @Nonnull Set<Pair<String, Version>> getExportPackages() {
        return exportPackages;
    }

    public @Nonnull Set<String> getReexportedBundles() {
        return reexportedBundles;
    }

    public @Nonnull Set<Pair<String, VersionRange>> getImportPackages() {
        return importPackages;
    }

    public @Nullable Pair<String, VersionRange> getFragmentHost() {
        return fragmentHost;
    }

    public Set<BundleInfo> getFragments() {
        return fragments;
    }

    public List<String> getRequireFragments() {
        return requireFragments;
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

    public void addFragmentBundle(BundleInfo fragment) {
        this.fragments.add(fragment);
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

    @Override
    public String getModuleName() {
        return getBundleName();
    }

    @Override
    public Path getModuleFile() {
        return getPath();
    }
}