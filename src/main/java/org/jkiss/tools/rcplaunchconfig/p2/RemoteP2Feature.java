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

import org.jkiss.tools.rcplaunchconfig.DynamicImportsResolver;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2BundleInfo;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2Repository;
import org.jkiss.tools.rcplaunchconfig.resolvers.ManifestParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class RemoteP2Feature {
    private static final Logger log = LoggerFactory.getLogger(RemoteP2BundleInfo.class);

    RemoteP2Repository repository;
    String name;
    String version;
    private Path path;

    public RemoteP2Feature(String name, String version, RemoteP2Repository repository) {
        this.repository = repository;
        this.name = name;
        this.version = version;
    }

    public boolean resolveFeature() {
        if (path != null) {
            return true;
        }
        log.info("Downloading " + getName() + "_" + getVersion() + " from " + getRepository().getName() + "... ");
        Path filePath = repository.resolveFeature(this);
        if (filePath == null) {
            return false;
        }
        this.path = filePath;
        return true;
    }
    public RemoteP2Repository getRepository() {
        return repository;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public Path getPath() {
        return path;
    }
}
