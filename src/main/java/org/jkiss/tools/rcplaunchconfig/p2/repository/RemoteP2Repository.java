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


import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.RemoteBundleInfo;
import org.jkiss.tools.rcplaunchconfig.p2.P2BundleLookupCache;
import org.jkiss.tools.rcplaunchconfig.p2.repository.exception.RepositoryInitialisationError;
import org.jkiss.tools.rcplaunchconfig.utils.SystemUtils;
import org.jkiss.tools.rcplaunchconfig.xml.ContentFileHandler;
import org.jkiss.tools.rcplaunchconfig.xml.IndexFileParser;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RemoteP2Repository implements IRepository<RemoteBundleInfo> {
    private final URL url;
    private final List<RemoteP2Repository> subRepositories = new ArrayList<>();

    private final Set<RemoteBundleInfo> remoteBundleInfoSet = new LinkedHashSet<>();

    public RemoteP2Repository(URL url) {
        this.url = url;
    }

    @Override
    public String getName() {
        return url.toString();
    }

    public Path resolveArtifact(RemoteBundleInfo remoteBundleInfo) {
        try {

            Path eclipsePluginsPath = PathsManager.INSTANCE.getEclipsePluginsPath();
            URI pluginsFolder = url.toURI().resolve("plugins/");
            String pluginFilename = remoteBundleInfo.getBundleName() + "_" + remoteBundleInfo.getBundleVersion() + ".jar";
            URI resolve = pluginsFolder.resolve(pluginFilename);
            Path file = eclipsePluginsPath.resolve(pluginFilename);
            return SystemUtils.tryToDownloadFile(resolve, file);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
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

    private void loadArtifacts(P2BundleLookupCache cache) throws URISyntaxException, IOException {
        URI contentsURI = url.toURI().resolve("content.jar");
        Path contentPath = SystemUtils.tryToDownloadFile(contentsURI, null);
        if (contentPath != null) {
            try {
                Path contentsXMl = SystemUtils.extractConfigFromJar(contentPath, "content.xml");
                remoteBundleInfoSet.addAll(ContentFileHandler.indexContent(contentsXMl.toFile(), this, cache));
            } catch (SAXException | ParserConfigurationException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
