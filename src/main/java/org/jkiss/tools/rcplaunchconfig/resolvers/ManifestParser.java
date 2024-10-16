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
package org.jkiss.tools.rcplaunchconfig.resolvers;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.jkiss.code.NotNull;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.util.DependencyInformation;
import org.jkiss.tools.rcplaunchconfig.util.Version;
import org.jkiss.tools.rcplaunchconfig.util.VersionRange;
import org.jkiss.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ManifestParser {

    private static final Logger log = LoggerFactory.getLogger(ManifestParser.class);
    private static final Pattern VERSION_REGEX = Pattern.compile(".*(?:version|bundle-version)=\"([^\"]*)");
    public static @Nullable BundleInfo parseManifest(
        @Nonnull Path pathToContainingFolderOrJar,
        @Nullable Integer startLevel,
        @Nonnull Manifest manifest
    ) {
        var attributes = manifest.getMainAttributes();

        var bundleNameArg = attributes.getValue("Bundle-SymbolicName");
        if (bundleNameArg == null) {
            log.error("Manifest without Bundle-SymbolicName '{}'", pathToContainingFolderOrJar);
            return null;
        }
        var bundleName = trimBundleName(bundleNameArg);

        var bundleVersionArg = attributes.getValue("Bundle-Version");

        var classPath = parseBundleClasspath(attributes);

        var requireBundlesArg = attributes.getValue("Require-Bundle");
        Stream<String> requiredBundlesStream = getBundlesStream(requireBundlesArg);
        List<Pair<String, VersionRange>> requireBundles = requiredBundlesStream == null
            ? List.of()
            : requiredBundlesStream
            .map(it -> convertToDependencyInformation(it, false))
            .map(ManifestParser::convertDependencyInformationToVersionRangePair)
            .collect(Collectors.toList());

        String requiredFragmentsArg = attributes.getValue("X-Require-Fragment");
        Stream<String> bundlesStream = getBundlesStream(requiredFragmentsArg);
        List<String> requiredFragments = bundlesStream == null
            ? List.of()
            : bundlesStream
            .map(ManifestParser::trimBundleName)
            .toList();

        Set<String> reexportedBundles = parseReexportedBundles(attributes);
        var exportPackageArg = splitExportPackagesList(attributes.getValue("Export-Package"));
        var importPackageArg = splitImportPackagesList(attributes.getValue("Import-Package"));
        Pair<String, VersionRange> fragmentHost = parseFragmentHost(attributes);
        return new BundleInfo(
            pathToContainingFolderOrJar,
            bundleName,
            bundleVersionArg != null ? bundleVersionArg.trim() : "",
            classPath,
            requireBundles,
            reexportedBundles,
            exportPackageArg,
            importPackageArg,
            requiredFragments,
            fragmentHost,
            startLevel
        );
    }

    @org.jkiss.code.Nullable
    public static Pair<String, VersionRange> parseFragmentHost(Attributes attributes) {
        if (attributes.getValue("Fragment-Host") == null) {
            return null;
        }
        String value = attributes.getValue("Fragment-Host");
        Matcher matcher = VERSION_REGEX.matcher(value);
        VersionRange range = null;
        if (matcher.matches()) {
            String group = matcher.group(1);
            range = VersionRange.fromString(group);
        }
        String name = trimBundleName(attributes.getValue("Fragment-Host"));
        return new Pair<>(name, range);
    }

    @NotNull
    public static Set<String> parseReexportedBundles(@Nonnull Attributes attrs) {
        var requireBundlesArg = attrs.getValue("Require-Bundle");
        Stream<String> requiredBundlesStream;
        requiredBundlesStream = getBundlesStream(requireBundlesArg);
        return requiredBundlesStream == null ? Set.of() :
            requiredBundlesStream.filter(it -> it.contains("visibility:=reexport"))
                .map(ManifestParser::trimBundleName).collect(Collectors.toSet());
    }

    @org.jkiss.code.Nullable
    private static Stream<String> getBundlesStream(String requireBundlesArg) {
        return requireBundlesArg == null ? null : Arrays.stream(
                removeAllBetweenQuotes(requireBundlesArg)
                    .split(",")
            )
            .filter(ManifestParser::filterOptionalDependencies);
    }

    private static boolean filterOptionalDependencies(@Nonnull String depString) {
        return !depString.contains("resolution:=optional");
    }

    public static @Nonnull String trimBundleName(@Nonnull String bundleName) {
        return StringUtils.substringBefore(bundleName, ";")
            .trim();
    }

    public static DependencyInformation convertToDependencyInformation(String bundleInfoString, boolean isExport) {
        Version version = null;
        VersionRange versionRange = null;
        Matcher matcher = VERSION_REGEX.matcher(bundleInfoString);
        if (matcher.matches()) {
            if (isExport) {
                version = new Version(matcher.group(1));
            } else {
                versionRange = VersionRange.fromString(matcher.group(1));
            }
        }
        return new DependencyInformation(trimBundleName(bundleInfoString), version, versionRange);
    }

    public static Pair<String, VersionRange> convertDependencyInformationToVersionRangePair(DependencyInformation information) {
        return new Pair<>(information.name(), information.range());
    }
    public static Pair<String, Version> convertDependencyInformationToVersionPair(DependencyInformation information) {
        return new Pair<>(information.name(), information.version());
    }

    public static @Nonnull List<String> parseBundleClasspath(@Nonnull Attributes attrs) {
        var bundleClassPathArg = attrs.getValue("Bundle-ClassPath");
        if (bundleClassPathArg == null) {
            return List.of();
        }
        return Arrays.stream(bundleClassPathArg.split(","))
            .map(String::trim)
            .filter(it -> !it.equals("."))
            .collect(Collectors.toList());
    }

    private static Set<Pair<String, Version>> splitExportPackagesList(@Nullable String packagesList) {
        if (packagesList == null) {
            return Set.of();
        }
        Stream<DependencyInformation> dependencyInformationStream = getDependencyInformationStream(packagesList, true);
        return dependencyInformationStream
            .map(ManifestParser::convertDependencyInformationToVersionPair)
            .collect(Collectors.toSet());
    }

    private static Set<Pair<String, VersionRange>> splitImportPackagesList(@Nullable String packagesList) {
        if (packagesList == null) {
            return Set.of();
        }
        Stream<DependencyInformation> dependencyInformationStream = getDependencyInformationStream(packagesList, false);
        return dependencyInformationStream
            .map(ManifestParser::convertDependencyInformationToVersionRangePair)
            .collect(Collectors.toSet());
    }

    @NotNull
    private static Stream<DependencyInformation> getDependencyInformationStream(@NotNull String packagesList, boolean isExport) {
        var packagesListWithoutQuotes = removeAllBetweenQuotes(packagesList);
        return Arrays.stream(packagesListWithoutQuotes.split(","))
            .filter(ManifestParser::filterOptionalDependencies)
            .map(it -> convertToDependencyInformation(it, isExport));
    }

    private static @Nonnull String removeAllBetweenQuotes(@Nonnull String str) {
        var i = str.indexOf("\"");
        while (i != -1) {
            var j = str.indexOf("\"", i + 1);
            if (j != -1) {
                var data = str.substring(0, i);
                var temp = str.substring(j + 1);
                data += temp;
                str = data;
                i = str.indexOf("\"", i + 2);
            } else {
                break;
            }
        }
        return str;
    }
}
