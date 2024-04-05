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
package org.jkiss.tools.rcplaunchconfig.p2;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jkiss.tools.rcplaunchconfig.Artifact;
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.RemoteBundleInfo;
import org.jkiss.tools.rcplaunchconfig.xml.ContentFileHandler;
import org.jkiss.tools.rcplaunchconfig.xml.IndexFileParser;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class P2RepositoryManager {
    private List<URL> repositoriesList;
    private MultiValuedMap<URL, Artifact> multiValuedMap = new ArrayListValuedHashMap();
    public static final P2RepositoryManager INSTANCE = new P2RepositoryManager();

    public Path downloadArtifact(RemoteBundleInfo remoteBundleInfo) {
        try {
            Path eclipsePluginsPath = PathsManager.INSTANCE.getEclipsePluginsPath();
            URL repositoryURL = remoteBundleInfo.getRepositoryURL();
            URI pluginsFolder = repositoryURL.toURI().resolve("plugins/");
            String pluginFilename = remoteBundleInfo.getBundleName() + "_" + remoteBundleInfo.getBundleVersion() + ".jar";
            URI resolve = pluginsFolder.resolve(pluginFilename);
            Path file = eclipsePluginsPath.resolve(pluginFilename);
            return tryToDownloadFile(resolve, file);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public void init(Properties settings, String eclipseVersion, String elkVersion) {
        String repositoriesString = (String) settings.get("repositories");
        try {
            List<URL> list = new ArrayList<>();
            String[] repositories = repositoriesString.replace("${eclipse-version}", eclipseVersion).replace("${elk-version}", elkVersion).split(";");
            for (String s : repositories) {
                String trim = s.trim();
                URI uri = new URI(trim);
                URL url = uri.toURL();
                list.add(url);
            }
            repositoriesList = list;
            for (URL url : repositoriesList) {
                System.out.println("Indexing " + url + " repository artifacts...");
                loadArtifactList(url);
            }
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadArtifactList(URL url) throws URISyntaxException, MalformedURLException {
        URI compositeArtifactJarURI = url.toURI().resolve("compositeArtifacts.jar");
        URI compositeArtifactJarXML = url.toURI().resolve("compositeArtifacts.xml");
        try {
            Path compositeJar = tryToDownloadFile(compositeArtifactJarURI, null);
            Path compositeXML = tryToDownloadFile(compositeArtifactJarXML, null);
            if (compositeJar != null) {
                Path path = extractConfigFromJar(compositeJar, "compositeArtifacts.xml");
                List<String> childrenURLs = IndexFileParser.INSTANCE.listChildrenRepositoriesFromFile(path.toFile());
                for (String childrenURL : childrenURLs) {
                    loadArtifactList(url.toURI().resolve(childrenURL + "/").toURL());
                }
            } else if (compositeXML != null) {
                List<String> childrenURLs = IndexFileParser.INSTANCE.listChildrenRepositoriesFromFile(compositeXML.toFile());
                for (String childrenURL : childrenURLs) {
                    loadArtifactList(url.toURI().resolve(childrenURL + "/").toURL());
                }
            } else {
                loadArtifacts(url);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadArtifacts(URL url) throws URISyntaxException, IOException {
        URI artifactsURI = url.toURI().resolve("artifacts.jar");
        Path path = tryToDownloadFile(artifactsURI, null);
        if (path != null) {
            indexArtifacts(url, path);
        }
        URI contentsURI = url.toURI().resolve("content.jar");
        Path contentPath = tryToDownloadFile(contentsURI, null);
        if (contentPath != null) {
            Path contentsXMl = extractConfigFromJar(contentPath, "content.xml");
            try {
                List<RemoteBundleInfo> remoteBundleInfos = ContentFileHandler.indexContent(contentsXMl.toFile(), url);
                remoteBundleInfos.get(0).resolveBundle();
            } catch (SAXException | ParserConfigurationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Path tryToDownloadFile(URI artifactsURI, Path path)  {
        try {
            if (tryToLoadFile(artifactsURI)) {
                InputStream stream = artifactsURI.toURL().openStream();
                if (path == null) {
                    path = Files.createTempFile("dbeaver", ".jar");
                }
                Files.copy(stream, path, StandardCopyOption.REPLACE_EXISTING);
                return path;
            }
        } catch (IOException | URISyntaxException e) {
            throw null;
        }
        return null;
    }

    private void indexArtifacts(URL url, Path artifactJar) throws IOException {
        Path path = extractConfigFromJar(artifactJar, "artifacts.xml");
        List<Artifact> artifacts = IndexFileParser.INSTANCE.listArtifactsFromIndexFile(path.toFile());

        multiValuedMap.putAll(url, artifacts);
    }

    private static Path extractConfigFromJar(Path artifactJar, String config) throws IOException {
        try (JarFile jarFile = new JarFile(artifactJar.toFile())) {
            JarEntry jarEntry = jarFile.getJarEntry(config);
            InputStream inputStream = jarFile.getInputStream(jarEntry);
            Path configFile = Files.createTempFile("dbeaver", ".tmp");
            Files.copy(inputStream, configFile, StandardCopyOption.REPLACE_EXISTING);
            return configFile;
        }
    }

    private static boolean tryToLoadFile(URI artifactsURI) throws IOException, URISyntaxException {
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


    private P2RepositoryManager() {

    }
}
