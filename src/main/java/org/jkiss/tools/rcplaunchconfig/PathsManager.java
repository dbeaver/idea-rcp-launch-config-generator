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
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum PathsManager {
    INSTANCE();

    private Collection<Path> featuresPaths;
    private Collection<Path> bundlesPaths;
    private Map<Path, String> productsPathsAndWorkDirs;
    private Collection<Path> testBundlesPaths;


    private Path eclipsePath;
    private Path eclipsePluginsPath;
    private Path eclipseFeaturesPath;
    private Set<Path> modulesRoots;
    private List<Path> additionalLibraries;
    private Path imlModules;
    private List<Path> additionalIMlModules;
    private List<Path> ideaConfigurationFiles;
    private Path projectsFolderPath;
    private String workspaceName;
    private List<Path> additionalRepositoriesPaths;

    public void init(
        @Nonnull Properties settings,
        @Nonnull Path projectsFolderPath,
        @Nullable Path eclipsePath,
        @Nonnull Path... additionalBundlesPaths
    ) throws IOException {
        if (eclipsePath == null) {
            eclipsePath = projectsFolderPath.resolve(ConfigurationConstants.DEFAULT_WORKSPACE_LOCATION);
        }
        this.eclipsePath = eclipsePath;
        eclipsePluginsPath = eclipsePath.resolve(ConfigurationConstants.PLUGINS_FOLDER);


        if (!eclipsePluginsPath.toFile().exists()) {
            Files.createDirectories(eclipsePluginsPath);
        }
        this.workspaceName = (String) settings.get(ConfigurationConstants.WORKSPACE_NAME_PARAM);

        imlModules = eclipsePath.getParent().resolve(workspaceName);
        if (!imlModules.toFile().exists()) {
            Files.createDirectories(imlModules);
        }

        eclipseFeaturesPath = eclipsePath.resolve(ConfigurationConstants.FEATURES_FOLDER);
        if (!eclipseFeaturesPath.toFile().exists()) {
            Files.createDirectories(eclipseFeaturesPath);
        }
        var featuresPathsString = (String) settings.get(ConfigurationConstants.FEATURES_PATHS_PARAM);
        featuresPaths = Arrays.stream(featuresPathsString.split(";"))
            .map(String::trim)
            .map(projectsFolderPath::resolve)
            .filter(FileUtils::exists)
            .collect(Collectors.toList());
        featuresPaths.add(eclipseFeaturesPath);

        var additionalRepositoriesPathsStrings = (String) settings.get(ConfigurationConstants.OPTIONAL_FEATURE_REPOSITORIES_PARAM);
        additionalRepositoriesPaths = Arrays.stream(additionalRepositoriesPathsStrings.split(";"))
            .map(String::trim)
            .map(projectsFolderPath::resolve)
            .filter(FileUtils::exists)
            .collect(Collectors.toList());

        String additionalIMlModulesString = (String) settings.get(ConfigurationConstants.ADDITIONAL_IML_MODULES_PARAM);
        if (additionalIMlModulesString != null) {
            additionalIMlModules = Arrays.stream(additionalIMlModulesString.split(";"))
                .map(String::trim)
                .map(projectsFolderPath::resolve)
                .filter(FileUtils::exists)
                .collect(Collectors.toList());
        }
        var bundlesPathsString = (String) settings.get(ConfigurationConstants.BUNDLES_PATHS_PARAM);
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
        var productsPathsString = (String) settings.get(ConfigurationConstants.PRODUCTS_PATHS_PARAM);
        Map<Path, String> list = new LinkedHashMap<>();
        for (String pathString : productsPathsString.split(";")) {
            String trim = pathString.trim();
            if (pathString.contains(":")) {
                String[] pathAndWorkDir = pathString.split(":");
                if (pathAndWorkDir.length != 2) {
                    continue;
                }
                Path productPath = projectsFolderPath.resolve(pathAndWorkDir[0]);
                if (FileUtils.exists(productPath)) {
                    list.put(productPath, pathAndWorkDir[1]);
                }
            } else {
                Path  resolve = projectsFolderPath.resolve(trim);
                if (FileUtils.exists(resolve)) {
                    list.put(resolve, null);
                }
            }
        }
        productsPathsAndWorkDirs = list;
        Stream<Path> allModules = Stream.concat(Arrays.stream(bundlesPathsString.split(";"))
            .map(Path::of), Arrays.stream(featuresPathsString.split(";")).map(Path::of));
        Set<Path> collect = allModules.collect(Collectors.toSet());
        Set<Path> set = new HashSet<>();
        for (Path path : collect) {
            Path root = path;
            while (root.getParent() != null) {
                root = root.getParent();
            }
            Path resolvedRoot = projectsFolderPath.resolve(root);
            if (FileUtils.exists(resolvedRoot)) {
                set.add(resolvedRoot);
            }
        }
        modulesRoots = set;
        String additionalModuleRootsString = (String) settings.get(ConfigurationConstants.ADDITIONAL_MODULE_ROOTS_PARAM);
        if (additionalModuleRootsString != null) {
            Set<Path> additionalModuleRoots = Arrays.stream(additionalModuleRootsString.split(";"))
                .map(String::trim)
                .map(projectsFolderPath::resolve).collect(Collectors.toSet());
            modulesRoots.addAll(additionalModuleRoots);
        }
        var testBundlesPathsString = (String) settings.get(ConfigurationConstants.TEST_BUNDLE_PATHS_PARAM);
        testBundlesPaths =
            Arrays.stream(testBundlesPathsString.split(";"))
                .map(String::trim)
                .map(projectsFolderPath::resolve)
                .filter(FileUtils::exists)
                .collect(Collectors.toList());
        var additionalLibrariesString = (String) settings.get(ConfigurationConstants.ADDITIONAL_LIBRARIES_PATHS_PARAM);
        if (additionalLibrariesString != null) {
            this.additionalLibraries = Arrays.stream(additionalLibrariesString.split(";"))
                .map(String::trim)
                .map(projectsFolderPath::resolve)
                .filter(FileUtils::exists)
                .collect(Collectors.toList());;
        }
        String ideaConfigurationFilesString = (String) settings.get(ConfigurationConstants.IDEA_CONFIGURATION_FILES_PATHS_PARAM);
        if (ideaConfigurationFilesString != null) {
            this.ideaConfigurationFiles = Arrays.stream(ideaConfigurationFilesString.split(";"))
                .map(String::trim)
                .map(projectsFolderPath::resolve)
                .filter(FileUtils::exists)
                .collect(Collectors.toList());
        }
        this.projectsFolderPath = projectsFolderPath;
    }

    public @Nonnull Collection<Path> getFeaturesLocations() {
        return featuresPaths;
    }

    public @Nonnull Collection<Path> getModulesRoots() {
        return modulesRoots;
    }

    public @Nonnull Collection<Path> getBundlesLocations() {
        return bundlesPaths;
    }

    public @Nonnull Collection<Path> getTestBundlesPaths() {
        return testBundlesPaths;
    }

    public @Nonnull Path getEclipsePath() {
        return eclipsePath;
    }

    public @Nonnull Path getEclipsePluginsPath() {
        return eclipsePluginsPath;
    }

    public Map<Path, String> getProductsPathsAndWorkDirs() {
        return productsPathsAndWorkDirs;
    }

    public @Nullable List<Path> getAdditionalLibraries() {
        return additionalLibraries;
    }

    @Nullable
    public List<Path> getAdditionalIMlModules() {
        return additionalIMlModules;
    }

    @NotNull
    public Path getEclipseFeaturesPath() {
        return eclipseFeaturesPath;
    }

    @NotNull
    public Path getImlModulesPath() {
        return imlModules;
    }

    @Nullable
    public  List<Path> getIdeaConfigurationFiles() {
        return ideaConfigurationFiles;
    }

    public List<Path> getAdditionalRepositoriesPaths() {
        return additionalRepositoriesPaths;
    }

    public Path getProjectsFolderPath() {
        return projectsFolderPath;
    }
}
