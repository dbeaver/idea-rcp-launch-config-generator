package org.jkiss.tools.rcplaunchconfig.producers.iml;

import org.jkiss.code.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.nio.file.Path;

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

            // Add or update FormatOnSaveOptions component
            addOrUpdateComponent(doc, "FormatOnSaveOptions", new String[][]{
                    {"myFormatOnlyChangedLines", "true"},
                    {"myRunOnSave", "true"}
            });

            // Add or update OptimizeOnSaveOptions component
            addOrUpdateComponent(doc, "OptimizeOnSaveOptions", new String[][]{
                    {"myRunOnSave", "true"}
            });
            addOrUpdateComponent(doc, "UpdateCopyrightCheckinHandler", new String[][]{
                {"UPDATE_COPYRIGHT", "true"}
            });
            addOrUpdateComponent(doc, "OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT", new String[][]{
                {"REFORMAT_BEFORE_PROJECT_COMMIT", "true"}
            });
            // Update PropertiesComponent
            updatePropertiesComponent(doc);

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

    private static void addOrUpdateComponent(Document doc, String componentName, String[][] options) {
        NodeList components = doc.getElementsByTagName("component");
        Element targetComponent = null;

        // Check if the component exists
        for (int i = 0; i < components.getLength(); i++) {
            Element component = (Element) components.item(i);
            if (component.getAttribute("name").equals(componentName)) {
                targetComponent = component;
                break;
            }
        }

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

    private static void updatePropertiesComponent(Document doc) {
        NodeList components = doc.getElementsByTagName("component");
        Element propertiesComponent = null;

        // Find the PropertiesComponent
        for (int i = 0; i < components.getLength(); i++) {
            Element component = (Element) components.item(i);
            if (component.getAttribute("name").equals("PropertiesComponent")) {
                propertiesComponent = component;
                break;
            }
        }

        if (propertiesComponent != null) {
            // Find the CDATA section
            Node cdataNode = null;
            for (int i = 0; i < propertiesComponent.getChildNodes().getLength(); i++) {
                Node node = propertiesComponent.getChildNodes().item(i);
                if (node.getNodeType() == Node.CDATA_SECTION_NODE) {
                    cdataNode = node;
                    break;
                }
            }

            if (cdataNode != null) {
                String cdataContent = cdataNode.getNodeValue();

                // Add the new key if not already present
                if (!cdataContent.contains("\"update.copyright.on.save\"")) {
                    int insertPosition = cdataContent.indexOf("}");
                    String newKey = "\n    \"update.copyright.on.save\": \"true\"\n";
                    cdataContent = cdataContent.substring(0, insertPosition - 1).trim() + ",\n" + newKey +
                            "\n" + cdataContent.substring(insertPosition).trim();
                    Node parentNode = cdataNode.getParentNode();
                    parentNode.removeChild(cdataNode);
                    CDATASection cdataSection = parentNode.getOwnerDocument().createCDATASection(cdataContent);
                    parentNode.appendChild(cdataSection);
                }
            }
        }
    }
}