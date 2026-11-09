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
package org.jkiss.tools.rcplaunchconfig.maven.processors;

import jakarta.xml.bind.JAXBContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenSourceGeneratorTest {
    @TempDir
    Path module;

    @Test
    void generatesConfiguredSourcesOnlyWhenMissing() throws Exception {
        // On Windows this directory name produces a Unicode escape prefix in native paths.
        module = Files.createDirectory(module.resolve("user"));
        Files.writeString(module.resolve("pom.xml"), """
            <project><build><plugins><plugin>
              <groupId>org.antlr</groupId><artifactId>antlr4-maven-plugin</artifactId>
              <executions><execution><phase>generate-sources</phase><goals><goal>antlr4</goal></goals>
                <configuration><sourceDirectory>grammar</sourceDirectory>
                  <outputDirectory>target/generated-sources/antlr4</outputDirectory>
                </configuration>
              </execution></executions>
            </plugin></plugins></build></project>
            """);
        Files.createDirectory(module.resolve("grammar"));
        Files.writeString(module.resolve("grammar/Example.g4"), "grammar Example; start: 'hello' EOF;");

        Files.createDirectory(module.resolve("nested"));
        MavenSourceGenerator.generate(module.resolve("nested/.."));
        Path parser = module.resolve("target/generated-sources/antlr4/ExampleParser.java");
        assertTrue(Files.isRegularFile(parser));
        for (String name : new String[]{"ExampleLexer", "ExampleParser", "ExampleListener", "ExampleBaseListener"}) {
            String header = Files.readString(parser.resolveSibling(name + ".java")).lines().findFirst().orElseThrow();
            assertTrue(header.startsWith("// Generated from "));
            assertFalse(header.contains("\\"), header);
            assertTrue(header.contains("/user/grammar/Example.g4"), header);
        }
        Files.writeString(parser, "unchanged");
        MavenSourceGenerator.generate(module.resolve("nested/.."));
        assertEquals("unchanged", Files.readString(parser));
    }

    @Test
    void generatesJaxbSourcesFromImplicitGenerateSourcesPhase() throws Exception {
        // XJC needs the JAXB implementation to process schemas with bindings, not just the API.
        assertTrue(JAXBContext.newInstance(Object.class).getClass().getName().contains("jaxb"));
        Files.writeString(module.resolve("pom.xml"), """
            <project><build><plugins><plugin>
              <groupId>org.codehaus.mojo</groupId><artifactId>jaxb2-maven-plugin</artifactId>
              <executions><execution><goals><goal>xjc</goal></goals>
                <configuration><sources><source>schema/api.xsd</source></sources>
                  <packageName>test.generated</packageName>
                  <outputDirectory>target/generated-sources/jaxb</outputDirectory>
                </configuration>
              </execution></executions>
            </plugin></plugins></build></project>
            """);
        Files.createDirectory(module.resolve("schema"));
        Files.writeString(module.resolve("schema/api.xsd"), """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="message" type="xs:string"/>
            </xs:schema>
            """);

        Files.createDirectory(module.resolve("nested"));
        MavenSourceGenerator.generate(module.resolve("nested/.."));

        Path factory = module.resolve("target/generated-sources/jaxb/test/generated/ObjectFactory.java");
        assertTrue(Files.isRegularFile(factory));
        Files.writeString(factory, "unchanged");
        MavenSourceGenerator.generate(module.resolve("nested/.."));
        assertEquals("unchanged", Files.readString(factory));
    }

    @Test
    void copiesPrebuiltWidgetResourcesAndRefreshesExistingAssets() throws Exception {
        Path bundle = Files.createDirectories(module.resolve("ee/bundles/chart"));
        Path dist = Files.createDirectories(module.resolve("widgets/dist"));
        Files.writeString(dist.resolve("data-chart.js"), "new widget");
        Files.writeString(dist.resolve("data-chart.css"), "chart styles");
        Files.writeString(dist.resolve("data-chart.d.ts"), "types");
        Files.writeString(bundle.resolve("pom.xml"), """
            <project xmlns="http://maven.apache.org/POM/4.0.0"><build><plugins><plugin>
              <groupId>org.apache.maven.plugins</groupId><artifactId>maven-resources-plugin</artifactId>
              <executions><execution><phase>generate-resources</phase><goals><goal>copy-resources</goal></goals>
                <configuration><outputDirectory>${project.basedir}/web</outputDirectory><overwrite>true</overwrite>
                  <resources><resource><directory>${project.basedir}/../../../widgets/dist</directory>
                    <includes><include>data-chart.js</include><include>data-chart.css</include></includes>
                  </resource></resources>
                </configuration>
              </execution></executions>
            </plugin></plugins></build></project>
            """);
        Path web = Files.createDirectory(bundle.resolve("web"));
        Files.writeString(web.resolve("index.html"), "host page");
        Files.writeString(web.resolve("data-chart.js"), "stale widget");

        MavenSourceGenerator.generate(bundle);

        assertEquals("new widget", Files.readString(web.resolve("data-chart.js")));
        assertEquals("chart styles", Files.readString(web.resolve("data-chart.css")));
        assertEquals("host page", Files.readString(web.resolve("index.html")));
        assertFalse(Files.exists(web.resolve("data-chart.d.ts")));
        var modified = Files.getLastModifiedTime(web.resolve("data-chart.js"));
        MavenSourceGenerator.generate(bundle);
        assertEquals(modified, Files.getLastModifiedTime(web.resolve("data-chart.js")));
        Files.writeString(dist.resolve("data-chart.js"), "updated widget");
        MavenSourceGenerator.generate(bundle);
        assertEquals("updated widget", Files.readString(web.resolve("data-chart.js")));
    }

    @Test
    void copiesPluginConfiguredResourcesWithPatternsAndTargetPathsOnlyInPreparationPhase() throws Exception {
        Files.createDirectories(module.resolve("assets/nested"));
        Files.writeString(module.resolve("assets/main.js"), "main");
        Files.writeString(module.resolve("assets/nested/chart.js"), "chart");
        Files.writeString(module.resolve("assets/nested/chart.test.js"), "test");
        Files.writeString(module.resolve("assets/chart.css"), "css");
        Files.writeString(module.resolve("pom.xml"), """
            <project><build><plugins><plugin>
              <artifactId>maven-resources-plugin</artifactId>
              <configuration><outputDirectory>${project.build.directory}/web</outputDirectory>
                <resources><resource><directory>${basedir}/assets</directory><targetPath>scripts</targetPath>
                  <includes><include>**/*.js</include></includes>
                  <excludes><exclude>**/*.test.js</exclude></excludes>
                </resource></resources>
              </configuration>
              <executions>
                <execution><phase>generate-resources</phase><goals><goal>copy-resources</goal></goals></execution>
                <execution><phase>package</phase><goals><goal>copy-resources</goal></goals>
                  <configuration><outputDirectory>${project.basedir}/packaged</outputDirectory></configuration>
                </execution>
              </executions>
            </plugin></plugins></build></project>
            """);

        MavenSourceGenerator.generate(module);

        assertEquals("main", Files.readString(module.resolve("target/web/scripts/main.js")));
        assertEquals("chart", Files.readString(module.resolve("target/web/scripts/nested/chart.js")));
        assertFalse(Files.exists(module.resolve("target/web/scripts/nested/chart.test.js")));
        assertFalse(Files.exists(module.resolve("target/web/scripts/chart.css")));
        assertFalse(Files.exists(module.resolve("packaged")));
        Files.writeString(module.resolve("target/web/scripts/main.js"), "newer target");
        MavenSourceGenerator.generate(module);
        assertEquals("newer target", Files.readString(module.resolve("target/web/scripts/main.js")));
    }

    @Test
    void rejectsResourceOutputOutsideModule() throws Exception {
        Files.writeString(module.resolve("pom.xml"), """
            <project><build><plugins><plugin>
              <artifactId>maven-resources-plugin</artifactId>
              <executions><execution><phase>generate-resources</phase><goals><goal>copy-resources</goal></goals>
                <configuration><outputDirectory>${project.basedir}/../outside</outputDirectory></configuration>
              </execution></executions>
            </plugin></plugins></build></project>
            """);

        assertThrows(java.io.IOException.class, () -> MavenSourceGenerator.generate(module));
    }
}
