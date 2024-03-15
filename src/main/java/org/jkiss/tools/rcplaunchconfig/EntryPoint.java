/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp
 *
 * All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of DBeaver Corp and its suppliers, if any.
 * The intellectual and technical concepts contained
 * herein are proprietary to DBeaver Corp and its suppliers
 * and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from DBeaver Corp.
 */
package org.jkiss.tools.rcplaunchconfig;

import org.jkiss.tools.rcplaunchconfig.producers.ConfigIniProducer;
import org.jkiss.tools.rcplaunchconfig.producers.DevPropertiesProducer;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;
import org.jkiss.tools.rcplaunchconfig.xml.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

public class EntryPoint {
    private static final Logger log = LoggerFactory.getLogger(EntryPoint.class);

    public static void main(String[] args) throws IOException, XMLStreamException {
        var params = new Params();
        params.init(args);
        if (!params.productFilePath.toFile().exists()) {
            log.error("'{}' is not exists", params.productFilePath);
            return;
        }

        var pathsManager = PathsManager.INSTANCE;
        pathsManager.init(params.configFilePath, params.projectsFolderPath, params.eclipsePath);

        if (log.isDebugEnabled()) {
            var featuresPaths = pathsManager.getFeaturesLocations().stream()
                .map(it -> it.toAbsolutePath().toString())
                .collect(Collectors.joining("\n  "));
            var bundlesPaths = pathsManager.getBundlesLocations().stream()
                .map(it -> it.toAbsolutePath().toString())
                .collect(Collectors.joining("\n  "));
            log.debug("Search dependencies for '{}' in Eclipse folder '{}' and projects:\n  {}\n  {}",
                params.productFilePath,
                pathsManager.getEclipsePluginsPath(),
                featuresPaths,
                bundlesPaths
            );
        }

        var result = new Result();
        XmlReader.INSTANCE.parseXmlFile(result, params.productFilePath.toFile());
        new DynamicImportsResolver()
            .start(result);

        var resultPath = params.resultFilesPath;
        try {
            FileUtils.removeAllFromDir(resultPath);
        } catch (Throwable e) {
            log.debug("Error deleting target folder", e);
        }

        {
            // dev props
            var devProperties = DevPropertiesProducer.generateDevProperties(result.getBundlesByNames().values());
            FileUtils.writePropertiesFile(resultPath.resolve("dev.properties"), devProperties);
        }
        {
            // config ini
            var configIni = ConfigIniProducer.generateConfigIni(
                result.getOsgiSplashPath(),
                result.getBundlesByNames().values()
            );
            FileUtils.writePropertiesFile(resultPath.resolve("config.ini"), configIni);
        }
        {
            // debug launch
            String launchConfig = ConfigIniProducer.generateProductLaunch(params, result);
            Files.writeString(
                params.productFilePath.getParent().resolve(result.getProductName() + ".product.launch"),
                launchConfig);
        }
    }
}
