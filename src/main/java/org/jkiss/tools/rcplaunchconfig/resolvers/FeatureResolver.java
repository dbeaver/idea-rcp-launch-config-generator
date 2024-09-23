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
import org.jkiss.code.NotNull;
import org.jkiss.tools.rcplaunchconfig.FeatureInfo;
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.Result;
import org.jkiss.tools.rcplaunchconfig.p2.P2BundleLookupCache;
import org.jkiss.tools.rcplaunchconfig.p2.P2RepositoryManager;
import org.jkiss.tools.rcplaunchconfig.p2.RemoteP2Feature;
import org.jkiss.tools.rcplaunchconfig.util.BundleUtils;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;
import org.jkiss.tools.rcplaunchconfig.xml.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class FeatureResolver {
    private final Lock lock = new ReentrantLock();

    private static final Logger log = LoggerFactory.getLogger(FeatureResolver.class);

    private static final String FEATURES_XML_FILENAME = "feature.xml";

    private static final Map<Path, List<FeatureInfo>> projectFeatureStack = new ConcurrentHashMap<>();

    public static void addNewFeatureProject(Path productPath) {
        projectFeatureStack.put(productPath, new ArrayList<>());
    }

    public static FeatureInfo getCurrentFeature(Path productPath) {
        try {
            if (!projectFeatureStack.containsKey(productPath)) {
                System.out.println("Failed " + productPath);
            }
        } catch (NullPointerException nullPointerException) {
            System.out.println("Failed " + productPath);
        }
        if (projectFeatureStack.get(productPath).isEmpty()) {
            return null;
        }
        return projectFeatureStack.get(productPath).get(projectFeatureStack.get(productPath).size() - 1);
    }

    public static void resolveFeatureDependencies(
        @Nonnull Result result,
        @Nonnull String bundleName
    ) throws IOException, XMLStreamException {
        if (result.isFeatureResolved(bundleName)) {
            return;
        }

        var featuresFoldersPaths = PathsManager.INSTANCE.getFeaturesLocations();
        var featureXmlFiles = featuresFoldersPaths.stream()
            .map(featuresFolderPath -> FileUtils.findFirstChildByPackageName(featuresFolderPath, bundleName))
            .filter(Objects::nonNull)
            .map(featureFolder -> FileUtils.findFirstChildByPackageName(featureFolder, FEATURES_XML_FILENAME))
            .filter(Objects::nonNull)
            .toList();

        if (featureXmlFiles.size() == 1) {
            Optional<RemoteP2Feature> maxVersionRemoteFeature = BundleUtils.getMaxVersionRemoteFeature(bundleName, P2RepositoryManager.INSTANCE.getLookupCache());
            if (maxVersionRemoteFeature.isPresent() && BundleUtils.isRemoteFeatureVersionGreater(maxVersionRemoteFeature.get(), FileUtils.extractVersion(featureXmlFiles.get(0).getParentFile()))) {
                if (!resolveRemoteFeature(result, bundleName, maxVersionRemoteFeature.get())) {
                    log.error("Couldn't resolve newer version feature '{}'", bundleName);
                }
            } else {
                parseFeatureFile(result, bundleName, featureXmlFiles.get(0));
            }
        } else if (featureXmlFiles.isEmpty()) {
            P2BundleLookupCache lookupCache = P2RepositoryManager.INSTANCE.getLookupCache();
            Optional<RemoteP2Feature> remoteP2FeatureOptional
                = lookupCache.getRemoteFeaturesByName(bundleName).stream().max(Comparator.comparing(RemoteP2Feature::getVersion));
            if (remoteP2FeatureOptional.isPresent()) {
                RemoteP2Feature remoteP2Feature = remoteP2FeatureOptional.get();
                if (resolveRemoteFeature(result, bundleName, remoteP2Feature)) {
                    return;
                }
            }
            log.error("Couldn't find feature '{}'", bundleName);
        } else {
            var featuresFilesPaths = featureXmlFiles.stream()
                .map(it -> {
                    try {
                        return it.getCanonicalPath();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.joining("\n  "));
            log.warn("Found multiple features '{}'. First will be used.\n  {}", bundleName, featuresFilesPaths);
            parseFeatureFile(result, bundleName, featureXmlFiles.get(0));
        }
    }

    private static boolean resolveRemoteFeature(@NotNull Result result, @NotNull String bundleName, RemoteP2Feature remoteP2Feature) throws XMLStreamException, IOException {
        boolean success = remoteP2Feature.resolveFeature();
        if (success) {
            File child = FileUtils.findFirstChildByPackageName(remoteP2Feature.getPath(), FEATURES_XML_FILENAME);
            parseFeatureFile(result, bundleName, child);
            return true;
        }
        return false;
    }

    private static void parseFeatureFile(
        @Nonnull Result result,
        @Nonnull String bundleName,
        @Nonnull File featureXmlFile
    ) throws XMLStreamException, IOException {
        FeatureInfo currentFeature = getCurrentFeature(result.getProductPath());

        FeatureInfo newFeature = result.addResolvedFeature(bundleName, featureXmlFile);
        projectFeatureStack.get(result.getProductPath()).add(newFeature);

        if (currentFeature != null) {
            currentFeature.addFeatureDependency(newFeature);
        }
        XmlReader.INSTANCE.parseXmlFile(result, featureXmlFile);

        FeatureInfo lastFeature = projectFeatureStack.get(result.getProductPath()).remove(projectFeatureStack.get(result.getProductPath()).size() - 1);
        if (lastFeature != newFeature) {
            throw new IOException("Feature parser internal error. Feature [" +
                lastFeature.getFeatureName() + "] found while [" + newFeature.getFeatureName() + "] was expected");
        }
    }
}
