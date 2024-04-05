package org.jkiss.tools.rcplaunchconfig;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.tools.rcplaunchconfig.p2.P2RepositoryManager;
import org.jkiss.tools.rcplaunchconfig.resolvers.ManifestParser;
import org.jkiss.tools.rcplaunchconfig.resolvers.PluginResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Manifest;

public class RemoteBundleInfo extends BundleInfo{
    private static final Logger log = LoggerFactory.getLogger(RemoteBundleInfo.class);

    private final URL repositoryURL;
    private RemoteBundleInfo(@NotNull URL repositoryURL, @NotNull String bundleName, @NotNull String bundleVersion, @NotNull List<String> classpathLibs, @NotNull List<String> requireBundles, @NotNull Set<String> exportPackages, @NotNull Set<String> importPackages, @Nullable Integer startLevel) {
        super(null, bundleName, bundleVersion, classpathLibs, requireBundles, exportPackages, importPackages, startLevel);
        this.repositoryURL = repositoryURL;
    }

    public boolean resolveBundle() {
        Path filePath = P2RepositoryManager.INSTANCE.downloadArtifact(this);
        if (filePath == null) {
            return false;
        }
        this.path = filePath;
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
        return true;
    }

    public URL getRepositoryURL() {
        return repositoryURL;
    }

    public static class RemoteBundleInfoBuilder {
        String bundleName;
        String bundleVersion;
        List<String> classpathLibs;

        URL repositoryURL;
        List<String> requireBundles = new ArrayList<>();
        Set<String> exportPackages = new LinkedHashSet<>();
        Set<String> importPackages = new LinkedHashSet<>();
        int startLevel;
        public RemoteBundleInfoBuilder() {
        }

        public RemoteBundleInfo build() {
            return new RemoteBundleInfo(repositoryURL, bundleName, bundleVersion, classpathLibs, requireBundles, exportPackages, importPackages, startLevel);
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

        public void repositoryURL(URL repositoryURL) {
            this.repositoryURL = repositoryURL;
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
    }
}