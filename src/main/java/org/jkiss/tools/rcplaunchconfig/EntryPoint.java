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

import ch.qos.logback.classic.Level;
import com.dbeaver.osgi.dependency.processing.ConfigFileManager;
import com.dbeaver.osgi.dependency.processing.PathsManager;
import com.dbeaver.osgi.dependency.processing.Result;
import com.dbeaver.osgi.dependency.processing.p2.P2RepositoryManager;
import com.dbeaver.osgi.dependency.processing.p2.repository.exception.RepositoryInitialisationError;
import com.dbeaver.osgi.dependency.processing.resolvers.DynamicImportsResolver;
import com.dbeaver.osgi.dependency.processing.resolvers.FeatureResolver;
import com.dbeaver.osgi.dependency.processing.resolvers.PluginResolver;
import com.dbeaver.osgi.dependency.processing.util.DependencyGraph;
import com.dbeaver.osgi.dependency.processing.util.DependencyGraphImpl;
import com.dbeaver.osgi.dependency.processing.util.DependencyGraphStub;
import com.dbeaver.osgi.dependency.processing.util.FileUtils;
import com.dbeaver.osgi.dependency.processing.xml.CategoryXMLFileParser;
import com.dbeaver.osgi.dependency.processing.xml.XmlReader;
import org.jkiss.code.NotNull;
import org.jkiss.tools.rcplaunchconfig.producers.ConfigIniProducer;
import org.jkiss.tools.rcplaunchconfig.producers.DevPropertiesProducer;
import org.jkiss.tools.rcplaunchconfig.producers.iml.IMLConfigurationProducer;
import org.jkiss.utils.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
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
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        boolean generateDependencyTree = !params.notGenerateDependencyTree;
        if (params.debug) {
            logger.setLevel(Level.DEBUG);
        } else {
            logger.setLevel(Level.INFO);
        }
        ForkJoinPool forkJoinPool;
        if (params.singleCoreMode) {
            forkJoinPool = new ForkJoinPool(1);
        } else {
            forkJoinPool = ForkJoinPool.commonPool();
        }
        log.info("Dependency folder location: " + params.eclipsePath);

        var settings = ConfigFileManager.readSettingsFile(params.configFilePath);

        var pathsManager = PathsManager.INSTANCE;
        pathsManager.init(settings, params.projectsFolderPath, params.eclipsePath);
        P2RepositoryManager p2RepositoryManager = P2RepositoryManager.INSTANCE;
        p2RepositoryManager.init(settings, params.eclipseVersion, null);
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
        List<ResultInfo> executionResults = forkJoinPool.submit(() -> pathsManager.getProductsPathsAndWorkDirs().entrySet().parallelStream().map((productPath) -> {
            log.info("Starting generation for {}", productPath);
            log.debug("Thread name {} used for {}", Thread.currentThread().getName(), productPath);
            try {
                Result result = new Result();
                result.setWorkDir(productPath.getValue());
                result.setProductPath(productPath.getKey());
                DependencyGraph dependencyGraph = getGraphImplementation(generateDependencyTree);
                result.setProductGraph(dependencyGraph);
                FeatureResolver.addNewFeatureProject(result.getProductPath());
                dependencyGraph.traverseIntoNode(result.getProductPath().toString());
                DependencyGraph.DependencyNode root = dependencyGraph.getCurrentNode();
                XmlReader.INSTANCE.parseXmlFile(result, productPath.getKey().toFile(), dependencyGraph);
                dependencyGraph.setCurrentNode(root);
                new DynamicImportsResolver(IMLConfigurationProducer.INSTANCE)
                    .start(result, p2RepositoryManager.getLookupCache(), dependencyGraph);
                dependencyGraph.setCurrentNode(root);
                var resultPath = params.resultFilesPath;
                resultPath = resultPath.resolve(productPath.getKey().getFileName());
                try {
                    Files.createDirectories(resultPath.getParent());
                } catch (Throwable throwable) {
                    log.debug("Error creating target parent directories for {}", resultPath);
                }
                try {
                    FileUtils.removeAllFromDir(resultPath);
                } catch (Throwable e) {
                    log.debug("Error deleting target folder for {}", resultPath, e);
                }
                log.info("Product generation for {} completed", result.getProductId());
                log.debug("Thread {} finished execution", Thread.currentThread().getName());
                dependencyGraph.printDependencyTree(root);
                return new ResultInfo(productPath, result, resultPath);
            } catch (XMLStreamException | IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList())).join();
        forkJoinPool.submit(() -> {
            executionResults.parallelStream().forEach((executionResult) -> {
                try {
                    Path resultPath = executionResult.resultPath();
                    Result result = executionResult.result;
                    Map.Entry<Path, String> productPath = executionResult.productPath();
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

                        log.info("Starting to load test bundles for {}...", result.getProductName());
                        PluginResolver.resolveTestBundlesAndLibraries(result, result.getProductGraph());
                    }
                    {
                        IMLConfigurationProducer.INSTANCE.generateIMLFiles(result, resultPath);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }).join();
        log.info("Product generation completed for all products!");
        forkJoinPool.shutdown();
        List<Path> additionalLibraries = PathsManager.INSTANCE.getAdditionalLibraries();
        log.info("Appending additional libraries...");
        if (additionalLibraries != null) {
            for (Path additionalLibrary : additionalLibraries) {
                FileUtils.copyFolder(additionalLibrary, PathsManager.INSTANCE.getEclipsePath(), false);
            }
        }
        log.info("Resolving additional repositories...");
        if (!CommonUtils.isEmpty(pathsManager.getAdditionalRepositoriesPaths())) {
            Result result = new Result();
            result.setProductPath(Path.of("/"));
            FeatureResolver.addNewFeatureProject(result.getProductPath());
            DependencyGraph dependencyGraph = getGraphImplementation(generateDependencyTree);
            dependencyGraph.traverseIntoNode("additional");
            DependencyGraph.DependencyNode root = dependencyGraph.getCurrentNode();
            for (Path additionalRepositoriesPath : pathsManager.getAdditionalRepositoriesPaths()) {
                try (Stream<Path> stream = Files.walk(additionalRepositoriesPath)) {
                    List<Path> categoryXMLS = stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().equals("category.xml"))
                        .toList();
                    for (Path categoryXML : categoryXMLS) {
                        log.debug("Generating config for " + categoryXML);
                        CategoryXMLFileParser.parseCategoryXML(result, categoryXML, dependencyGraph);
                    }
                } catch (IOException e) {
                    log.error("Error reading the repository " + e);
                }
            }
            dependencyGraph.printDependencyTree(root);
            log.debug(result.getBundlesByNames().size() + " additional bundles to resolve found");

            IMLConfigurationProducer.INSTANCE.generateIMLFiles(result, null);
        }
        log.info("Producing final IML configuration...");
        IMLConfigurationProducer.INSTANCE.generateImplConfiguration();
        if (params.updateWorkspace) {
            IMLConfigurationProducer.INSTANCE.patchWorkspaceParameters();
        }
        log.info("Execution completed!");
    }

    @NotNull
    private static DependencyGraph getGraphImplementation(boolean generateDependencyTree) {
        if (generateDependencyTree) {
            return new DependencyGraphImpl();
        } else {
            return new DependencyGraphStub();
        }
    }

    private record ResultInfo(Map.Entry<Path, String> productPath, Result result, Path resultPath) {

    }
}
