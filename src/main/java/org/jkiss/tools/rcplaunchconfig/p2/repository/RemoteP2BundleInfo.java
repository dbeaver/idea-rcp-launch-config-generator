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
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.p2.P2RepositoryManager;
import org.jkiss.tools.rcplaunchconfig.resolvers.DynamicImportsResolver;
import org.jkiss.tools.rcplaunchconfig.resolvers.ManifestParser;
import org.jkiss.tools.rcplaunchconfig.util.Version;
import org.jkiss.tools.rcplaunchconfig.util.VersionRange;
import org.jkiss.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class RemoteP2BundleInfo extends BundleInfo {
    private static final Logger log = LoggerFactory.getLogger(RemoteP2BundleInfo.class);

    private final RemoteP2Repository repository;
    private final boolean zipped;
    private final Lock lock = new ReentrantLock();

    private RemoteP2BundleInfo(
        @NotNull RemoteP2Repository repositoryURL,
        @NotNull String bundleName,
        @NotNull String bundleVersion,
        @NotNull List<String> classpathLibs,
        @NotNull List<Pair<String, VersionRange>> requireBundles,
        @NotNull Set<Pair<String, Version>> exportPackages,
        @NotNull Set<String> reexportedBundles,
        @NotNull Set<Pair<String, VersionRange>> importPackages,
        @Nullable Integer startLevel,
        boolean zipped
    ) {
        super(
            null,
            bundleName,
            bundleVersion,
            classpathLibs,
            requireBundles,
            reexportedBundles,
            exportPackages,
            importPackages,
            List.of(),
            null,
            startLevel
        );
        this.repository = repositoryURL;
        this.zipped = zipped;
        this.path = getPluginPath();
    }

    public boolean resolveBundle() {
        while (!lock.tryLock()) {
            // Already resolving by another thread
            Thread.onSpinWait();
        }
        try {
            if (path.toFile().exists()) {
                return true;
            }
            log.info("Downloading %s_%s from %s... ".formatted(getBundleName(), getBundleVersion(), getRepository().getName()));
            log.debug("Thread number %s used to download %s".formatted(Thread.currentThread().getName(), getBundleName()));
            Path filePath = repository.resolveBundle(this);
            if (filePath == null) {
                return false;
            }
            if (path.toFile().isDirectory()) {
                File manifestFile = path.resolve(DynamicImportsResolver.MANIFEST_PATH).toFile();
                if (!manifestFile.exists()) {
                    log.error("Cannot find '{}'", manifestFile.getPath());
                    return false;
                }
                try (var inputStream = new FileInputStream(manifestFile)) {
                    var manifest = new Manifest(inputStream);
                    this.classpathLibs = ManifestParser.parseBundleClasspath(manifest.getMainAttributes());
                    this.reexportedBundles = ManifestParser.parseReexportedBundles(manifest.getMainAttributes());
                    this.fragmentHost = ManifestParser.parseFragmentHost(manifest.getMainAttributes());
                } catch (IOException e) {
                    log.error("Cannot load bundle %s".formatted(getBundleName()), e);
                    return false;
                }
            } else {
                try (var jarFile = new JarFile(path.toFile())) {
                    var manifest = jarFile.getManifest();
                    this.classpathLibs = ManifestParser.parseBundleClasspath(manifest.getMainAttributes());
                    this.reexportedBundles = ManifestParser.parseReexportedBundles(manifest.getMainAttributes());
                    this.fragmentHost = ManifestParser.parseFragmentHost(manifest.getMainAttributes());
                } catch (IOException e) {
                    log.error("Cannot load bundle %s".formatted(getBundleName()), e);
                    return false;
                }
            }
            Collection<RemoteP2BundleInfo> sourceBundle = P2RepositoryManager.INSTANCE.getLookupCache().getRemoteBundlesByName(getBundleName() + ".source");
            if (!sourceBundle.isEmpty()) {
                for (RemoteP2BundleInfo remoteP2BundleInfo : sourceBundle) {
                    if (remoteP2BundleInfo.getBundleVersion().equalsIgnoreCase(getBundleVersion())) {
                        remoteP2BundleInfo.resolveBundle();
                    }
                }
            }
            log.info("%s download completed".formatted(getBundleName()));
            return true;
        } finally {
            lock.unlock();
        }
    }

    @NotNull
    @Override
    public List<String> getClasspathLibs() {
        while (!lock.tryLock()) {
            Thread.onSpinWait();
        }
        try {
            return super.getClasspathLibs();
        } finally {
            lock.unlock();
        }
    }

    @NotNull
    @Override
    public Set<String> getReexportedBundles() {
        while (!lock.tryLock()) {
            Thread.onSpinWait();
        }
        try {
            return super.getReexportedBundles();
        } finally {
            lock.unlock();
        }
    }



    @Nullable
    @Override
    public Pair<String, VersionRange> getFragmentHost() {
        while (!lock.tryLock()) {
            Thread.onSpinWait();
        }
        try {
            return super.getFragmentHost();
        } finally {
            lock.unlock();
        }
    }

    private Path getPluginPath() {
        String fileName = getBundleName() + "_" + getBundleVersion();
        if (!zipped) {
            fileName += ".jar";
        }
        Path eclipsePluginsPath = PathsManager.INSTANCE.getEclipsePluginsPath();
        return eclipsePluginsPath.resolve(fileName);
    }

    boolean isZipped() {
        return zipped;
    }

    public RemoteP2Repository getRepository() {
        return repository;
    }

    public static class RemoteBundleInfoBuilder {
        private String bundleName;
        private String bundleVersion;
        private List<String> classpathLibs;

        private RemoteP2Repository repository;
        private final List<Pair<String, VersionRange>> requireBundles = new ArrayList<>();
        private Set<String> reexportedBundles = new HashSet<>();
        private final Set<Pair<String, Version>> exportPackages = new LinkedHashSet<>();
        private final Set<Pair<String, VersionRange>> importPackages = new LinkedHashSet<>();
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
                reexportedBundles,
                importPackages,
                startLevel,
                zipped
            );
        }

        public RemoteBundleInfoBuilder addReexportedBundle(String reexportedBundle) {
            this.reexportedBundles.add(reexportedBundle);
            return this;
        }

        public RemoteBundleInfoBuilder bundleName(String bundleName) {
            this.bundleName = bundleName;
            return this;
        }

        public RemoteBundleInfoBuilder version(String bundleVersion) {
            this.bundleVersion = bundleVersion;
            return this;
        }

        public RemoteBundleInfoBuilder repositoryURL(RemoteP2Repository repository) {
            this.repository = repository;
            return this;
        }

        public RemoteBundleInfoBuilder addToRequiredBundles(String requiredBundle, VersionRange range) {
            this.requireBundles.add(new Pair<>(requiredBundle, range));
            return this;
        }

        public RemoteBundleInfoBuilder addToExportPackage(String exportPackage, Version version) {
            this.exportPackages.add(new Pair<>(exportPackage, version));
            return this;
        }

        public RemoteBundleInfoBuilder addToRequiredPackages(String importPackage, VersionRange range) {
            this.importPackages.add(new Pair<>(importPackage, range));
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