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

import org.jkiss.tools.rcplaunchconfig.p2.P2RepositoryManager;
import org.jkiss.tools.rcplaunchconfig.p2.repository.exception.RepositoryInitialisationError;
import org.jkiss.tools.rcplaunchconfig.producers.ConfigIniProducer;
import org.jkiss.tools.rcplaunchconfig.producers.DevPropertiesProducer;
import org.jkiss.tools.rcplaunchconfig.producers.iml.IMLConfigurationProducer;
import org.jkiss.tools.rcplaunchconfig.resolvers.DynamicImportsResolver;
import org.jkiss.tools.rcplaunchconfig.resolvers.PluginResolver;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;
import org.jkiss.tools.rcplaunchconfig.xml.CategoryXMLFileParser;
import org.jkiss.tools.rcplaunchconfig.xml.XmlReader;
import org.jkiss.utils.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EntryPoint {
    private static final Logger log = LoggerFactory.getLogger(EntryPoint.class);

    public static void main(String[] args) throws IOException, XMLStreamException, RepositoryInitialisationError {
        try {
            launchGenerate(args);
        } catch (Exception exception) {
            exception.printStackTrace(System.out);
        }
    }

    private static void launchGenerate(String[] args) throws IOException, RepositoryInitialisationError, XMLStreamException {
        var params = new Params();
        log.info("Process started with the following arguments: " + Arrays.toString(args));
        params.init(args);
        log.info("Dependency folder location: " + params.eclipsePath);

        var settings = ConfigFileManager.readSettingsFile(params.configFilePath);

        var pathsManager = PathsManager.INSTANCE;
        pathsManager.init(settings, params.projectsFolderPath, params.eclipsePath);
        P2RepositoryManager p2RepositoryManager = P2RepositoryManager.INSTANCE;
        p2RepositoryManager.init(settings, params.eclipseVersion);
        if (log.isDebugEnabled()) {
            var featuresPaths = pathsManager.getFeaturesLocations().stream()
                .map(it -> it.toAbsolutePath().toString())
                .collect(Collectors.joining("\n  "));
            var bundlesPaths = pathsManager.getBundlesLocations().stream()
                .map(it -> it.toAbsolutePath().toString())
                .collect(Collectors.joining("\n  "));
            var productPaths = pathsManager.getProductsPathsAndWorkDirs().entrySet().stream()
                .map(it -> it.getKey().toAbsolutePath().toString())
                .collect(Collectors.joining("\n  "));
            log.debug("Search dependencies for '{}' in Eclipse folder '{}' and projects:\n  {}\n  {}",
                productPaths,
                pathsManager.getEclipsePluginsPath(),
                featuresPaths,
                bundlesPaths
            );
        }
        for (Map.Entry<Path, String> productPath : pathsManager.getProductsPathsAndWorkDirs().entrySet()) {
            log.info("Target location: " + productPath);

            var result = new Result();
            result.setWorkDir(productPath.getValue());
            XmlReader.INSTANCE.parseXmlFile(result, productPath.getKey().toFile());
            new DynamicImportsResolver()
                .start(result, p2RepositoryManager.getLookupCache());

            var resultPath = params.resultFilesPath;
            resultPath = resultPath.resolve(productPath.getKey().getFileName());
            try {
                Files.createDirectories(resultPath.getParent());
            } catch (Throwable throwable) {
                log.debug("Error creating target parent directories");
            }
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
                    productPath.getKey().getParent().resolve(result.getProductName() + ".product.launch"),
                    launchConfig);
            }
            {
                log.info("Loading test bundles");
                PluginResolver.resolveTestBundles(result);
            }
            {
                IMLConfigurationProducer.INSTANCE.generateIMLFiles(result, resultPath);
            }
            List<Path> additionalLibraries = PathsManager.INSTANCE.getAdditionalLibraries();
            if (additionalLibraries != null) {
                for (Path additionalLibrary : additionalLibraries) {
                    FileUtils.copyFolder(additionalLibrary, PathsManager.INSTANCE.getEclipsePath(), false);
                }
            }
        }
        if (!CommonUtils.isEmpty(pathsManager.getAdditionalRepositoriesPaths())) {
            Result result = new Result();
            for (Path additionalRepositoriesPath : pathsManager.getAdditionalRepositoriesPaths()) {
                try (Stream<Path> stream = Files.walk(additionalRepositoriesPath)) {
                    List<Path> categoryXMLS = stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().equals("category.xml"))
                        .toList();
                    for (Path categoryXML : categoryXMLS) {
                        log.info("Generating config for " + categoryXML);
                        CategoryXMLFileParser.parseCategoryXML(result, categoryXML);
                    }
                } catch (IOException e) {
                    log.error("Error reading the repository " + e);
                }
            }
            log.info(result.getBundlesByNames().size() + " additional bundles to resolve found");
            IMLConfigurationProducer.INSTANCE.generateIMLFiles(result, null);

        }
        IMLConfigurationProducer.INSTANCE.generateImplConfiguration();
    }
}
