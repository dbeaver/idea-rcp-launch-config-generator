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
import org.jkiss.code.Nullable;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum PathsManager {
    INSTANCE();

    private Collection<Path> featuresPaths;
    private Collection<Path> bundlesPaths;
    private Collection<String> testBundles;

    private Path eclipsePath;
    private Path eclipsePluginsPath;
    private Path eclipseFeaturesPath;

    public void init(
        @Nonnull Properties settings,
        @Nonnull Path projectsFolderPath,
        @Nullable Path eclipsePath,
        @Nonnull Path... additionalBundlesPaths
    ) throws IOException {
        if (eclipsePath == null) {
            eclipsePath = projectsFolderPath.resolve("dbeaver-eclipse-workspace/dependencies/");
        }
        this.eclipsePath = eclipsePath;
        eclipsePluginsPath = eclipsePath.resolve("plugins");
        if (!eclipsePluginsPath.toFile().exists()) {
            Files.createDirectories(eclipsePluginsPath);
        }
        eclipseFeaturesPath = eclipsePath.resolve("features");
        if (!eclipseFeaturesPath.toFile().exists()) {
            Files.createDirectories(eclipseFeaturesPath);
        }
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
        var testBundlesPathsString = (String) settings.get("testBundles");
        testBundles =
            Arrays.stream(testBundlesPathsString.split(";"))
                .map(String::trim)
                .collect(Collectors.toList());

    }

    public @Nonnull Collection<Path> getFeaturesLocations() {
        return featuresPaths;
    }

    public @Nonnull Collection<Path> getBundlesLocations() {
        return bundlesPaths;
    }

    public @Nonnull Collection<String> getTestBundles() {
        return testBundles;
    }

    public @Nonnull Path getEclipsePath() {
        return eclipsePath;
    }

    public @Nonnull Path getEclipsePluginsPath() {
        return eclipsePluginsPath;
    }

    public Path getEclipseFeaturesPath() {
        return eclipseFeaturesPath;
    }
}
