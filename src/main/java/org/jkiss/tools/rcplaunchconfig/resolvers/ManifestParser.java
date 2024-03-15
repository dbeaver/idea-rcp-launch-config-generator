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
package org.jkiss.tools.rcplaunchconfig.resolvers;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class ManifestParser {

    private static final Logger log = LoggerFactory.getLogger(ManifestParser.class);

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
        List<String> requireBundles = requireBundlesArg == null
            ? List.of()
            : Arrays.stream(
                removeAllBetweenQuotes(requireBundlesArg)
                    .split(",")
            )
            .filter(ManifestParser::filterOptionalDependencies)
            .map(ManifestParser::trimBundleName)
            .collect(Collectors.toList());

        var exportPackageArg = splitPackagesList(attributes.getValue("Export-Package"));
        var importPackageArg = splitPackagesList(attributes.getValue("Import-Package"));

        return new BundleInfo(
            pathToContainingFolderOrJar,
            bundleName,
            bundleVersionArg != null ? bundleVersionArg.trim() : "",
            classPath,
            requireBundles,
            exportPackageArg,
            importPackageArg,
            startLevel
        );
    }

    private static boolean filterOptionalDependencies(@Nonnull String depString) {
        return !depString.contains("resolution:=optional");
    }

    public static @Nonnull String trimBundleName(@Nonnull String bundleName) {
        return StringUtils.substringBefore(bundleName, ";")
            .trim();
    }

    private static @Nonnull List<String> parseBundleClasspath(@Nonnull Attributes attrs) {
        var bundleClassPathArg = attrs.getValue("Bundle-ClassPath");
        if (bundleClassPathArg == null) {
            return List.of();
        }
        return Arrays.stream(bundleClassPathArg.split(","))
            .map(String::trim)
            .filter(it -> !it.equals("."))
            .collect(Collectors.toList());
    }

    private static Set<String> splitPackagesList(@Nullable String packagesList) {
        if (packagesList == null) {
            return Set.of();
        }
        var packagesListWithoutQuotes = removeAllBetweenQuotes(packagesList);
        return Arrays.stream(packagesListWithoutQuotes.split(","))
            .map(ManifestParser::trimBundleName)
            .collect(Collectors.toSet());
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
