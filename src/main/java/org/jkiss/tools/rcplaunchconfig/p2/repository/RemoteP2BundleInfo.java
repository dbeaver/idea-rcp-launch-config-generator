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

package org.jkiss.tools.rcplaunchconfig.p2.repository;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.DynamicImportsResolver;
import org.jkiss.tools.rcplaunchconfig.resolvers.ManifestParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class RemoteP2BundleInfo extends BundleInfo {
    private static final Logger log = LoggerFactory.getLogger(RemoteP2BundleInfo.class);

    private final RemoteP2Repository repository;
    private final boolean zipped;

    private RemoteP2BundleInfo(
        @NotNull RemoteP2Repository repositoryURL,
        @NotNull String bundleName,
        @NotNull String bundleVersion,
        @NotNull List<String> classpathLibs,
        @NotNull List<String> requireBundles,
        @NotNull Set<String> exportPackages,
        @NotNull Set<String> importPackages,
        @Nullable Integer startLevel,
        boolean zipped
    ) {
        super(null, bundleName, bundleVersion, classpathLibs, requireBundles, exportPackages, importPackages, startLevel);
        this.repository = repositoryURL;
        this.zipped = zipped;
    }

    public boolean resolveBundle() {
        if (path != null) {
            return true;
        }
        log.info("Downloading " + getBundleName() + "_" + getBundleVersion() + " from " + getRepository().getName() + "... ");
        Path filePath = repository.resolveBundle(this);
        if (filePath == null) {
            return false;
        }
        this.path = filePath;
        if (path.toFile().isDirectory()) {
            File manifestFile = path.resolve(DynamicImportsResolver.MANIFEST_PATH).toFile();
            if (!manifestFile.exists()) {
                log.error("Cannot find '{}'", manifestFile.getPath());
                return false;
            }
            try (var inputStream = new FileInputStream(manifestFile)) {
                var manifest = new Manifest(inputStream);
                this.classpathLibs = ManifestParser.parseBundleClasspath(manifest.getMainAttributes());
            } catch (IOException e) {
                log.error("Cannot load bundle", e);
                return false;
            }
        } else {
            try (var jarFile = new JarFile(path.toFile())) {
                var manifest = jarFile.getManifest();
                this.classpathLibs = ManifestParser.parseBundleClasspath(manifest.getMainAttributes());
            } catch (IOException e) {
                log.error("Cannot load bundle", e);
                return false;
            }
        }
        return true;
    }

    boolean isZipped() {
        return zipped;
    }
    public RemoteP2Repository getRepository() {
        return repository;
    }

    public static class RemoteBundleInfoBuilder {
        String bundleName;
        String bundleVersion;
        List<String> classpathLibs;

        RemoteP2Repository repository;
        List<String> requireBundles = new ArrayList<>();
        Set<String> exportPackages = new LinkedHashSet<>();
        Set<String> importPackages = new LinkedHashSet<>();
        Integer startLevel;
        private boolean zipped = false;

        public RemoteBundleInfoBuilder() {
        }

        public RemoteP2BundleInfo build() {
            return new RemoteP2BundleInfo(
                repository,
                bundleName,
                bundleVersion,
                classpathLibs,
                requireBundles,
                exportPackages,
                importPackages,
                startLevel,
                zipped
            );
        }

        public RemoteBundleInfoBuilder bundleName(String bundleName) {
            this.bundleName = bundleName;
            return this;
        }

        public RemoteBundleInfoBuilder version(String bundleVersion) {
            this.bundleVersion = bundleVersion;
            return this;
        }

        public RemoteBundleInfoBuilder classpathLibs(List<String> classpathLibs) {
            this.classpathLibs = classpathLibs;
            return this;
        }

        public RemoteBundleInfoBuilder repositoryURL(RemoteP2Repository repository) {
            this.repository = repository;
            return this;
        }

        public RemoteBundleInfoBuilder addToRequiredBundles(String requiredBundle) {
            this.requireBundles.add(requiredBundle);
            return this;
        }

        public RemoteBundleInfoBuilder addToExportPackage(String exportPackage) {
            this.exportPackages.add(exportPackage);
            return this;
        }

        public RemoteBundleInfoBuilder addToRequiredPackages(String importPackage) {
            this.importPackages.add(importPackage);
            return this;
        }

        public RemoteBundleInfoBuilder setStartLevel(int startLevel) {
            this.startLevel = startLevel;
            return this;
        }

        public RemoteBundleInfoBuilder setZipped(boolean zipped) {
            this.zipped = zipped;
            return this;
        }
    }
}