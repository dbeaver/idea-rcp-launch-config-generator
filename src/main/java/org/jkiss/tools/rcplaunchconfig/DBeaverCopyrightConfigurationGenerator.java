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
package org.jkiss.tools.rcplaunchconfig;

import com.dbeaver.osgi.dependency.processing.PathsManager;
import org.jkiss.code.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class DBeaverCopyrightConfigurationGenerator {
    private static final Logger log = LoggerFactory.getLogger(DBeaverCopyrightConfigurationGenerator.class);

    private static final String CE_COPYRIGHT_PROFILE = "DBeaver CE";
    private static final String EE_COPYRIGHT_PROFILE = "DBeaver EE";
    private static final String FILE_NAME = "dbeaver-copyright-settings.xml";
    private static final String IDEA_FOLDER = ".idea";


    private record PathCopyright(@NotNull String folderPath, @NotNull String copyrightProfileName) {
    }

    public static void generateXml() {

        List<PathCopyright> allCopyrights = new ArrayList<>();
        Path projectPath = PathsManager.INSTANCE.getImlModulesPath();

        for (Path repo : PathsManager.INSTANCE.getOpenSourceReposPaths()) {
            String relativePath = transformPath(projectPath, repo);
            allCopyrights.add(new PathCopyright(relativePath, CE_COPYRIGHT_PROFILE));
        }

        List<Path> allBundlesPath = new ArrayList<>(PathsManager.INSTANCE.getBundlesLocations());
        allBundlesPath.addAll(PathsManager.INSTANCE.getTestBundlesPaths());

        for (Path path : allBundlesPath) {
            boolean useCE = PathsManager.INSTANCE.getOpenSourceReposPaths()
                .stream()
                .anyMatch(path::startsWith);
            String projectRelativePath = transformPath(projectPath, path);
            String profile = useCE ? CE_COPYRIGHT_PROFILE : EE_COPYRIGHT_PROFILE;
            log.info("Adding copyright for {}, : profile {}", projectRelativePath, profile);
            allCopyrights.add(
                new PathCopyright(projectRelativePath, profile)
            );
        }
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .newDocument();

            Element project = doc.createElement("project");
            project.setAttribute("version", "4");
            doc.appendChild(project);

            Element component = doc.createElement("component");
            component.setAttribute("name", "DBeaverCopyrightSettings");
            project.appendChild(component);

            Element option = doc.createElement("option");
            option.setAttribute("name", "pathCopyrights");
            component.appendChild(option);

            Element list = doc.createElement("list");
            option.appendChild(list);

            for (PathCopyright pc : allCopyrights) {
                Element pathCopyright = doc.createElement("PathCopyright");
                list.appendChild(pathCopyright);

                Element folderOpt = doc.createElement("option");
                folderOpt.setAttribute("name", "folderPath");
                folderOpt.setAttribute("value", pc.folderPath());
                pathCopyright.appendChild(folderOpt);

                Element profileOpt = doc.createElement("option");
                profileOpt.setAttribute("name", "copyrightProfileName");
                profileOpt.setAttribute("value", pc.copyrightProfileName());
                pathCopyright.appendChild(profileOpt);
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            Path ideaConfFolder = projectPath.resolve(IDEA_FOLDER);
            if (!Files.exists(ideaConfFolder)) {
                Files.createDirectories(ideaConfFolder);
            }
            Files.writeString(projectPath.resolve(FILE_NAME), writer.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate copyright settings", e);
        }
    }

    @NotNull
    private static String transformPath(@NotNull Path projectPath, @NotNull Path repo) {
        String relativePath = projectPath.relativize(repo).toString();
        return "$PROJECT_DIR$/" + relativePath;
    }

}
