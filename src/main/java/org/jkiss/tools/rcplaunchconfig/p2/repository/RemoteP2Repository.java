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


import org.jkiss.tools.rcplaunchconfig.Artifact;
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.p2.P2BundleLookupCache;
import org.jkiss.tools.rcplaunchconfig.p2.RemoteP2Feature;
import org.jkiss.tools.rcplaunchconfig.p2.repository.exception.RepositoryInitialisationError;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;
import org.jkiss.tools.rcplaunchconfig.util.SystemUtils;
import org.jkiss.tools.rcplaunchconfig.xml.ContentFileHandler;
import org.jkiss.tools.rcplaunchconfig.xml.IndexFileParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

public class RemoteP2Repository implements IRepository<RemoteP2BundleInfo> {
    private static final Logger log = LoggerFactory.getLogger(RemoteP2Repository.class);

    private final URL url;
    private final List<RemoteP2Repository> subRepositories = new ArrayList<>();

    private final Set<RemoteP2BundleInfo> remoteP2BundleInfoSet = new LinkedHashSet<>();
    private final Set<RemoteP2Feature> remoteP2FeatureSet = new LinkedHashSet<>();

    private List<Artifact> indexedArtifacts;

    public RemoteP2Repository(URL url) {
        this.url = url;
    }

    @Override
    public String getName() {
        return url.toString();
    }

    private Map<String, String> eclipseIDToMavenID = new LinkedHashMap<>();
    public boolean isIndexed(String id, String version) {
        for (Artifact indexedArtifact : indexedArtifacts) {
            if (indexedArtifact.id().equalsIgnoreCase(id)
                && indexedArtifact.version().toString().equals(version)) {
                return true;
            }
        }
        return false;
    }

    public void linkEclipseIdToArtifactID(String eclipseID, String artifactID) {
        eclipseIDToMavenID.put(eclipseID, artifactID);
    }

    public Path resolveBundle(RemoteP2BundleInfo remoteP2BundleInfo) {
        try {
            Path eclipsePluginsPath = PathsManager.INSTANCE.getEclipsePluginsPath();
            URI pluginsFolder = url.toURI().resolve("plugins/");
            String pluginFilename = remoteP2BundleInfo.getBundleName() + "_" + remoteP2BundleInfo.getBundleVersion() + ".jar";
            URI resolve = pluginsFolder.resolve(pluginFilename);
            Path file = eclipsePluginsPath.resolve(pluginFilename);
            return SystemUtils.tryToDownloadFile(resolve, file);
        } catch (URISyntaxException e) {
            log.error("Error resolving the artifact", e);
            return null;
        }
    }

    public Path resolveFeature(RemoteP2Feature remoteP2Feature) {
        try {
            Path eclipseFeaturesPath = PathsManager.INSTANCE.getEclipseFeaturesPath();
            URI pluginsFolder = url.toURI().resolve("features/");
            String featureName = remoteP2Feature.getName() + "_" + remoteP2Feature.getVersion();
            URI resolve = pluginsFolder.resolve(featureName + ".jar");
            Path filePath = eclipseFeaturesPath.resolve(featureName);
            Path jarPath = SystemUtils.tryToDownloadFile(resolve, null);
            SystemUtils.extractJarToFolder(jarPath, filePath);
            return filePath;
        } catch (URISyntaxException | IOException e) {
            log.error("Error resolving the artifact", e);
            return null;
        }
    }


    @Override
    public void init(P2BundleLookupCache cache) throws RepositoryInitialisationError {
        try {
            URI compositeArtifactJarURI = url.toURI().resolve("compositeArtifacts.jar");
            URI compositeArtifactJarXML = url.toURI().resolve("compositeArtifacts.xml");
            Path compositeJar = SystemUtils.tryToDownloadFile(compositeArtifactJarURI, null);
            Path compositeXML = SystemUtils.tryToDownloadFile(compositeArtifactJarXML, null);
            if (compositeJar != null) {
                Path path = SystemUtils.extractConfigFromJar(compositeJar, "compositeArtifacts.xml");
                List<String> childrenURLs = IndexFileParser.INSTANCE.listChildrenRepositoriesFromFile(path.toFile());
                for (String childrenURL : childrenURLs) {
                    RemoteP2Repository childP2repository = new RemoteP2Repository(url.toURI().resolve(childrenURL + "/").toURL());
                    subRepositories.add(childP2repository);
                    log.info("Indexing " + childP2repository.getName() + " sub repository...");
                    childP2repository.init(cache);
                }
            } else if (compositeXML != null) {
                List<String> childrenURLs = IndexFileParser.INSTANCE.listChildrenRepositoriesFromFile(compositeXML.toFile());
                for (String childrenURL : childrenURLs) {
                    RemoteP2Repository childP2repository = new RemoteP2Repository(url.toURI().resolve(childrenURL + "/").toURL());
                    subRepositories.add(childP2repository);
                    childP2repository.init(cache);
                }
            } else {
                loadArtifacts(cache);
            }
        } catch (Exception exception) {
            throw new RepositoryInitialisationError("Error during" + getName() + " repository initialisation", exception);
        }
    }

    private void loadArtifacts(P2BundleLookupCache cache) throws RepositoryInitialisationError {
        try {
            URI artifactsURI = url.toURI().resolve("artifacts.jar");
            Path path = SystemUtils.tryToDownloadFile(artifactsURI, null);
            if (path != null) {
                indexArtifacts(path);
            }
            URI contentsURI = url.toURI().resolve("content.jar");
            Path contentPath = SystemUtils.tryToDownloadFile(contentsURI, null);
            if (contentPath != null) {
                Path contentsXMl = SystemUtils.extractConfigFromJar(contentPath, "content.xml");
                ContentFileHandler.indexContent(this, contentsXMl.toFile(), cache);
                log.info("Repository " + getName() + " indexed, " +
                    (remoteP2BundleInfoSet.size() + remoteP2FeatureSet.size()) + " artifacts found");
            }
        } catch (Exception e) {
            throw new RepositoryInitialisationError("Error during repository indexing", e);
        }
    }

    public void addRemoteBundles(Collection<RemoteP2BundleInfo> bundleInfos) {
        remoteP2BundleInfoSet.addAll(bundleInfos);
    }

    public void addRemoteFeatures(Collection<RemoteP2Feature> features) {
        remoteP2FeatureSet.addAll(features);
    }

    private void indexArtifacts(Path artifactJar) throws IOException, SAXException, RepositoryInitialisationError {
        Path path = SystemUtils.extractConfigFromJar(artifactJar, "artifacts.xml");
        indexedArtifacts = IndexFileParser.INSTANCE.listArtifactsFromIndexFile(path.toFile());
    }
}
