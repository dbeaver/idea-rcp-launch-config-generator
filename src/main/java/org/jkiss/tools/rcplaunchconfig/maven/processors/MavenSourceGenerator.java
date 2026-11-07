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

import com.sun.tools.xjc.Driver;
import org.antlr.v4.Tool;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;

/** Processes supported generate-sources executions directly, without running a Maven build. */
public class MavenSourceGenerator {
    private static final Logger log = LoggerFactory.getLogger(MavenSourceGenerator.class);
    private static final Map<String, SourceGoal> GOALS = Map.of(
        "org.antlr:antlr4-maven-plugin:antlr4", new SourceGoal("generate-sources", MavenSourceGenerator::generateAntlr),
        "org.codehaus.mojo:jaxb2-maven-plugin:xjc", new SourceGoal("generate-sources", MavenSourceGenerator::generateJaxb)
    );

    private interface SourceHandler {
        void generate(@NotNull Path module, @Nullable Element executionConfig, @Nullable Element pluginConfig) throws IOException;
    }

    private record SourceGoal(String defaultPhase, SourceHandler handler) {
    }

    public static void generate(@NotNull Path module) throws IOException {
        module = module.toAbsolutePath().normalize();
        Path pom = module.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return;
        }
        try (var input = Files.newInputStream(pom)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Element project = factory.newDocumentBuilder().parse(input).getDocumentElement();
            Element build = child(project, "build");
            Element plugins = build == null ? null : child(build, "plugins");
            if (plugins == null) {
                return;
            }
            for (Element plugin : children(plugins, "plugin")) {
                Element executions = child(plugin, "executions");
                if (executions == null) {
                    continue;
                }
                for (Element execution : children(executions, "execution")) {
                    Element goals = child(execution, "goals");
                    if (goals == null) {
                        continue;
                    }
                    for (Element goal : children(goals, "goal")) {
                        String key = text(plugin, "groupId") + ":" + text(plugin, "artifactId") + ":" + goal.getTextContent().trim();
                        SourceGoal sourceGoal = GOALS.get(key);
                        if (sourceGoal != null && "generate-sources".equals(value(execution, "phase", sourceGoal.defaultPhase()))) {
                            sourceGoal.handler().generate(module, child(execution, "configuration"), child(plugin, "configuration"));
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to read generate-sources configuration from " + pom, e);
        }
    }

    private static void generateAntlr(
        @NotNull Path module,
        @Nullable Element executionConfig,
        @Nullable Element pluginConfig
    ) throws IOException {
        Path source = module.resolve(value(executionConfig, pluginConfig, "sourceDirectory", "src/main/antlr4")).normalize();
        Path output = module.resolve(
            value(executionConfig, pluginConfig, "outputDirectory", "target/generated-sources/antlr4")
        ).normalize();
        if (!source.startsWith(module) || !output.startsWith(module) || !Files.isDirectory(source)) {
            return;
        }
        List<Path> grammars;
        try (Stream<Path> files = Files.walk(source)) {
            grammars = files.filter(path -> path.toString().endsWith(".g4"))
                .sorted(Comparator.comparing(Path::toString)).toList();
        }
        if (grammars.isEmpty()) {
            return;
        }
        boolean outdated = false;
        for (Path grammar : grammars) {
            String name = grammar.getFileName().toString().replaceFirst("\\.g4$", "");
            String generatedName = name.endsWith("Lexer") || name.endsWith("Parser")
                ? name + ".java" : name + "Parser.java";
            Path generated = output.resolve(generatedName);
            outdated |= !Files.isRegularFile(generated)
                || Files.getLastModifiedTime(generated).compareTo(Files.getLastModifiedTime(grammar)) < 0;
        }
        if (!outdated) {
            return;
        }
        Files.createDirectories(output);
        List<String> args = new ArrayList<>(List.of("-o", output.toString(), "-lib", source.toString()));
        for (Path grammar : grammars) {
            args.add(grammar.toString());
        }
        log.info("Generating ANTLR sources for {}", module);
        Tool tool = new Tool(args.toArray(String[]::new));
        tool.processGrammarsOnCommandLine();
        if (tool.getNumErrors() != 0) {
            throw new IOException("ANTLR generation failed for " + module);
        }
    }

    private static void generateJaxb(
        @NotNull Path module,
        @Nullable Element executionConfig,
        @Nullable Element pluginConfig
    ) throws IOException {
        Element sources = child(executionConfig, "sources");
        if (sources == null) {
            sources = child(pluginConfig, "sources");
        }
        if (sources == null) {
            return;
        }
        Path output = module.resolve(value(executionConfig, pluginConfig, "outputDirectory", "target/generated-sources/jaxb")).normalize();
        if (!output.startsWith(module)) {
            throw new IOException("JAXB output is outside module " + module);
        }
        String packageName = value(executionConfig, pluginConfig, "packageName", "");
        List<Path> schemas = new ArrayList<>();
        for (Element source : children(sources, "source")) {
            Path schema = module.resolve(source.getTextContent().trim()).normalize();
            if (!schema.startsWith(module)) {
                throw new IOException("JAXB schema is outside module " + module);
            }
            if (Files.isRegularFile(schema)) {
                schemas.add(schema);
            }
        }
        if (schemas.isEmpty()) {
            return;
        }
        Path generated = output.resolve(packageName.replace('.', '/')).resolve("ObjectFactory.java");
        if (Files.isRegularFile(generated) && schemas.stream().allMatch(schema -> {
            try {
                return Files.getLastModifiedTime(schema).compareTo(Files.getLastModifiedTime(generated)) <= 0;
            } catch (IOException e) {
                return false;
            }
        })) {
            return;
        }
        Files.createDirectories(output);
        List<String> args = new ArrayList<>(List.of("-d", output.toString()));
        if (!packageName.isBlank()) {
            args.add("-p");
            args.add(packageName);
        }
        Element bindings = child(executionConfig, "xjbSources");
        if (bindings == null) {
            bindings = child(pluginConfig, "xjbSources");
        }
        if (bindings != null) {
            for (Element binding : children(bindings, "xjbSource")) {
                args.add("-b");
                args.add(module.resolve(binding.getTextContent().trim()).toString());
            }
        }
        schemas.stream().map(Path::toString).forEach(args::add);
        log.info("Generating JAXB sources for {}", module);
        try {
            if (Driver.run(args.toArray(String[]::new), System.out, System.err) != 0) {
                throw new IOException("JAXB generation failed for " + module);
            }
        } catch (Exception e) {
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("JAXB generation failed for " + module, e);
        }
    }

    private static @NotNull String value(@NotNull Element parent, @NotNull String name, @NotNull String fallback) {
        String value = text(parent, name);
        return value == null ? fallback : value;
    }

    private static @NotNull String value(
        @Nullable Element executionConfig,
        @Nullable Element pluginConfig,
        @NotNull String name,
        @NotNull String fallback
    ) {
        String value = text(executionConfig, name);
        if (value == null) {
            value = text(pluginConfig, name);
        }
        return value == null ? fallback : value;
    }

    private static @Nullable String text(@Nullable Element parent, @NotNull String name) {
        Element child = child(parent, name);
        return child == null ? null : child.getTextContent().trim();
    }

    private static @Nullable Element child(@Nullable Element parent, @NotNull String name) {
        if (parent == null) {
            return null;
        }
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element
                && name.equals(element.getLocalName() == null ? element.getNodeName() : element.getLocalName())) {
                return element;
            }
        }
        return null;
    }

    private static @NotNull List<Element> children(@NotNull Element parent, @NotNull String name) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element
                && name.equals(element.getLocalName() == null ? element.getNodeName() : element.getLocalName())) {
                elements.add(element);
            }
        }
        return elements;
    }
}
