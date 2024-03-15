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
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.Result;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class PluginResolver {
    private static final Logger log = LoggerFactory.getLogger(PluginResolver.class);

    private static final Path MANIFEST_PATH = Paths.get("META-INF", "MANIFEST.MF");

    private static final Map<String, String> correctedFolderNames = Map.of(
    );

    public static void resolvePluginDependencies(
        @Nonnull Result result,
        @Nonnull String bundleName,
        @Nullable Integer startLevel
    ) throws IOException {
        if (PackageChecker.INSTANCE.isPackageExcluded(bundleName)) {
            return;
        }
        var previousParsedBundle = result.getBundleByName(bundleName);
        if (previousParsedBundle != null) {
            if (previousParsedBundle.getStartLevel() == null && startLevel != null) {
                // if previousParsedBundle does not have 'startLevel' — update it
                var newParsedBundle = new BundleInfo(
                    previousParsedBundle.getPath(),
                    previousParsedBundle.getBundleName(),
                    previousParsedBundle.getBundleVersion(),
                    previousParsedBundle.getClasspathLibs(),
                    previousParsedBundle.getRequireBundles(),
                    previousParsedBundle.getExportPackages(),
                    previousParsedBundle.getImportPackages(),
                    startLevel
                );
                result.addBundle(newParsedBundle);
            }
            return;
        }

        var pluginsFoldersPaths = PathsManager.INSTANCE.getBundlesLocations();

        var bundleInfos = pluginsFoldersPaths.stream()
            .map(pluginFolderPath -> {
                var correctedFolderName = correctFolderName(bundleName);
                return FileUtils.findFirstChildByPackageName(pluginFolderPath, correctedFolderName);
            })
            .filter(Objects::nonNull)
            .map(pluginJarOrFolder -> extractBundleInfo(pluginJarOrFolder, startLevel))
            .filter(Objects::nonNull)
            .toList();

        if (bundleInfos.size() == 1) {
            parseBundleInfo(result, bundleInfos.get(0));
        } else if (bundleInfos.isEmpty()) {
            log.error("Couldn't find plugin '{}'", bundleName);
        } else {
            var bundlesPaths = bundleInfos.stream()
                .map(it -> it.getPath().toString())
                .collect(Collectors.joining("\n  "));
            log.debug("Found multiple plugins '{}'. First will be used.\n  {}", bundleName, bundlesPaths);
            parseBundleInfo(result, bundleInfos.get(0));
        }
    }

    private static @Nonnull String correctFolderName(@Nonnull String nameToCorrect) {
        var correctedName = correctedFolderNames.get(nameToCorrect);
        return correctedName == null
            ? nameToCorrect
            : correctedName;
    }

    private static @Nullable BundleInfo extractBundleInfo(
        @Nonnull File pluginJarOrFolder,
        @Nullable Integer startLevel
    ) {
        try {
            if (pluginJarOrFolder.isDirectory()) {
                var manifestFile = pluginJarOrFolder.toPath().resolve(MANIFEST_PATH).toFile();
                if (!manifestFile.exists()) {
                    log.error("Cannot find '{}'", manifestFile.getPath());
                }
                try (var inputStream = new FileInputStream(manifestFile)) {
                    var manifest = new Manifest(inputStream);
                    return ManifestParser.parseManifest(pluginJarOrFolder.toPath(), startLevel, manifest);
                }
            } else {
                try (var jarFile = new JarFile(pluginJarOrFolder)) {
                    var manifest = jarFile.getManifest();
                    return ManifestParser.parseManifest(pluginJarOrFolder.toPath(), startLevel, manifest);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void parseBundleInfo(
        @Nonnull Result result,
        @Nonnull BundleInfo bundleInfo
    ) throws IOException {
        result.addBundle(bundleInfo);

        for (var requireBundle : bundleInfo.getRequireBundles()) {
            PluginResolver.resolvePluginDependencies(result, requireBundle, null);
        }
    }
}
