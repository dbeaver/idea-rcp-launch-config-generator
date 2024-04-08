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
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.Result;
import org.jkiss.tools.rcplaunchconfig.p2.P2BundleLookupCache;
import org.jkiss.tools.rcplaunchconfig.p2.P2RepositoryManager;
import org.jkiss.tools.rcplaunchconfig.p2.RemoteP2Feature;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;
import org.jkiss.tools.rcplaunchconfig.xml.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class FeatureResolver {

    private static final Logger log = LoggerFactory.getLogger(FeatureResolver.class);

    private static final String FEATURES_XML_FILENAME = "feature.xml";

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
            parseFeatureFile(result, bundleName, featureXmlFiles.get(0));
        } else if (featureXmlFiles.isEmpty()) {
            P2BundleLookupCache lookupCache = P2RepositoryManager.INSTANCE.getLookupCache();
            Optional<RemoteP2Feature> remoteP2FeatureOptional
                = lookupCache.getRemoteFeaturesByNames().get(bundleName).stream().max(Comparator.comparing(RemoteP2Feature::getVersion));
            if (remoteP2FeatureOptional.isPresent()) {
                RemoteP2Feature remoteP2Feature = remoteP2FeatureOptional.get();
                boolean success = remoteP2Feature.resolveFeature();
                if (success) {
                    File child = FileUtils.findFirstChildByPackageName(remoteP2Feature.getPath(), FEATURES_XML_FILENAME);
                    parseFeatureFile(result, bundleName, child);
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

    private static void parseFeatureFile(
        @Nonnull Result result,
        @Nonnull String bundleName,
        @Nonnull File featureXmlFile
    ) throws XMLStreamException, IOException {
        result.addResolvedFeature(bundleName);
        XmlReader.INSTANCE.parseXmlFile(result, featureXmlFile);
    }
}
