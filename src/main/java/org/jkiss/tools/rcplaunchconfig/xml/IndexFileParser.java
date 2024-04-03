
package org.jkiss.tools.rcplaunchconfig.xml;

import org.jkiss.tools.rcplaunchconfig.Artifact;
import org.jkiss.tools.rcplaunchconfig.util.BundleVersion;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IndexFileParser {
    public static final IndexFileParser INSTANCE = new IndexFileParser();
    private final DocumentBuilder builder;

    public List<String> listChildrenRepositoriesFromFile(File file) {
        try {
            Document doc = builder.parse(file);
            NodeList nodeList = doc.getElementsByTagName("child");
            List<String> locations = new ArrayList<>();
            for (int temp = 0; temp < nodeList.getLength(); temp++) {
                Node node = nodeList.item(temp);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) node;
                    locations.add(childElement.getAttribute("location"));
                }
            }
            return locations;
        } catch (IOException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Artifact> listArtifactsFromIndexFile(File file) {
        try {
            Document document = builder.parse(file);
            NodeList artifactNodeList = getArtifactRootNode(document);
            ArrayList<Artifact> artifacts = new ArrayList<>();
            for (int i = 0; i < artifactNodeList.getLength(); i++) {
                Node node = artifactNodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String classifier = element.getAttribute("classifier");
                    String id = element.getAttribute("id");
                    BundleVersion bundleVersion = new BundleVersion(element.getAttribute("version"));
                    artifacts.add(new Artifact(classifier, id, bundleVersion));
                } else {
                    throw new RuntimeException();
                }
            }
            return artifacts;
        } catch (IOException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private static NodeList getArtifactRootNode(Document document) {
        Element root = document.getDocumentElement();
        NodeList artifactRepository = root.getElementsByTagName("artifactRepository");
        for (int i = 0; i < artifactRepository.getLength(); i++) {
            Node item = artifactRepository.item(i);
            if (item.getNodeType() == Node.ELEMENT_NODE && "properties".equals(((Element) item).getTagName())) {
                root = ((Element) item);
                break;
            }
        }
        return root.getElementsByTagName("artifact");
    }

    private IndexFileParser() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }
}
