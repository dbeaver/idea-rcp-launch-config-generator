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
package org.jkiss.tools.rcplaunchconfig;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ConfigFileManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigFileManager.class);

    private static final String CONFIG_FILE_NAME = "config.properties";

    public static @Nonnull Properties readSettingsFile(Path configFilePath) throws IOException {
        if (!Files.exists(configFilePath)) {
            throw new IOException("Config file '" + configFilePath + "' not found");
        }
        try (var reader = Files.newBufferedReader(configFilePath)) {
            var result = new Properties();
            result.load(reader);
            return result;
        }
    }

}
