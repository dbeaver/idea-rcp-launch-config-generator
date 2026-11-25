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

import org.jkiss.code.NotNull;
import org.jkiss.tools.rcplaunchconfig.maven.model.MavenDependency;
import org.jkiss.tools.rcplaunchconfig.maven.registry.MavenLocalArtifactRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MavenPomProcessorInheritanceTest {
    @TempDir
    Path tempDir;

    @Test
    void inheritsCompilerPropertiesAndResolvesChildOverrides() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), """
            <project><groupId>test.inheritance</groupId><artifactId>parent</artifactId><version>1</version><properties>
                <java.version>25</java.version>
                <maven.compiler.source>${java.version}</maven.compiler.source>
                <maven.compiler.target>${java.version}</maven.compiler.target>
            </properties></project>
            """);
        Path parent = childPom("parent", "");
        Path child = childPom("parent/child", "");
        assertEquals("JDK_25", MavenPomProcessor.getJavaLanguageLevel(child));

        childPom("parent/child", "<properties><java.version>17</java.version></properties>");
        assertEquals("JDK_17", MavenPomProcessor.getJavaLanguageLevel(child));
        assertEquals("JDK_25", MavenPomProcessor.getJavaLanguageLevel(parent));

        childPom("parent/child", "<properties><maven.compiler.release>21</maven.compiler.release></properties>");
        assertEquals("JDK_21", MavenPomProcessor.getJavaLanguageLevel(child));
    }

    @Test
    void resolvesInheritedCompilerPluginConfiguration() throws Exception {
        for (String container : List.of("%s", "<pluginManagement>%s</pluginManagement>")) {
            Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                    <groupId>test.inheritance</groupId><artifactId>parent</artifactId><version>1</version>
                    <properties><java.version>25</java.version><maven.compiler.source>17</maven.compiler.source></properties>
                    <build>%s</build>
                </project>
                """.formatted(container.formatted("""
                <plugins><plugin>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration><release>${java.version}</release></configuration>
                </plugin></plugins>
                """)));
            Path child = childPom("child", "<properties><java.version>21</java.version></properties>");
            assertEquals("JDK_21", MavenPomProcessor.getJavaLanguageLevel(child));

            childPom("child", """
                <build><plugins><plugin>
                    <groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId>
                    <configuration><release>17</release></configuration>
                </plugin></plugins></build>
                """);
            assertEquals("JDK_17", MavenPomProcessor.getJavaLanguageLevel(child));
        }
    }

    @Test
    void skipsNonInheritedPluginAndExecutionConfiguration() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), """
            <project><groupId>test.inheritance</groupId><artifactId>parent</artifactId><version>1</version>
                <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId>
                    <inherited>FALSE</inherited><configuration><release>17</release></configuration>
                </plugin></plugins></build>
            </project>
            """);
        Path child = childPom("child", "");
        assertEquals("JDK_17", MavenPomProcessor.getJavaLanguageLevel(tempDir));
        assertNull(MavenPomProcessor.getJavaLanguageLevel(child));

        childPom("child", """
            <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><executions><execution>
                <id>default-compile</id><inherited>FALSE</inherited><configuration><source>21</source></configuration>
            </execution></executions></plugin></plugins></build>
            """);
        assertEquals("JDK_21", MavenPomProcessor.getJavaLanguageLevel(child));
        assertNull(MavenPomProcessor.getJavaLanguageLevel(childPom("child/grandchild", "")));
    }

    @Test
    void nonInheritanceBlocksOlderAncestorConfiguration() throws Exception {
        for (String configuration : List.of(
            "<configuration><release>17</release></configuration>",
            "<executions><execution><id>default-compile</id><configuration><release>17</release></configuration></execution></executions>"
        )) {
            Files.writeString(tempDir.resolve("pom.xml"), """
                <project><groupId>test.inheritance</groupId><artifactId>parent</artifactId><version>1</version>
                    <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId>%s</plugin></plugins></build>
                </project>
                """.formatted(configuration));
            String barrier = configuration.startsWith("<executions>")
                ? "<executions><execution><id>default-compile</id><inherited>false</inherited></execution></executions>"
                : "<inherited>false</inherited>";
            Path parent = childPom("parent", """
                <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId>%s</plugin></plugins></build>
                """.formatted(barrier));
            assertEquals("JDK_17", MavenPomProcessor.getJavaLanguageLevel(parent));
            assertNull(MavenPomProcessor.getJavaLanguageLevel(childPom("parent/child", "")));
        }
    }

    @Test
    void defaultCompileExecutionOverridesTopLevelConfiguration() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), """
            <project><groupId>test.inheritance</groupId><artifactId>parent</artifactId><version>1</version>
                <build><pluginManagement><plugins><plugin><artifactId>maven-compiler-plugin</artifactId>
                    <configuration><release>17</release></configuration>
                    <executions><execution><id>default-compile</id>
                        <configuration><release>${java.version}</release></configuration>
                    </execution><execution><id>default-testCompile</id>
                        <configuration><release>8</release></configuration>
                    </execution></executions>
                </plugin></plugins></pluginManagement></build>
            </project>
            """);
        Path child = childPom("child", "<properties><java.version>25</java.version></properties>");
        assertEquals("JDK_25", MavenPomProcessor.getJavaLanguageLevel(child));
    }

    @Test
    void ignoresLocalPomWithDifferentParentCoordinates() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), """
            <project><groupId>test.inheritance</groupId><artifactId>aggregator</artifactId><version>1</version>
                <properties><maven.compiler.source>17</maven.compiler.source></properties>
            </project>
            """);
        Path child = childPom("child", "");
        assertNull(MavenPomProcessor.getJavaLanguageLevel(child));
    }

    @Test
    void appliesDefaultAndFileActivatedCompilerProfiles() throws Exception {
        Path child = childPom("child", """
            <properties><maven.compiler.source>17</maven.compiler.source></properties>
            <profiles>
                <profile><id>default</id><activation><activeByDefault>true</activeByDefault></activation>
                    <properties><maven.compiler.source>25</maven.compiler.source></properties>
                </profile>
                <profile><id>file</id><activation><file><exists>compiler-profile</exists></file></activation>
                    <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId>
                        <configuration><release>21</release></configuration>
                    </plugin></plugins></build>
                </profile>
            </profiles>
            """);
        assertEquals("JDK_25", MavenPomProcessor.getJavaLanguageLevel(child));
        Files.createFile(child.resolve("compiler-profile"));
        assertEquals("JDK_21", MavenPomProcessor.getJavaLanguageLevel(child));
    }

    @Test
    void activatesCompilerProfileByPackaging() throws Exception {
        Path child = childPom("child", """
            <profiles><profile><id>jar</id>
                <activation><property><name>packaging</name><value>jar</value></property></activation>
                <properties><maven.compiler.release>21</maven.compiler.release></properties>
            </profile></profiles>
            """);
        assertEquals("JDK_21", MavenPomProcessor.getJavaLanguageLevel(child));
    }

    @Test
    void normalizesLegacySourceAndIgnoresTargetAndInactiveProfiles() throws Exception {
        Path child = childPom("child", """
            <properties><maven.compiler.target>17</maven.compiler.target></properties>
            <profiles><profile><properties><maven.compiler.source>21</maven.compiler.source></properties></profile></profiles>
            """);
        assertNull(MavenPomProcessor.getJavaLanguageLevel(child));
        for (String source : List.of("8", "1.8", "9", "1.9")) {
            childPom("child", """
                <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId>
                    <configuration><source>%s</source><target>17</target></configuration>
                </plugin></plugins></build>
                """.formatted(source));
            assertEquals(source.endsWith("8") ? "JDK_1_8" : "JDK_1_9", MavenPomProcessor.getJavaLanguageLevel(child));
        }
    }

    @Test
    void unresolvedAndCyclicCompilerPropertiesInheritProjectDefault() throws Exception {
        Path child = childPom("child", """
            <properties>
                <maven.compiler.release>${missing}</maven.compiler.release>
                <maven.compiler.source>${java.version}</maven.compiler.source>
                <java.version>${maven.compiler.source}</java.version>
            </properties>
            """);
        assertNull(MavenPomProcessor.getJavaLanguageLevel(child));
    }

    @Test
    void opensEachPomOnlyOnceDuringGeneration() throws Exception {
        Path parentPom = tempDir.resolve("pom.xml");
        Files.writeString(parentPom, "<project><groupId>test</groupId><artifactId>parent</artifactId><version>1</version></project>");
        Path child = childPom("child", "");
        MavenPomProcessor.startGeneration();
        try {
            MavenPomProcessor.collectArtifacts(child);
            MavenPomProcessor.collectArtifacts(child);
            MavenPomProcessor.processDependencies(child);
            MavenPomProcessor.processDependencies(child);
            assertEquals(1, MavenPomProcessor.getPomOpenCount(child.resolve("pom.xml")));
            assertEquals(1, MavenPomProcessor.getPomOpenCount(parentPom));
        } finally {
            MavenPomProcessor.finishGeneration();
        }
    }

    @Test
    void inheritsSameTestFrameworkWithAndWithoutDirectTestDependencies() throws Exception {
        MavenDependency junit = providedDependency("junit-jupiter-engine", "1", "test");
        MavenDependency sqlite = providedDependency("sqlite-jdbc", "1", "test");
        Files.writeString(tempDir.resolve("pom.xml"), """
            <project>
                <properties><test.version>1</test.version></properties>
                <dependencyManagement><dependencies>
                    <dependency><groupId>test.inheritance</groupId><artifactId>junit-jupiter-engine</artifactId>
                        <version>${test.version}</version></dependency>
                </dependencies></dependencyManagement>
                <dependencies>
                    <dependency><groupId>test.inheritance</groupId><artifactId>junit-jupiter-engine</artifactId>
                        <scope>test</scope></dependency>
                </dependencies>
            </project>
            """);
        Path libsql = childPom("libsql", "");
        Path d1 = childPom("d1", """
            <dependencies>
                <dependency><groupId>test.inheritance</groupId><artifactId>sqlite-jdbc</artifactId>
                    <version>1</version><scope>test</scope></dependency>
            </dependencies>
            """);

        assertEquals(List.of(junit), MavenPomProcessor.processDependencies(libsql));
        assertEquals(List.of(sqlite, junit), MavenPomProcessor.processDependencies(d1));
    }

    @Test
    void childDependencyOverridesInheritedDependency() throws Exception {
        providedDependency("override", "1", "test");
        MavenDependency override = providedDependency("override", "2", "compile");
        Files.writeString(tempDir.resolve("pom.xml"), """
            <project><dependencies>
                <dependency><groupId>test.inheritance</groupId><artifactId>override</artifactId>
                    <version>1</version><scope>test</scope></dependency>
            </dependencies></project>
            """);
        Path child = childPom("child", """
            <dependencies>
                <dependency><groupId>test.inheritance</groupId><artifactId>override</artifactId>
                    <version>2</version><scope>compile</scope></dependency>
            </dependencies>
            """);
        assertEquals(List.of(override), MavenPomProcessor.processDependencies(child));
    }

    @NotNull
    private Path childPom(@NotNull String name, @NotNull String content) throws Exception {
        Path child = Files.createDirectories(tempDir.resolve(name));
        Files.writeString(child.resolve("pom.xml"), """
            <project>
                <parent><groupId>test.inheritance</groupId><artifactId>parent</artifactId>
                    <version>1</version><relativePath>../pom.xml</relativePath></parent>
                <artifactId>parent</artifactId>
                %s
            </project>
            """.formatted(content));
        return child;
    }

    @NotNull
    private MavenDependency providedDependency(@NotNull String name, @NotNull String version, @NotNull String scope) {
        MavenDependency dependency = new MavenDependency("test.inheritance", name, version, List.of(), scope);
        MavenLocalArtifactRegistry.INSTANCE.addProvidedDependency(dependency, tempDir.resolve(name));
        return dependency;
    }
}
