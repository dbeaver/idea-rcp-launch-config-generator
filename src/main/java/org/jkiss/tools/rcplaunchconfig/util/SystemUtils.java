package org.jkiss.tools.rcplaunchconfig.util;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.p2.P2BundleLookupCache;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2BundleInfo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class SystemUtils {
    public static boolean matchesDeclaredOS(String ws, String os, String arch) {
        if (ws != null && !ws.equals(BundleInfo.currentWS)) {
            return false;
        }
        if (os != null && !os.equals(BundleInfo.currentOS)) {
            return false;
        }
        if (arch != null && !arch.equals(BundleInfo.currentArch)) {
            return false;
        }
        return true;
    }

    public static Path extractConfigFromJar(Path artifactJar, String config) throws IOException {
        try (JarFile jarFile = new JarFile(artifactJar.toFile())) {
            JarEntry jarEntry = jarFile.getJarEntry(config);
            try (InputStream inputStream = jarFile.getInputStream(jarEntry)) {
                Path configFile = Files.createTempFile("dbeaver", ".tmp");
                Files.copy(inputStream, configFile, StandardCopyOption.REPLACE_EXISTING);
                return configFile;
            }
        }
    }

    public static boolean extractJarToFolder(Path jarPath, Path folderPath) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Iterator<JarEntry> iterator = jarFile.entries().asIterator();
            while (iterator.hasNext()) {
                JarEntry entry = iterator.next();
                Path childPath = folderPath.resolve(entry.getName());
                if (entry.isDirectory()) {
                    if (!childPath.toFile().exists()) {
                        childPath.toFile().mkdirs();
                    }
                } else {
                    try (InputStream inputStream = jarFile.getInputStream(entry)) {
                        if (!childPath.toFile().exists()) {
                            childPath.toFile().getParentFile().mkdirs();
                        }
                        Files.copy(inputStream, childPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        return true;
    }

    @Nullable
    public static Path tryToDownloadFile(@NotNull URI fileURI, @Nullable Path path)  {
        try {
            if (tryToLoadFile(fileURI)) {
                InputStream stream = fileURI.toURL().openStream();
                if (path == null) {
                    path = Files.createTempFile("dbeaver", ".jar");
                }
                Files.copy(stream, path, StandardCopyOption.REPLACE_EXISTING);
                return path;
            }
        } catch (IOException | URISyntaxException e) {
            return null;
        }
        return null;
    }

    public static boolean tryToLoadFile(@NotNull URI artifactsURI) throws IOException, URISyntaxException {
        HttpURLConnection httpURLConnection = (HttpURLConnection) artifactsURI.toURL().openConnection();
        boolean fileExist = httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK;
        httpURLConnection.connect();
        try {
            fileExist = fileExist & httpURLConnection.getURL().toURI().equals(artifactsURI);
        } finally {
            httpURLConnection.disconnect();
        }
        return fileExist;
    }

    @NotNull
    public static Optional<RemoteP2BundleInfo> getMaxVersionRemoteBundle(@NotNull String bundleName, P2BundleLookupCache cache) {
        boolean max = !FileUtils.preferOlderBundles.contains(bundleName);
        Stream<RemoteP2BundleInfo> bundleStream = cache.getRemoteBundlesByNames().get(bundleName).stream();
        Optional<RemoteP2BundleInfo> remoteP2BundleInfo;
        if (max) {
            remoteP2BundleInfo = bundleStream.max(Comparator.comparing(o -> new BundleVersion(o.getBundleVersion())));
        } else {
            remoteP2BundleInfo = bundleStream.min(Comparator.comparing(o -> new BundleVersion(o.getBundleVersion())));
        }
        return remoteP2BundleInfo;
    }
}
