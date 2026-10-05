/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2026 DBeaver Corp and others
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
package org.jkiss.tools.rcplaunchconfig.producers.iml;

import com.dbeaver.osgi.dependency.processing.PathsManager;
import com.dbeaver.osgi.dependency.processing.Result;
import com.dbeaver.osgi.dependency.processing.resolvers.ManifestParser;
import org.jkiss.tools.rcplaunchconfig.maven.model.MavenDependency;
import org.jkiss.tools.rcplaunchconfig.maven.registry.MavenLocalArtifactRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.Manifest;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;

import static org.junit.jupiter.api.Assertions.*;

class IMLConfigurationProducerTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesMavenOnlyBundleDependenciesRecursively() throws Exception {
        Path api = createModule("bundles", "com.dbeaver.test.api", true, "com.dbeaver.test.model");
        Path model = createModule("bundles", "com.dbeaver.test.model", true, "com.dbeaver.test.api");
        createModule("bundles", "com.dbeaver.test.unused", true);
        Path service = createModule("services", "com.dbeaver.test.service", false, "com.dbeaver.test.api");
        initPaths(service);

        IMLConfigurationProducer producer = new IMLConfigurationProducer();
        Result result = new Result();
        producer.generateIMLFiles(result, null);
        assertEquals(api, MavenLocalArtifactRegistry.INSTANCE.getProvidedDependencyPath(
            new MavenDependency("com.dbeaver.test", "com.dbeaver.test.api", "1.0.0", List.of())
        ));
        producer.generateImplConfiguration();

        assertModuleDependency(service, api);
        assertModuleDependency(api, model);
        assertModuleDependency(model, api);
        for (Path module : List.of(service, api, model)) {
            assertModuleListedOnce(module);
            Document iml = readIml(module);
            assertEquals("1", evaluate(iml, "count(//sourceFolder[contains(@url, '/src/main/java')])"));
            assertEquals("1", evaluate(iml, "count(//output[contains(@url, '/target/classes')])"));
        }
        assertFalse(Files.exists(PathsManager.INSTANCE.getImlModulesPath().resolve("com.dbeaver.test.unused.iml")));
        assertTrue(result.getBundlesByNames().isEmpty());
    }

    @Test
    void preservesBundleModuleAlreadyGeneratedForAnotherProduct() throws Exception {
        Path api = createModule("bundles", "com.dbeaver.test.shared", true);
        Path service = createModule("services", "com.dbeaver.test.consumer", false, "com.dbeaver.test.shared");
        initPaths(service);

        IMLConfigurationProducer producer = new IMLConfigurationProducer();
        producer.generateIMLFiles(new Result(), null);
        Result otherProduct = new Result();
        try (var input = Files.newInputStream(api.resolve("META-INF/MANIFEST.MF"))) {
            otherProduct.addBundle(ManifestParser.parseManifest(api, null, new Manifest(input)));
        }
        producer.generateIMLFiles(otherProduct, null);
        String bundleConfig = Files.readString(imlPath(api));
        producer.generateImplConfiguration();

        assertModuleDependency(service, api);
        assertModuleListedOnce(api);
        assertEquals(bundleConfig, Files.readString(imlPath(api)));
        assertEquals("JDK_21", evaluate(readIml(api), "string(//component/@LANGUAGE_LEVEL)"));
    }

    private void initPaths(Path service) throws IOException {
        Properties settings = new Properties();
        settings.setProperty("workspaceName", "test-workspace");
        settings.setProperty("bundlesPaths", "repository/bundles");
        settings.setProperty("featuresPaths", "repository/features");
        settings.setProperty("productsPaths", "repository/test.product");
        settings.setProperty("testBundlePaths", "repository/tests");
        settings.setProperty("mavenModules", tempDir.relativize(service).toString());
        PathsManager.INSTANCE.init(settings, tempDir, tempDir.resolve("workspace/eclipse"));
    }

    private Path createModule(String folder, String name, boolean bundle, String... dependencies) throws IOException {
        Path module = tempDir.resolve("repository").resolve(folder).resolve(name);
        Files.createDirectories(module.resolve("src/main/java"));
        StringBuilder dependencyXml = new StringBuilder();
        for (String dependency : dependencies) {
            dependencyXml.append("""
                <dependency>
                    <groupId>com.dbeaver.test</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0.0</version>
                </dependency>
                """.formatted(dependency));
        }
        Files.writeString(module.resolve("pom.xml"), """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>com.dbeaver.test</groupId>
                    <artifactId>test-parent</artifactId>
                    <version>1.0.0</version>
                </parent>
                <artifactId>%s</artifactId>
                <packaging>%s</packaging>
                <dependencies>%s</dependencies>
            </project>
            """.formatted(name, bundle ? "eclipse-plugin" : "jar", dependencyXml));
        if (bundle) {
            Files.createDirectories(module.resolve("META-INF"));
            Files.writeString(module.resolve("META-INF/MANIFEST.MF"), """
                Manifest-Version: 1.0
                Bundle-ManifestVersion: 2
                Bundle-SymbolicName: %s
                Bundle-Version: 1.0.0
                Bundle-RequiredExecutionEnvironment: JavaSE-21

                """.formatted(name));
            Files.writeString(module.resolve("build.properties"), "source.. = src/main/java/\noutput.. = target/classes/\n");
        }
        return module;
    }

    private void assertModuleDependency(Path module, Path dependency) throws Exception {
        assertEquals("1", evaluate(readIml(module),
            "count(//orderEntry[@type='module' and @module-name='" + dependency.getFileName() + "'])"));
        assertEquals("0", evaluate(readIml(module), "count(//orderEntry[@type='library'])"));
    }

    private void assertModuleListedOnce(Path module) throws Exception {
        Document modules = readXml(PathsManager.INSTANCE.getImlModulesPath().resolve(".idea/modules.xml"));
        assertEquals("1", evaluate(modules,
            "count(//module[@filepath='$PROJECT_DIR$/" + module.getFileName() + ".iml'])"));
    }

    private Path imlPath(Path module) {
        return PathsManager.INSTANCE.getImlModulesPath().resolve(module.getFileName() + ".iml");
    }

    private Document readIml(Path module) throws Exception {
        return readXml(imlPath(module));
    }

    private Document readXml(Path path) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());
    }

    private String evaluate(Document document, String expression) throws Exception {
        return XPathFactory.newInstance().newXPath().evaluate(expression, document);
    }
}
