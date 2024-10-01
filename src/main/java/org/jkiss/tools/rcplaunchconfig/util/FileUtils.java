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
package org.jkiss.tools.rcplaunchconfig.util;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.jkiss.code.NotNull;
import org.jkiss.tools.rcplaunchconfig.EntryPoint;
import org.jkiss.tools.rcplaunchconfig.FeatureInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileUtils {

    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);

    private static final String NAME_AND_VERSION_SEPARATOR = "_";

    private static final Object lockObject = new String();
    private static final Map<File, File[]> folderContents = new HashMap<>();
    public static final Set<String> preferOlderBundles = Set.of(
//        "com.google.guava",
        "jakarta.servlet-api",
        "org.apache.commons.io"
    );


    @Nullable
    public static File findFirstChildByPackageName(@Nonnull Path folder, @Nonnull String packageName) {
        var folderFile = folder.toFile();
        if (!folderFile.exists()) {
            log.error("Cannot find '{}'", folder);
            return null;
        }
        return findFirstChildByPackageName(folderFile, packageName);
    }

    @Nullable
    public static File findFirstChildByPackageName(@Nonnull File folder, @Nonnull String packageName) {
        File[] fileList = folderContents.get(folder);
        if (fileList == null) {
            fileList = folder.listFiles();
            if (fileList == null) {
                return null;
            }
            folderContents.put(folder, fileList);
        }
        List<File> files = new ArrayList<>();
        for (File file : fileList) {
            String name = file.getName();
            var candidatePackageName = name;
            int divPos = candidatePackageName.lastIndexOf(NAME_AND_VERSION_SEPARATOR);
            if (divPos != -1 && Character.isDigit(candidatePackageName.charAt(divPos + 1))) {
                candidatePackageName = StringUtils.substringBeforeLast(name, NAME_AND_VERSION_SEPARATOR);
            }
            if (packageName.equals(candidatePackageName)) {
                files.add(file);
            }
        }
        if (files.isEmpty()) {
            return null;
        } else if (files.size() > 1) {
            return getMaxVersion(files.toArray(new File[0]), packageName);
        }
        return files.get(0);
    }

    private static File getMaxVersion(@Nonnull File[] files, @Nonnull String packageName) {
        var filesByVersions = Arrays.stream(files)
            .collect(Collectors.toMap(FileUtils::extractVersion, it -> it));

        BundleVersion properVersion;
        Stream<BundleVersion> bundleStream = filesByVersions.keySet().stream();
        if (preferOlderBundles.contains(packageName)) {
            properVersion = bundleStream.min(BundleVersion::compareTo).orElseThrow();
        } else {
            properVersion = bundleStream.max(BundleVersion::compareTo).orElseThrow();
        }
        var result = filesByVersions.get(properVersion);

        var candidatesNamesList = Arrays.stream(files)
            .map(File::getName)
            .collect(Collectors.joining("\n  "));
        log.debug(
            "Multiple '{}' versions was found. '{}' will be used. Full candidates list:\n  {}",
            packageName,
            result.getName(),
            candidatesNamesList
        );

        return result;
    }

    public static @Nonnull BundleVersion extractVersion(@Nonnull File file) {
        return new BundleVersion(StringUtils.substringAfterLast(file.getName(), NAME_AND_VERSION_SEPARATOR));
    }

    public static @Nonnull Properties readPropertiesFile(@Nonnull Path path) throws IOException {
        Properties result = new Properties();
        try (var in = Files.newBufferedReader(path)) {
            result.load(in);
        }
        return result;
    }

    public static void writePropertiesFile(@Nonnull Path path, @Nonnull Map<String, String> properties)
        throws IOException {
        try (var out = Files.newBufferedWriter(path, StandardOpenOption.CREATE_NEW)) {
            storeProperties(out, "Generated by eclipse-plugins-resolver", properties);
        }
    }

    public static void storeProperties(BufferedWriter bw, String comments, @Nonnull Map<String, String> properties) {
        try {
            if (comments != null) {
                bw.write("#" + comments);
            }
            bw.write("#" + new Date());
            bw.newLine();
            for (Map.Entry<String, String> e : properties.entrySet()) {
                String key = e.getKey();
                String val = e.getValue();
                bw.write(key + "=" + val);
                bw.newLine();
            }
            bw.flush();
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    public static boolean exists(@Nonnull Path path) {
        return path.toFile().exists();
    }

    public static void removeAllFromDir(@Nonnull Path targetPath) throws IOException {
        if (targetPath.toFile().exists()) {
            Files.walkFileTree(targetPath,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        if (exc != null) {
                            throw exc;
                        }
                        if (!targetPath.equals(dir)) {
                            Files.delete(dir);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (!targetPath.equals(file)) {
                            Files.delete(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
        } else {
            var resultPathFile = targetPath.toFile();
            if (!resultPathFile.exists() && !resultPathFile.mkdirs()) {
                throw new IllegalStateException("Failed to create dirs: '" + targetPath + "'");
            }
        }
    }

    public static Path extractConfigFromJar(Path artifactJar, String config) throws IOException {
        try (JarFile jarFile = new JarFile(artifactJar.toFile())) {
            JarEntry jarEntry = jarFile.getJarEntry(config);
            try (InputStream inputStream = jarFile.getInputStream(jarEntry)) {
                Path configFile = Files.createTempFile("dbeaver", ".tmp");
                configFile.toFile().deleteOnExit();
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
        } catch (Exception e) {
            log.error("Error during opening jar file for " + jarPath);
            throw e;
        }
        return true;
    }

    @org.jkiss.code.Nullable
    public static Path tryToDownloadFile(@NotNull URI fileURI, @org.jkiss.code.Nullable Path path, boolean checkExisting)  {
        try {
            if (!checkExisting | tryToLoadFile(fileURI)) {
                try (InputStream stream = fileURI.toURL().openStream()) {
                    if (path == null) {
                        path = Files.createTempFile("dbeaver", ".jar");
                        path.toFile().deleteOnExit();
                        Files.copy(stream, path, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        boolean directory = Files.isDirectory(path);
                        Path tempPath;
                        if (directory) {
                            tempPath = Files.createTempDirectory(String.valueOf(path.getFileName()));
                        } else {
                            String fileName = path.getFileName().toString();
                            tempPath = Files.createTempFile("dbeaver", fileName.substring(fileName.lastIndexOf(".")));
                        }
                        Files.copy(stream, tempPath, StandardCopyOption.REPLACE_EXISTING);
                        // Yes this is a full lock, but I *really* don't want anything happening during copy to guarantee avoiding half-copy
                        synchronized (lockObject){
                            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    return path;
                }
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


    public static void copyFolder(Path sourceFolder, Path target, boolean replaceExisting) throws IOException {
        try (Stream<Path> fileStream = Files.walk(sourceFolder)) {
            fileStream
                .forEach(source -> {
                        Path destination = Paths.get(target.resolve(sourceFolder.getFileName()).toString(), source.toString()
                            .substring(sourceFolder.toString().length()));
                        try {
                            Files.copy(
                                source,
                                destination,
                                replaceExisting ? StandardCopyOption.REPLACE_EXISTING : StandardCopyOption.COPY_ATTRIBUTES
                            );
                        } catch (DirectoryNotEmptyException | FileAlreadyExistsException ignore) {

                        } catch (IOException e) {
                            log.error("Error transferring data", e);
                        }
                });
        }
    }
}


