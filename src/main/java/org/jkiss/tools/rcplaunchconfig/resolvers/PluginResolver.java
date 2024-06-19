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
import org.jkiss.code.NotNull;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.Result;
import org.jkiss.tools.rcplaunchconfig.p2.P2BundleLookupCache;
import org.jkiss.tools.rcplaunchconfig.p2.P2RepositoryManager;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2BundleInfo;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;
import org.jkiss.tools.rcplaunchconfig.util.BundleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PluginResolver {
    private static final Logger log = LoggerFactory.getLogger(PluginResolver.class);

    private static final Path MANIFEST_PATH = Paths.get("META-INF", "MANIFEST.MF");

    private static final Map<String, String> correctedFolderNames = Map.of(
    );

    public static void resolvePluginDependencies(
        @Nonnull Result result,
        @Nonnull String bundleName,
        @Nullable Integer startLevel,
        P2BundleLookupCache cache
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
                    previousParsedBundle.getReexportedBundles(),
                    previousParsedBundle.getExportPackages(),
                    previousParsedBundle.getImportPackages(),
                    previousParsedBundle.getFragmentHost(),
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
            Optional<RemoteP2BundleInfo> maxVersionRemoteBundle = BundleUtils.getMaxVersionRemoteBundle(bundleName, cache);
            if (maxVersionRemoteBundle.isPresent() && BundleUtils.isRemoteBundleVersionGreater(maxVersionRemoteBundle.get(), bundleInfos.get(0))) {
                maxVersionRemoteBundle.get().resolveBundle();
                parseBundleInfo(result, bundleInfos.get(0), cache);
            } else {
                parseBundleInfo(result, bundleInfos.get(0), cache);
            }
        } else if (bundleInfos.isEmpty()) {

            Optional<RemoteP2BundleInfo> remoteP2BundleInfos = BundleUtils.getMaxVersionRemoteBundle(bundleName, cache);
            if (remoteP2BundleInfos.isEmpty()) {
                log.error("Couldn't find plugin '{}'", bundleName);
            } else {
                remoteP2BundleInfos.stream().findFirst().get().resolveBundle();
                parseBundleInfo(result, remoteP2BundleInfos.stream().findFirst().get(), cache);
            }

        } else {
            var bundlesPaths = bundleInfos.stream()
                .map(it -> it.getPath().toString())
                .collect(Collectors.joining("\n  "));
            log.debug("Found multiple plugins '{}'. First will be used.\n  {}", bundleName, bundlesPaths);
            parseBundleInfo(result, bundleInfos.get(0), cache);
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

    public static void resolveTestBundles(Result result) throws IOException {
        P2BundleLookupCache lookupCache = P2RepositoryManager.INSTANCE.getLookupCache();
        Collection<Path> testBundlesPaths = PathsManager.INSTANCE.getTestBundlesPaths();
        for (Path testBundlesPath : testBundlesPaths) {
            if (testBundlesPath.toFile().exists() && testBundlesPath.toFile().isDirectory()) {
                List<BundleInfo> bundlesToResolve = new ArrayList<>();
                for (File file : testBundlesPath.toFile().listFiles()) {
                    if (file.getName().startsWith("org.jkiss") || file.getName().startsWith("com.dbeaver") || file.getName().startsWith("io.cloudbeaver")) {
                        if (file.isDirectory()) {
                            var manifestFile = file.toPath().resolve(MANIFEST_PATH).toFile();
                            if (!manifestFile.exists()) {
                                continue;
                            }
                            try (var inputStream = new FileInputStream(manifestFile)) {
                                var manifest = new Manifest(inputStream);
                                BundleInfo bundleInfo = ManifestParser.parseManifest(file.toPath(), null, manifest);
                                if (bundleInfo != null) {
                                    result.addBundle(bundleInfo);
                                    bundlesToResolve.add(bundleInfo);
                                    System.out.println("test bundle added " + bundleInfo + " fragment " + bundleInfo.getFragmentHost());
                                }
                            }
                        }
                    }
                }
                for (BundleInfo bundleInfo : bundlesToResolve) {
                    for (String requireBundle : bundleInfo.getRequireBundles()) {
                        resolvePluginDependencies(result, requireBundle, null, lookupCache);
                    }
                    if (bundleInfo.getFragmentHost() != null) {
                        resolvePluginDependencies(result, bundleInfo.getFragmentHost(), null, lookupCache);
                    }
                }
            }
        }
    }

    private static void parseBundleInfo(
        @Nonnull Result result,
        @Nonnull BundleInfo bundleInfo,
        P2BundleLookupCache cache
    ) throws IOException {
        result.addBundle(bundleInfo);
        if (bundleInfo.getFragmentHost() != null) {
            BundleInfo hostBundle = getHostBundle(result, bundleInfo, cache);
            if (hostBundle != null) {
                hostBundle.addFragmentBundle(bundleInfo);
            } else {
                log.error("Fragment host bundle not found");
            }
        }
        for (var requireBundle : bundleInfo.getRequireBundles()) {
            PluginResolver.resolvePluginDependencies(result, requireBundle, null, cache);
        }
    }

    @org.jkiss.code.Nullable
    private static BundleInfo getHostBundle(@NotNull Result result,
                                            @NotNull BundleInfo bundleInfo,
                                            P2BundleLookupCache cache) {
        BundleInfo hostBundle = result.getBundleByName(bundleInfo.getFragmentHost());
        if (hostBundle == null) {
            Collection<RemoteP2BundleInfo> remoteBundlesByName = cache.getRemoteBundlesByName(bundleInfo.getFragmentHost());
            if (!remoteBundlesByName.isEmpty()) {
                hostBundle = remoteBundlesByName.stream().findFirst().orElse(null);
            }
        }
        return hostBundle;
    }
}
