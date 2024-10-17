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
package org.jkiss.tools.rcplaunchconfig.xml;

import org.jkiss.tools.rcplaunchconfig.Result;
import org.jkiss.tools.rcplaunchconfig.resolvers.FeatureResolver;
import org.jkiss.tools.rcplaunchconfig.util.DependencyGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CategoryXMLFileParser {
    private static final Logger log = LoggerFactory.getLogger(CategoryXMLFileParser.class);

    public static void parseCategoryXML(Result result, Path path, DependencyGraph graph) {
        try {
            // Initialize the XML document
            var factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(path.toFile());

            // Normalize the XML structure
            doc.getDocumentElement().normalize();
            Set<String> features = new HashSet<>();
            // Get all the 'feature' elements
            NodeList featureList = doc.getElementsByTagName("feature");

            // Iterate through the features and print their ids
            for (int i = 0; i < featureList.getLength(); i++) {
                Node featureNode = featureList.item(i);

                if (featureNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element featureElement = (Element) featureNode;
                    String id = featureElement.getAttribute("id");
                    FeatureResolver.resolveFeatureDependencies(result, id, graph);
                }
            }

        } catch (Exception e) {
            log.error("Error during parsing category xml file" + e);
        }
    }
}
