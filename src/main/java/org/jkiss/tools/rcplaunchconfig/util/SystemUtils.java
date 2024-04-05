package org.jkiss.tools.rcplaunchconfig.util;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class SystemUtils {
    public static boolean matchesDeclaredOS(String ws, String os, String arch) {
        if (ws != null && !ws.equals(BundleInfo.currentWS)) {
            return true;
        }
        if (os != null && !os.equals(BundleInfo.currentOS)) {
            return true;
        }
        if (arch != null && !arch.equals(BundleInfo.currentArch)) {
            return true;
        }
        return false;
    }

    public static Path extractConfigFromJar(Path artifactJar, String config) throws IOException {
        try (JarFile jarFile = new JarFile(artifactJar.toFile())) {
            JarEntry jarEntry = jarFile.getJarEntry(config);
            InputStream inputStream = jarFile.getInputStream(jarEntry);
            Path configFile = Files.createTempFile("dbeaver", ".tmp");
            Files.copy(inputStream, configFile, StandardCopyOption.REPLACE_EXISTING);
            return configFile;
        }
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
}
