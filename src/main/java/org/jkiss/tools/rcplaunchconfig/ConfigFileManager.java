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
