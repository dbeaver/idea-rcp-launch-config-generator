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
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jkiss.tools.rcplaunchconfig.p2.P2BundleLookupCache;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2BundleInfo;
import org.jkiss.tools.rcplaunchconfig.resolvers.ManifestParser;
import org.jkiss.tools.rcplaunchconfig.resolvers.PackageChecker;
import org.jkiss.tools.rcplaunchconfig.resolvers.PluginResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class DynamicImportsResolver {

    private static final Logger log = LoggerFactory.getLogger(DynamicImportsResolver.class);

    public static final Path MANIFEST_PATH = Paths.get("META-INF", "MANIFEST.MF");

    private final MultiValuedMap<String, BundleInfo> failedToResolvePackagesToBundles = new ArrayListValuedHashMap<>();

    public void start(@Nonnull Result result, P2BundleLookupCache lookupCache) throws IOException {
        var eclipsePluginsByExportedPackages = readEclipsePluginsExportedPackages(PathsManager.INSTANCE.getEclipsePluginsPath());

        var parsedBundlesByExportedPackages = new ArrayListValuedHashMap<String, BundleInfo>();
        for (var parsedBundle : result.getBundlesByNames().values()) {
            for (var exportedPackage : parsedBundle.getExportPackages()) {
                parsedBundlesByExportedPackages.put(exportedPackage, parsedBundle);
            }
        }

        var bundlesToCheck = new LinkedHashMap<>(result.getBundlesByNames());
        var additionalBundlesByImportPackage = new ArrayListValuedHashMap<String, BundleInfo>();
        for (var bundleForResolve : bundlesToCheck.values()) {
            resolveImportPackages(
                result,
                eclipsePluginsByExportedPackages,
                parsedBundlesByExportedPackages,
                bundleForResolve,
                additionalBundlesByImportPackage,
                lookupCache);
        }
        for (var additionalBundle : additionalBundlesByImportPackage.values()) {
            //if (!result.isPluginResolved(additionalBundle.getBundleName())) {
            result.addBundle(additionalBundle);
            //}
        }

        var unresolvedPackagesMsg = failedToResolvePackagesToBundles.keySet().stream()
            .sorted()
            .filter(Predicate.not(additionalBundlesByImportPackage::containsKey))
            .map(failedToResolvePackage -> {
                var bundlesNamesList = failedToResolvePackagesToBundles.get(failedToResolvePackage).stream()
                    .map(it -> it.getPath() == null ? it.getBundleName() : it.getPath().toString())
                    .distinct()
                    .collect(Collectors.joining("\n  ", "  ", ""));
                return "  '" + failedToResolvePackage + "', imported by:\n" + bundlesNamesList;
            })
            .collect(Collectors.joining("\n"));
        if (!unresolvedPackagesMsg.isEmpty()) {
            log.debug("Couldn't resolve packages:\n{}", unresolvedPackagesMsg);
        }
    }

    private void resolveImportPackages(
        @Nonnull Result result,
        @Nonnull MultiValuedMap<String, BundleInfo> eclipsePluginsByExportedPackages,
        @Nonnull MultiValuedMap<String, BundleInfo> parsedResultPluginsByExportedPackages,
        @Nonnull BundleInfo bundleInfo,
        @Nonnull MultiValuedMap<String, BundleInfo> bundlesToAddByImportPackage,
        @Nonnull P2BundleLookupCache lookupCache
    ) throws IOException {
        for (var packageToImport : bundleInfo.getImportPackages()) {
            if (PackageChecker.INSTANCE.isPackageExcluded(packageToImport) ||
                parsedResultPluginsByExportedPackages.containsKey(packageToImport) ||
                bundlesToAddByImportPackage.containsKey(packageToImport)
            ) {
                // skip packages which is excluded or already resolved or planned to add
                continue;
            }
            var eclipseBundlesWithThisPackage = new ArrayList<>(eclipsePluginsByExportedPackages.get(packageToImport));
            if (eclipseBundlesWithThisPackage.isEmpty()) {
                Collection<RemoteP2BundleInfo> remoteP2BundleInfos = lookupCache.getRemoteBundlesByExports().get(packageToImport);
                if (!failedToResolvePackagesToBundles.containsKey(packageToImport) && !lookupCache.getRemoteBundlesByExports().get(packageToImport).isEmpty()) {
                    Optional<RemoteP2BundleInfo> remoteBundleInfo
                        = remoteP2BundleInfos.stream().max(Comparator.comparing(BundleInfo::getBundleVersion));
                    if (remoteBundleInfo.isPresent() && remoteBundleInfo.get().resolveBundle()) {
                        for (var packageToExport : remoteBundleInfo.get().getExportPackages()) {
                            eclipsePluginsByExportedPackages.put(packageToExport, remoteBundleInfo.get());
                        }
                        eclipseBundlesWithThisPackage.add(remoteBundleInfo.get());
                    } else {
                        failedToResolvePackagesToBundles.put(packageToImport, bundleInfo);
                        continue;
                    }
                } else {
                    failedToResolvePackagesToBundles.put(packageToImport, bundleInfo);
                    continue;
                }
            } else if (eclipseBundlesWithThisPackage.size() > 1) {
                var bundlesPathsList = eclipseBundlesWithThisPackage.stream()
                    .map(it -> it.getPath().toString())
                    .collect(Collectors.joining("\n  "));
                log.debug("Multiple plugins exports same package: '{}'\n  {}\n  All bundles will be used", packageToImport, bundlesPathsList);
            }
            for (var bundleToAdd : eclipseBundlesWithThisPackage) {
                bundlesToAddByImportPackage.put(packageToImport, bundleToAdd);

                var newResult = new DynamicImportResult(result);
                resolveImportPackages(newResult, eclipsePluginsByExportedPackages, parsedResultPluginsByExportedPackages, bundleToAdd, bundlesToAddByImportPackage, lookupCache);
                for (var requireBundle : bundleToAdd.getRequireBundles()) {
                    PluginResolver.resolvePluginDependencies(newResult, requireBundle, null, lookupCache);
                }
                for (var newAddedBundle : newResult.getNewBundles()) {
                    resolveImportPackages(newResult, eclipsePluginsByExportedPackages, parsedResultPluginsByExportedPackages, newAddedBundle, bundlesToAddByImportPackage, lookupCache);
                }
                newResult.flush();
            }
        }
    }

    private @Nonnull MultiValuedMap<String, BundleInfo> readEclipsePluginsExportedPackages(
        @Nonnull Path eclipsePluginsPath
    ) throws IOException {

        var eclipsePluginsFolder = eclipsePluginsPath.toFile();
        var children = eclipsePluginsFolder.listFiles();
        if (children == null) {
            log.error("Couldn't get '{}'s children", eclipsePluginsPath);
            return new ArrayListValuedHashMap<>();
        }

        var result = new ArrayListValuedHashMap<String, BundleInfo>();

        for (var folderOrJar : children) {
            BundleInfo bundleInfo;
            if (folderOrJar.isDirectory()) {
                var manifestFile = folderOrJar.toPath().resolve(MANIFEST_PATH).toFile();
                if (!manifestFile.exists()) {
                    log.error("Cannot find '{}'", manifestFile.getPath());
                    continue;
                }
                try (var inputStream = new FileInputStream(manifestFile)) {
                    var manifest = new Manifest(inputStream);
                    bundleInfo = ManifestParser.parseManifest(folderOrJar.toPath(), null, manifest);
                }
            } else {
                try (var jarFile = new JarFile(folderOrJar)) {
                    var manifest = jarFile.getManifest();
                    bundleInfo = ManifestParser.parseManifest(folderOrJar.toPath(), null, manifest);
                }
            }
            if (bundleInfo != null) {
                for (var packageToExport : bundleInfo.getExportPackages()) {
                    result.put(packageToExport, bundleInfo);
                }
            }
        }
        return result;
    }

    static class DynamicImportResult extends Result {
        private final Result previousResult;
        private final Map<String, BundleInfo> newBundlesByNames;

        DynamicImportResult(@Nonnull Result previousResult) {
            this.previousResult = previousResult;
            this.newBundlesByNames = new LinkedHashMap<>();
        }

        public Collection<BundleInfo> getNewBundles() {
            return newBundlesByNames.values();
        }

        @Override
        public void addBundle(@Nonnull BundleInfo bundleInfo) {
            newBundlesByNames.put(bundleInfo.getBundleName(), bundleInfo);
        }

        @Override
        public boolean isPluginResolved(@Nonnull String pluginName) {
            return newBundlesByNames.containsKey(pluginName) || previousResult.isPluginResolved(pluginName);
        }

        @Nullable
        @Override
        public BundleInfo getBundleByName(@Nonnull String name) {
            var newBundle = newBundlesByNames.get(name);
            return newBundle != null
                ? newBundle
                : previousResult.getBundleByName(name);
        }

        @Nonnull
        @Override
        public Map<String, BundleInfo> getBundlesByNames() {
            var result = new LinkedHashMap<>(newBundlesByNames);
            result.putAll(previousResult.getBundlesByNames());
            return result;
        }

        public void flush() {
            for (var bundleInfo : newBundlesByNames.values()) {
                previousResult.addBundle(bundleInfo);
            }
        }
    }
}
