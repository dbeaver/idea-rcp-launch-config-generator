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
package org.jkiss.tools.rcplaunchconfig;

import jakarta.annotation.Nonnull;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum PathsManager {
    INSTANCE();

    private Collection<Path> featuresPaths;
    private Collection<Path> bundlesPaths;

    private Path eclipsePath;
    private Path eclipsePluginsPath;
    private Path eclipseFeaturesPath;

    public void init(
        @Nonnull Path configFilePath,
        @Nonnull Path projectsFolderPath,
        @Nonnull Path eclipsePath,
        @Nonnull Path... additionalBundlesPaths
    ) throws IOException {
        var settings = ConfigFileManager.readSettingsFile(configFilePath);

        this.eclipsePath = eclipsePath;
        eclipsePluginsPath = eclipsePath.resolve("plugins");
        eclipseFeaturesPath = eclipsePath.resolve("features");

        var featuresPathsString = (String) settings.get("featuresPaths");
        featuresPaths = Arrays.stream(featuresPathsString.split(";"))
            .map(String::trim)
            .map(projectsFolderPath::resolve)
            .filter(FileUtils::exists)
            .collect(Collectors.toList());
        featuresPaths.add(eclipseFeaturesPath);

        var bundlesPathsString = (String) settings.get("bundlesPaths");
        bundlesPaths = Stream.concat(
                Arrays.stream(bundlesPathsString.split(";"))
                    .map(String::trim)
                    .map(projectsFolderPath::resolve),
                Stream.concat(
                    Stream.of(eclipsePluginsPath),
                    Arrays.stream(additionalBundlesPaths)
                )
            )
            .filter(FileUtils::exists)
            .collect(Collectors.toList());
    }

    public @Nonnull Collection<Path> getFeaturesLocations() {
        return featuresPaths;
    }

    public @Nonnull Collection<Path> getBundlesLocations() {
        return bundlesPaths;
    }

    public @Nonnull Path getEclipsePath() {
        return eclipsePath;
    }

    public @Nonnull Path getEclipsePluginsPath() {
        return eclipsePluginsPath;
    }
}
