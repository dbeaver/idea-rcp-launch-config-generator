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

import org.jkiss.tools.rcplaunchconfig.p2.repository.IRepository;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2BundleInfo;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2Repository;
import org.jkiss.tools.rcplaunchconfig.p2.repository.exception.RepositoryInitialisationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class P2RepositoryManager {
    private static final Logger log = LoggerFactory.getLogger(RemoteP2BundleInfo.class);

    public static final P2RepositoryManager INSTANCE = new P2RepositoryManager();
    private List<IRepository<?>> rootRepositories;
    private final P2BundleLookupCache cache = new P2BundleLookupCache();

    public void init(Properties settings, String eclipseVersion) throws RepositoryInitialisationError {
        String repositoriesString = (String) settings.get("repositories");
        String reposititoryString = repositoriesString.replace(
            "${eclipse-version}",
                eclipseVersion);
        String[] repositories = reposititoryString.split(";");
        indexRepositories(repositories);
        for (IRepository<?> repository : rootRepositories) {
            log.info("Indexing " + repository.getName() + " repository...");
            repository.init(cache);
        }
    }

    private void indexRepositories(String[] repositories) throws RepositoryInitialisationError {
        List<IRepository<?>> list =  new ArrayList<>();
        try {
            for (String s : repositories) {
                String trim = s.trim();
                URI uri = new URI(trim);
                try {
                    URL url = uri.toURL();
                    list.add(new RemoteP2Repository(url));
                } catch (MalformedURLException e) {
                    throw new UnsupportedOperationException("Local p2 repositories are not supported");
                }
            }
        } catch (Exception error) {
            throw new RepositoryInitialisationError("Error during repository indexing", error);
        }
        rootRepositories = list;
    }

    public P2BundleLookupCache getLookupCache() {
        return cache;
    }

    private P2RepositoryManager() {

    }
}
