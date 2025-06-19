package org.jkiss.tools.rcplaunchconfig.maven.processors;

import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.jkiss.tools.rcplaunchconfig.maven.download.MavenArtifactDownloader;
import org.jkiss.tools.rcplaunchconfig.maven.model.MavenDependency;
import org.jkiss.tools.rcplaunchconfig.registry.MavenLocalArtifactRegistry;
import org.jkiss.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MavenPomProcessor {
    private static final Logger log = LoggerFactory.getLogger(MavenPomProcessor.class);

    private Map<String, Path> coordinatesToPath = new HashMap<>();

    /**
     * check pom.xml if packaging is not eclipse-plugin return true
     */
    public static boolean isMavenModule(Path bundlePath) {
        Path pomPath = bundlePath.resolve("pom.xml");
        if (!Files.exists(pomPath)) {
            log.info("pom.xml not found in the current directory.");
            return false;
        }

        try (var inputStream = Files.newInputStream(pomPath)) {
            // Parse the pom.xml file.
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputStream);
            doc.getDocumentElement().normalize();

            // Extract main artifact details.
            String packaging = getTagValue(doc, "packaging");
            return !"eclipse-plugin".equals(packaging);
        } catch (Exception e) {
            log.error("Error processing pom.xml", e);
            return false;
        }
    }

    /**
     * Processes the pom.xml in the current directory.
     * <p>
     * If pom.xml exists, this method extracts the project's groupId, artifactId, version,
     * and packaging. If the packaging is not "eclipse-plugin", it adds only the main artifact
     * (ignoring any dependencies) to the MavenLocalArtifactRepository and returns true.
     * If the packaging is "eclipse-plugin" or any error occurs, it returns false.
     * </p>
     *
     * @return true if the artifact was processed and added, false otherwise.
     */
    public static boolean collectArtifacts(Path mavenPath) {
        if (mavenPath == null) {
            return false;
        }
        Path pomPath = mavenPath.resolve("pom.xml");
        if (!Files.exists(pomPath)) {
            log.info("pom.xml not found in the current directory.");
            return false;
        }

        try (var inputStream = Files.newInputStream(pomPath)) {
            // Parse the pom.xml file.
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputStream);
            doc.getDocumentElement().normalize();

            // Extract main artifact details.
            String groupId = getTagValue(doc, "groupId");
            // Fallback: if groupId is not defined on the project, check the parent.
            String artifactId = getTagValue(doc, "artifactId");
            String version = getTagValue(doc, "version");
            if (groupId == null || groupId.isEmpty()) {
                NodeList parentNodes = doc.getElementsByTagName("parent");
                if (parentNodes != null && parentNodes.getLength() > 0) {
                    Element parentElement = (Element) parentNodes.item(0);
                    groupId = getTagValue(parentElement, "groupId");
                }
            }
            if (version == null || version.isEmpty()) {
                NodeList parentNodes = doc.getElementsByTagName("parent");
                if (parentNodes != null && parentNodes.getLength() > 0) {
                    Element parentElement = (Element) parentNodes.item(0);
                    version = getTagValue(parentElement, "version");
                }
            }
            MavenDependency artifact = new MavenDependency(groupId, artifactId, version);
            MavenLocalArtifactRegistry.INSTANCE.addProvidedDependency(artifact, mavenPath);
            log.info("Added artifact: {}", artifact);

            return true;
        } catch (Exception e) {
            log.error("Error processing pom.xml", e);
            return false;
        }
    }

    // Helper method to retrieve the text content of a given tag from an Element.
    private static String getTagValue(Element element, String tag) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(node.getNodeName())) {
                return node.getTextContent().trim();
            }
        }
        return null;
    }

    // Helper method to retrieve the text content of the first occurrence of a given tag from the Document.
    private static String getTagValue(Document doc, String tag) {
        Element root = doc.getDocumentElement();
        NodeList children = root.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(node.getNodeName())) {
                return node.getTextContent().trim();
            }
        }
        return null;
    }


    public static List<MavenDependency> processDependencies(Path path) {
        Path pomXMl = path.resolve("pom.xml");
        try (var inputStream = Files.newInputStream(pomXMl)) {
            // Parse the pom.xml file.
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputStream);
            doc.getDocumentElement().normalize();
            NodeList dependencies = doc.getElementsByTagName("dependency");
            List<MavenDependency> dependencyList = new ArrayList<>();
            for (int i = 0; i < dependencies.getLength(); i++) {
                Element dependencyElement = (Element) dependencies.item(i);
                String groupId = getTagValue(dependencyElement, "groupId");
                String artifactId = getTagValue(dependencyElement, "artifactId");
                String unresolvedVersion = getTagValue(dependencyElement, "version");
                String version = resolveVersion(doc, artifactId, groupId, unresolvedVersion, new HashSet<>(), path.toFile());
                if (groupId != null && artifactId != null && (version != null && !version.startsWith("${"))) {
                    dependencyList.add(new MavenDependency(groupId, artifactId, version));
                }
            }
            // if version not found check parent
            tryToDownloadNonProvidedDependencies(dependencyList);
            return dependencyList;
        } catch (Exception e) {
            log.error("Error processing pom.xml", e);
            return List.of();
        }
    }

    public static String resolveVersion(Document doc, String groupID, String artifactID, String version, Set<String> visitedPomPaths, File currentPomFile) {
        if (version == null) {
            // Try to get version from <parent>
            return getDependencyManagementVersion(doc, groupID, artifactID);
        }

        if (version.startsWith("${") && version.endsWith("}")) {
            String propertyName = version.substring(2, version.length() - 1);
            return resolveProperty(doc, propertyName, visitedPomPaths, currentPomFile);
        }

        return version;
    }


    private static String getProjectVersion(Document doc, File currentPomFile, Set<String> visitedPomPaths) {
        String version = getTagValue(doc.getDocumentElement(), "version");
        if (version != null) {
            return version;
        }

        // Fall back to parent's version
        return getParentTagValue(doc, "version", currentPomFile, visitedPomPaths);
    }

    private static String resolveProperty(Document doc, String propertyName, Set<String> visitedPomPaths, File currentPomFile) {
        // Handle built-in Maven properties
        switch (propertyName) {
            case "project.version":
            case "version":
                return getProjectVersion(doc, currentPomFile, visitedPomPaths);
            case "project.parent.version":
                return getParentTagValue(doc, "version", currentPomFile, visitedPomPaths);
        }

        // Try from <properties>
        NodeList propsNodes = doc.getElementsByTagName("properties");
        if (propsNodes.getLength() > 0) {
            Element properties = (Element) propsNodes.item(0);
            String value = getTagValue(properties, propertyName);
            if (value != null && !value.startsWith("${")) {
                return value;
            } else if (value != null) {
                return resolveProperty(doc, value, visitedPomPaths, currentPomFile);
            }
        }

        // Try from <parent>/<properties>
        return getParentTagValue(doc, propertyName, currentPomFile, visitedPomPaths);
    }

    public static String getDependencyManagementVersion(Document doc, String groupId, String artifactId) {
        NodeList depMgmtList = doc.getElementsByTagName("dependencyManagement");
        for (int i = 0; i < depMgmtList.getLength(); i++) {
            Element depMgmt = (Element) depMgmtList.item(i);
            NodeList dependencies = depMgmt.getElementsByTagName("dependency");
            for (int j = 0; j < dependencies.getLength(); j++) {
                Element dependency = (Element) dependencies.item(j);
                String gid = getTagValue(dependency, "groupId");
                String aid = getTagValue(dependency, "artifactId");

                if (groupId.equals(gid) && artifactId.equals(aid)) {
                    return getTagValue(dependency, "version");
                }
            }
        }
        return null;
    }


    public static String resolveDependencyManagementVersionRecursive(
        Document doc,
        String groupId,
        String artifactId,
        File currentPomFile,
        Set<String> visitedPomPaths) {

        // Check local dependencyManagement
        String version = getDependencyManagementVersion(doc, groupId, artifactId);
        if (version != null) {
            return version;
        }

        // Check parent if not found
        NodeList parentNodes = doc.getElementsByTagName("parent");
        if (parentNodes.getLength() > 0) {
            Element parent = (Element) parentNodes.item(0);
            String relativePath = getTagValue(parent, "relativePath");
            if (relativePath == null || relativePath.isBlank()) {
                relativePath = "../pom.xml"; // default
            }
            if (!relativePath.endsWith("pom.xml")) {
                relativePath = relativePath + "/pom.xml";
            }

            File parentPom = new File(currentPomFile.isDirectory() ? currentPomFile : currentPomFile.getParentFile(), relativePath).getAbsoluteFile();

            if (!parentPom.exists() || visitedPomPaths.contains(parentPom.getAbsolutePath())) {
                return null;
            }

            visitedPomPaths.add(parentPom.getAbsolutePath());

            try {
                Document parentDoc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(parentPom);
                parentDoc.getDocumentElement().normalize();

                return resolveDependencyManagementVersionRecursive(parentDoc, groupId, artifactId, parentPom, visitedPomPaths);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public static String getParentTagValue(Document doc, String tag, File currentPomFile, Set<String> visitedPomPaths) {
        NodeList parentNodes = doc.getElementsByTagName("parent");
        if (parentNodes.getLength() > 0) {
            Element parent = (Element) parentNodes.item(0);
            String relativePath = getTagValue(parent, "relativePath");
            if (relativePath == null || relativePath.isBlank()) {
                relativePath = "../pom.xml"; // default
            }
            if (!relativePath.endsWith("pom.xml")) {
                relativePath = relativePath + "/pom.xml"; // remove "pom.xml"
            }
            File parentPom = new File(
                currentPomFile.isDirectory() ? currentPomFile : currentPomFile.getParentFile(),
                relativePath
            ).getAbsoluteFile();

            // Prevent infinite loop
            if (visitedPomPaths.contains(parentPom.getAbsolutePath()) || !parentPom.exists()) {
                return null;
            }

            visitedPomPaths.add(parentPom.getAbsolutePath());

            try {
                Document parentDoc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(parentPom);
                parentDoc.getDocumentElement().normalize();

                // Try to resolve tag in parent
                if ("version".equals(tag)) {
                    return getTagValue(parent, tag); // version is directly in <parent>
                } else {
                    return resolveProperty(parentDoc, tag, visitedPomPaths, parentPom);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    private static void tryToDownloadNonProvidedDependencies(List<MavenDependency> dependencyList)
    throws DependencyResolutionException, NoLocalRepositoryManagerException {
        List<MavenDependency> dependenciesToDownload = dependencyList.stream()
            .filter(it -> !MavenLocalArtifactRegistry.INSTANCE.isProvidedOrDownloaded(it))
            .toList();
        List<Pair<MavenDependency, Path>> resolvedDependencies = MavenArtifactDownloader.resolve(dependenciesToDownload);
        for (Pair<MavenDependency, Path> resolvedDependency : resolvedDependencies) {
            MavenLocalArtifactRegistry.INSTANCE.addLocalThirdPartyDependency(
                resolvedDependency.getFirst(),
                resolvedDependency.getSecond()
            );
        }
    }
}
