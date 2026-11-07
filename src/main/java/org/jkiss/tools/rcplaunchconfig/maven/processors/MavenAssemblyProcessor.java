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
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Manifest;
import javax.xml.parsers.DocumentBuilderFactory;

/** Restores missing bundle libraries declared by an assembly descriptor from resolved Maven artifacts. */
public class MavenAssemblyProcessor {
    public static synchronized void prepare(@NotNull Path module, @NotNull List<MavenDependency> dependencies) throws IOException {
        Path descriptor = module.resolve("descriptor.xml");
        Path manifestPath = module.resolve("META-INF/MANIFEST.MF");
        if (!Files.isRegularFile(descriptor) || !Files.isRegularFile(manifestPath)) {
            return;
        }
        try (var input = Files.newInputStream(descriptor); var manifestInput = Files.newInputStream(manifestPath)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Element assembly = factory.newDocumentBuilder().parse(input).getDocumentElement();
            Set<String> includes = coordinates(assembly, "include");
            Set<String> excludes = coordinates(assembly, "exclude");
            String classpath = new Manifest(manifestInput).getMainAttributes().getValue("Bundle-ClassPath");
            Set<String> required = new HashSet<>();
            if (classpath != null) {
                for (String entry : classpath.split(",")) {
                    String name = entry.trim();
                    if (name.startsWith("lib/") && name.endsWith(".jar")) {
                        required.add(name.substring(4, name.length() - 4));
                    }
                }
            }
            for (MavenDependency dependency : dependencies) {
                String coordinates = dependency.group() + ":" + dependency.name();
                if (dependency.isTestScope() || excludes.contains(coordinates)
                    || (!includes.contains(coordinates) && !required.contains(dependency.name()))) {
                    continue;
                }
                Path jar = MavenLocalArtifactRegistry.INSTANCE.getDowloadedDependencyPath(dependency);
                if (jar == null || !Files.isRegularFile(jar)) {
                    continue;
                }
                Path target = module.resolve("lib").resolve(dependency.name() + ".jar");
                if (!Files.isRegularFile(target)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(jar, target);
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to process assembly descriptor " + descriptor, e);
        }
    }

    private static Set<String> coordinates(Element assembly, String tag) {
        Set<String> result = new HashSet<>();
        NodeList entries = assembly.getElementsByTagName(tag);
        for (int i = 0; i < entries.getLength(); i++) {
            String value = entries.item(i).getTextContent().trim();
            if (value.split(":").length >= 2) {
                result.add(value.split(":")[0] + ":" + value.split(":")[1]);
            }
        }
        return result;
    }
}
