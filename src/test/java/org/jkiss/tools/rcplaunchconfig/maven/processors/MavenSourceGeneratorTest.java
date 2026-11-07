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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenSourceGeneratorTest {
    @TempDir
    Path module;

    @Test
    void generatesConfiguredSourcesOnlyWhenMissing() throws Exception {
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
}
