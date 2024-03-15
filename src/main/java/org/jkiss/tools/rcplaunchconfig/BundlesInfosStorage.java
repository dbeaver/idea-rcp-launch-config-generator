package org.jkiss.tools.rcplaunchconfig;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jkiss.tools.rcplaunchconfig.resolvers.ManifestParser;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class BundlesInfosStorage {

    public static final Logger log = LoggerFactory.getLogger(BundlesInfosStorage.class);

    private static final Path MANIFEST_PATH = Paths.get("META-INF", "MANIFEST.MF");

    private final MultiValuedMap<String, BundleInfo> bundlesByNames = new ArrayListValuedHashMap<>();
    private final MultiValuedMap<String, BundleInfo> bundlesByExportPackages = new ArrayListValuedHashMap<>();

    public void importData(@Nonnull Collection<Path> bundlesLocations) throws IOException {
        var options = Set.of(FileVisitOption.FOLLOW_LINKS);

        for (var bundlesLocation : bundlesLocations) {
            var fileVisitor = new BundlesLocationVisitor(bundlesLocation, bundlesByNames, bundlesByExportPackages);
            Files.walkFileTree(bundlesLocation, options, 1, fileVisitor);
        }
    }

    public @Nullable Collection<BundleInfo> getBundlesInfosByName(@Nonnull String name) {
        return bundlesByNames.get(name);
    }

    public @Nonnull Collection<BundleInfo> getBundlesByExportPackage(@Nonnull String exportPackageName) {
        return bundlesByExportPackages.get(exportPackageName);
    }


    private static class BundlesLocationVisitor extends SimpleFileVisitor<Path> {
        private final Path startPath;
        private final MultiValuedMap<String, BundleInfo> bundlesByNames;
        private final MultiValuedMap<String, BundleInfo> bundlesByExportPackages;

        private BundlesLocationVisitor(
            @Nonnull Path startPath,
            @Nonnull MultiValuedMap<String, BundleInfo> bundlesByNames,
            @Nonnull MultiValuedMap<String, BundleInfo> bundlesByExportPackages
        ) {
            this.startPath = startPath;
            this.bundlesByNames = bundlesByNames;
            this.bundlesByExportPackages = bundlesByExportPackages;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (dir.equals(startPath)) {
                return FileVisitResult.CONTINUE;
            }
            var manifestPath = dir.resolve(MANIFEST_PATH);
            if (!FileUtils.exists(manifestPath)) {
                log.warn("'{}' is not exists", manifestPath);
                return FileVisitResult.CONTINUE;
            }
            try (var inputStream = new FileInputStream(manifestPath.toFile())) {
                var bundleInfo = ManifestParser.parseManifest(dir, null, new Manifest(inputStream));
                if (bundleInfo != null) {
                    storeBundleInfo(bundleInfo);
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            var fileObj = file.toFile();
            if (!fileObj.getName().endsWith(".jar")) {
                return FileVisitResult.CONTINUE;
            }
            try (var jarFile = new JarFile(fileObj)) {
                var bundleInfo = ManifestParser.parseManifest(file, null, jarFile.getManifest());
                if (bundleInfo != null) {
                    storeBundleInfo(bundleInfo);
                }
            }
            return FileVisitResult.CONTINUE;
        }

        private void storeBundleInfo(@Nonnull BundleInfo bundleInfo) {
            bundlesByNames.put(bundleInfo.getBundleName(), bundleInfo);
            for (var exportPackage : bundleInfo.getExportPackages()) {
                bundlesByExportPackages.put(exportPackage, bundleInfo);
            }
        }
    }
}
