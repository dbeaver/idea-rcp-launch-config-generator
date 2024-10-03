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
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jkiss.code.NotNull;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.Result;
import org.jkiss.tools.rcplaunchconfig.p2.P2BundleLookupCache;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2BundleInfo;
import org.jkiss.tools.rcplaunchconfig.producers.iml.IMLConfigurationProducer;
import org.jkiss.tools.rcplaunchconfig.util.BundleUtils;
import org.jkiss.tools.rcplaunchconfig.util.Version;
import org.jkiss.tools.rcplaunchconfig.util.VersionRange;
import org.jkiss.utils.Pair;
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

    private Set<String> excludedBundles = Set.of("org.eclipse.rap.rwt");


    public void start(@Nonnull Result result, P2BundleLookupCache lookupCache) throws IOException {
        var eclipsePluginsByExportedPackages = readEclipsePluginsExportedPackages(PathsManager.INSTANCE.getEclipsePluginsPath());

        MultiValuedMap<String, Pair<BundleInfo, Version>> parsedBundlesByExportedPackages = new ArrayListValuedHashMap<>();
        for (var parsedBundles : result.getBundlesByNames().values()) {
            for (BundleInfo parsedBundle : parsedBundles) {
                for (var exportedPackage : parsedBundle.getExportPackages()) {
                    parsedBundlesByExportedPackages.put(exportedPackage.getFirst(), new Pair<>(parsedBundle, exportedPackage.getSecond()));
                }
            }
        }

        var bundlesToCheck = new LinkedHashMap<>(result.getBundlesByNames());
        var additionalBundlesByImportPackage = new ArrayListValuedHashMap<Pair<String, VersionRange>, BundleInfo>();
        for (var bundlesForResolve : bundlesToCheck.values()) {
            for (BundleInfo bundleInfo : bundlesForResolve) {
                resolveImportPackages(
                    result,
                    eclipsePluginsByExportedPackages,
                    parsedBundlesByExportedPackages,
                    bundleInfo,
                    additionalBundlesByImportPackage,
                    lookupCache);
            }
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
        @Nonnull MultiValuedMap<String, Pair<BundleInfo, Version>> eclipsePluginsByExportedPackages,
        @Nonnull MultiValuedMap<String, Pair<BundleInfo, Version>> parsedResultPluginsByExportedPackages,
        @Nonnull BundleInfo bundleInfo,
        @Nonnull MultiValuedMap<Pair<String, VersionRange>, BundleInfo> bundlesToAddByImportPackage,
        @Nonnull P2BundleLookupCache lookupCache
    ) throws IOException {
        for (var packageToImport : bundleInfo.getImportPackages()) {
            if (PackageChecker.INSTANCE.isPackageExcluded(packageToImport.getFirst()) ||
                parsedResultPluginsByExportedPackages.containsKey(packageToImport) ||
                bundlesToAddByImportPackage.containsKey(packageToImport)
            ) {
                if (parsedResultPluginsByExportedPackages.containsKey(packageToImport)) {
                    List<BundleInfo> list = getSuitableBundles(parsedResultPluginsByExportedPackages, packageToImport);
                    for (BundleInfo info : list) {
                        IMLConfigurationProducer.INSTANCE.addRequiredBundleforPackage(packageToImport, info);
                    }
                }
                // skip packages which is excluded or already resolved or planned to add
                continue;
            }
            var eclipseBundlesWithThisPackage = new ArrayList<>(getSuitableBundles(eclipsePluginsByExportedPackages, packageToImport));
            if (eclipseBundlesWithThisPackage.isEmpty()) {
                Collection<RemoteP2BundleInfo> remoteP2BundleInfos = lookupCache.getRemoteBundlesByExport(packageToImport.getFirst());
                if (!failedToResolvePackagesToBundles.containsKey(packageToImport) && !lookupCache.getRemoteBundlesByExport(packageToImport.getFirst()).isEmpty()) {
                    for (RemoteP2BundleInfo remoteP2BundleInfo : remoteP2BundleInfos) {
                        if (excludedBundles.contains(remoteP2BundleInfo.getBundleName())) {
                            continue;
                        }
                        Optional<RemoteP2BundleInfo> maxVersionRemoteBundle = BundleUtils.getMaxVersionRemoteBundle(new Pair<>(remoteP2BundleInfo.getBundleVersion(), packageToImport.getSecond()), lookupCache);
                        if (maxVersionRemoteBundle.isPresent() && maxVersionRemoteBundle.get().resolveBundle()) {
                            for (var packageToExport : maxVersionRemoteBundle.get().getExportPackages()) {
                                eclipsePluginsByExportedPackages.put(packageToExport.getFirst(), new Pair<>(maxVersionRemoteBundle.get(), packageToExport.getSecond()));
                            }
                            eclipseBundlesWithThisPackage.add(maxVersionRemoteBundle.get());
                        } else {
                            failedToResolvePackagesToBundles.put(packageToImport.getFirst(), bundleInfo);
                        }
                    }
                    if (eclipseBundlesWithThisPackage.isEmpty()) {
                        continue;
                    }
                } else {
                    failedToResolvePackagesToBundles.put(packageToImport.getFirst(), bundleInfo);
                    continue;
                }
            } else if (eclipseBundlesWithThisPackage.size() > 1) {
                var bundlesPathsList = eclipseBundlesWithThisPackage.stream()
                    .map(it -> it.getPath().toString())
                    .collect(Collectors.joining("\n  "));
                log.debug("Multiple plugins exports same package: '{}'\n  {}\n  All bundles will be used", packageToImport, bundlesPathsList);
            }
            eclipseBundlesWithThisPackage.forEach(it -> IMLConfigurationProducer.INSTANCE.addRequiredBundleforPackage(packageToImport, it));
            for (var bundleToAdd : eclipseBundlesWithThisPackage) {
                bundlesToAddByImportPackage.put(packageToImport, bundleToAdd);

                var newResult = new DynamicImportResult(result);
                resolveImportPackages(newResult, eclipsePluginsByExportedPackages, parsedResultPluginsByExportedPackages, bundleToAdd, bundlesToAddByImportPackage, lookupCache);
                for (var requireBundle : bundleToAdd.getRequireBundles()) {
                    PluginResolver.resolvePluginDependencies(newResult, requireBundle, null, lookupCache);
                }
                BundleInfo[] array = newResult.getNewBundles().toArray(new BundleInfo[0]);
                for (var newAddedBundle : array) {
                    resolveImportPackages(newResult, eclipsePluginsByExportedPackages, parsedResultPluginsByExportedPackages, newAddedBundle, bundlesToAddByImportPackage, lookupCache);
                }
                newResult.flush();
            }
        }
    }

    @NotNull
    private static List<BundleInfo> getSuitableBundles(@NotNull MultiValuedMap<String, Pair<BundleInfo, Version>> bundlesByExportedPackages,
                                                       Pair<String, VersionRange> packageToImport) {
        return bundlesByExportedPackages.get(packageToImport.getFirst()).stream().filter(it -> VersionRange.isVersionsCompatible(packageToImport.getSecond(), it.getSecond())).map(Pair::getFirst).toList();
    }

    private @Nonnull MultiValuedMap<String, Pair<BundleInfo, Version>> readEclipsePluginsExportedPackages(
        @Nonnull Path eclipsePluginsPath
    ) throws IOException {

        var eclipsePluginsFolder = eclipsePluginsPath.toFile();
        var children = eclipsePluginsFolder.listFiles();
        if (children == null) {
            log.error("Couldn't get '{}'s children", eclipsePluginsPath);
            return new ArrayListValuedHashMap<>();
        }

        var result = new ArrayListValuedHashMap<String, Pair<BundleInfo, Version>>();

        for (var folderOrJar : children) {
            if (".DS_Store".equals(folderOrJar.getName())) {
                continue;
            }
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
                } catch (Exception e) {
                    log.error("Error during opening jar file for " + folderOrJar);
                    throw e;
                }
            }
            if (bundleInfo != null) {
                for (var packageToExport : bundleInfo.getExportPackages()) {
                    result.put(packageToExport.getFirst(), new Pair<>(bundleInfo, packageToExport.getSecond()));
                }
            }
        }
        return result;
    }

    static class DynamicImportResult extends Result {
        private final Result previousResult;
        private final Map<String, Set<BundleInfo>> newBundlesByNames;

        DynamicImportResult(@Nonnull Result previousResult) {
            this.previousResult = previousResult;
            this.newBundlesByNames = new LinkedHashMap<>();
        }

        @Override
        public Path getProductPath() {
            return previousResult.getProductPath();
        }

        public Collection<BundleInfo> getNewBundles() {
            return newBundlesByNames.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        }

        @Override
        public void addBundle(@Nonnull BundleInfo bundleInfo) {
            newBundlesByNames.computeIfAbsent(bundleInfo.getBundleName(), it -> new HashSet<>()).add(bundleInfo);
        }

        @Override
        public boolean isPluginResolved(@Nonnull String pluginName) {
            return newBundlesByNames.containsKey(pluginName) || previousResult.isPluginResolved(pluginName);
        }

        @Nullable
        @Override
        public Set<BundleInfo> getBundlesByName(@Nonnull String name) {
            var newBundle = newBundlesByNames.get(name);
            return newBundle != null
                ? newBundle
                : previousResult.getBundlesByName(name);
        }

        @Nonnull
        @Override
        public Map<String, Set<BundleInfo>> getBundlesByNames() {
            Map<String, Set<BundleInfo>> result = new LinkedHashMap<>(newBundlesByNames);
            result.putAll(previousResult.getBundlesByNames());
            return result;
        }

        public void flush() {
            for (var bundleInfo : newBundlesByNames.values()) {
                for (BundleInfo info : bundleInfo) {
                    previousResult.addBundle(info);
                }
            }
        }
    }
}
