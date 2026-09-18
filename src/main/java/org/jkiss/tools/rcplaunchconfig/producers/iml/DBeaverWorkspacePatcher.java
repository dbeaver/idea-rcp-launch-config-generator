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

import org.jkiss.code.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class DBeaverWorkspacePatcher {
    private static final Logger log = LoggerFactory.getLogger(DBeaverWorkspacePatcher.class);

    /**
     * Adds additional parameters to workspace
     *
     * @param path path to workspace.xml file
     */
    public static void patchWorkspace(@NotNull Path path) {
        try {
            // Load the XML file
            File xmlFile = path.toFile();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);

            // Normalize the document
            doc.getDocumentElement().normalize();


            addOrUpdateComponent(doc, "VcsManagerConfiguration", new String[][]{
                {"OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT", "true"}
            });

            addOrUpdateComponent(doc, "UpdateCopyrightCheckinHandler", new String[][]{
                {"UPDATE_COPYRIGHT", "true"}
            });


            // Save the updated XML back to the file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(xmlFile);
            transformer.transform(source, result);

            System.out.println("XML file updated successfully.");
        } catch (Exception e) {
            log.error("Error updating workspace file", e);
        }
    }

    public static void patchVcsMappings(
        @NotNull Path path,
        @NotNull Path projectPath,
        @NotNull Collection<Path> repositoryPaths
    ) throws IOException {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(path.toFile());
            doc.getDocumentElement().normalize();

            Element mappingsComponent = findComponent(doc, "VcsDirectoryMappings");
            if (mappingsComponent == null) {
                mappingsComponent = doc.createElement("component");
                mappingsComponent.setAttribute("name", "VcsDirectoryMappings");
                doc.getDocumentElement().appendChild(mappingsComponent);
            }
            Element targetComponent = mappingsComponent;

            Set<String> existingMappings = new HashSet<>();
            NodeList mappings = mappingsComponent.getElementsByTagName("mapping");
            for (int i = 0; i < mappings.getLength(); i++) {
                existingMappings.add(((Element) mappings.item(i)).getAttribute("directory"));
            }

            Path absoluteProjectPath = projectPath.toAbsolutePath().normalize();
            repositoryPaths.stream()
                .map(repositoryPath -> repositoryPath.toAbsolutePath().normalize())
                .filter(repositoryPath -> Files.exists(repositoryPath.resolve(".git")))
                .sorted()
                .map(repositoryPath -> toIdeaPath(absoluteProjectPath, repositoryPath))
                .filter(existingMappings::add)
                .forEach(directory -> {
                    Element mapping = doc.createElement("mapping");
                    mapping.setAttribute("directory", directory);
                    mapping.setAttribute("vcs", "Git");
                    targetComponent.appendChild(mapping);
                });

            saveDocument(doc, path.toFile());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Error updating VCS mappings", e);
        }
    }

    @NotNull
    private static String toIdeaPath(@NotNull Path projectPath, @NotNull Path repositoryPath) {
        Path relativePath = projectPath.relativize(repositoryPath);
        if (relativePath.getNameCount() == 0) {
            return "$PROJECT_DIR$";
        }
        return "$PROJECT_DIR$/" + relativePath.toString().replace('\\', '/');
    }

    private static void saveDocument(@NotNull Document doc, @NotNull File xmlFile) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(doc), new StreamResult(xmlFile));
    }

    private static Element findComponent(@NotNull Document doc, @NotNull String componentName) {
        NodeList components = doc.getElementsByTagName("component");
        for (int i = 0; i < components.getLength(); i++) {
            Element component = (Element) components.item(i);
            if (component.getAttribute("name").equals(componentName)) {
                return component;
            }
        }
        return null;
    }

    private static void addOrUpdateComponent(Document doc, String componentName, String[][] options) {
        Element targetComponent = findComponent(doc, componentName);

        // If the component does not exist, create it
        if (targetComponent == null) {
            targetComponent = doc.createElement("component");
            targetComponent.setAttribute("name", componentName);
            doc.getDocumentElement().appendChild(targetComponent);
        }

        // Add or update options
        for (String[] option : options) {
            String optionName = option[0];
            String optionValue = option[1];

            boolean optionExists = false;
            NodeList optionNodes = targetComponent.getElementsByTagName("option");
            for (int i = 0; i < optionNodes.getLength(); i++) {
                Element optionElement = (Element) optionNodes.item(i);
                if (optionElement.getAttribute("name").equals(optionName)) {
                    optionElement.setAttribute("value", optionValue);
                    optionExists = true;
                    break;
                }
            }

            if (!optionExists) {
                Element newOption = doc.createElement("option");
                newOption.setAttribute("name", optionName);
                newOption.setAttribute("value", optionValue);
                targetComponent.appendChild(newOption);
            }
        }
    }

}
