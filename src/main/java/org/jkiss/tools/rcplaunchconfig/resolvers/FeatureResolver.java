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
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.Result;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;
import org.jkiss.tools.rcplaunchconfig.xml.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
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
            .collect(Collectors.toList());

        if (featureXmlFiles.size() == 1) {
            parseFeatureFile(result, bundleName, featureXmlFiles.get(0));
        } else if (featureXmlFiles.isEmpty()) {
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
