package org.jkiss.tools.rcplaunchconfig.maven.processors;

import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.tools.rcplaunchconfig.maven.download.MavenArtifactDownloader;
import org.jkiss.tools.rcplaunchconfig.maven.model.MavenDependency;
import org.jkiss.tools.rcplaunchconfig.maven.registry.MavenLocalArtifactRegistry;
import org.jkiss.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class MavenPomProcessor {
    private static final Logger log = LoggerFactory.getLogger(MavenPomProcessor.class);
    private static final String PARENT_TAG = "parent";
    private static final String RELATIVE_PATH_TAG = "relativePath";


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
    public static boolean collectArtifacts(@Nullable Path mavenPath) {
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
                NodeList parentNodes = doc.getElementsByTagName(PARENT_TAG);
                if (parentNodes != null && parentNodes.getLength() > 0) {
                    Element parentElement = (Element) parentNodes.item(0);
                    groupId = getTagValue(parentElement, "groupId");
                }
            }
            if (version == null || version.isEmpty()) {
                NodeList parentNodes = doc.getElementsByTagName(PARENT_TAG);
                if (parentNodes != null && parentNodes.getLength() > 0) {
                    Element parentElement = (Element) parentNodes.item(0);
                    version = getTagValue(parentElement, "version");
                }
            }
            MavenDependency artifact = new MavenDependency(groupId, artifactId, version);
            MavenLocalArtifactRegistry.INSTANCE.addProvidedDependency(artifact, mavenPath);

            return true;
        } catch (Exception e) {
            log.error("Error processing pom.xml", e);
            return false;
        }
    }

    /**
     * Processes the pom.xml file at the specified path and extracts its dependencies.
     *
     * @param path the path to the directory containing the pom.xml file.
     * @return a list of MavenDependency objects representing the dependencies found in the pom.xml.
     */
    @NotNull
    public static List<MavenDependency> processDependencies(@NotNull Path path) {
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
                if (groupId == null || groupId.isEmpty() ||
                    artifactId == null || artifactId.isEmpty()) {
                    log.warn("Skipping dependency with missing groupId or artifactId: {}:{}", groupId, artifactId);
                    continue;
                }
                String version = resolveVersion(doc, groupId, artifactId, unresolvedVersion, new LinkedHashSet<>(), path.toFile());
                if (version != null && !version.startsWith("${")) {
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

    @Nullable
    // Helper method to retrieve the text content of a given tag from an Element.
    private static String getTagValue(@NotNull Element element, @NotNull String tag) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(node.getNodeName())) {
                return node.getTextContent().trim();
            }
        }
        return null;
    }

    @Nullable
    // Helper method to retrieve the text content of the first occurrence of a given tag from the Document.
    private static String getTagValue(@NotNull Document doc, @NotNull String tag) {
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

    @Nullable
    private static String resolveVersion(
        @NotNull Document doc,
        @NotNull String groupID,
        @NotNull String artifactID,
        @Nullable String version,
        @NotNull LinkedHashSet<String> visitedPomPaths,
        @NotNull File currentPomFile
    ) throws IOException, ParserConfigurationException, SAXException {
        if (version == null) {
            // Try to get version from <parent>
            return resolveDependencyManagementVersionRecursive(doc, groupID, artifactID, currentPomFile, visitedPomPaths);
        }

        if (version.startsWith("${") && version.endsWith("}")) {
            String propertyName = version.substring(2, version.length() - 1);
            return resolveProperty(doc, propertyName, visitedPomPaths, currentPomFile);
        }

        return version;
    }


    private static String getProjectVersion(
        @NotNull Document doc,
        @NotNull File currentPomFile,
        @NotNull LinkedHashSet<String> visitedPomPaths
    ) {
        String version = getTagValue(doc.getDocumentElement(), "version");
        if (version != null) {
            return version;
        }

        // Fall back to parent's version
        return getParentTagValue(doc, "version", currentPomFile, visitedPomPaths);
    }

    private static String resolveProperty(
        @NotNull Document doc,
        @NotNull String propertyName,
        @NotNull LinkedHashSet<String> visitedPomPaths,
        @NotNull File currentPomFile
    ) {
        // Handle built-in Maven properties
        switch (propertyName) {
            case "project.version":
            case "version":
                return getProjectVersion(doc, currentPomFile, visitedPomPaths);
            case "project.parent.version":
                return getParentTagValue(doc, "version", currentPomFile, visitedPomPaths);
            default:
                // Continue to search for the property in the pom.xml
                break;
        }
        String foundPropertyValue = searchForLatestProperty(propertyName, visitedPomPaths);
        if (foundPropertyValue != null) {
            return foundPropertyValue;
        }
        // Check if the property was previously
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

    private static String searchForLatestProperty(@NotNull String propertyName, @NotNull LinkedHashSet<String> visitedPomPaths) {
        for (String visitedPomPath : visitedPomPaths) {
            File visitedPomFile = new File(visitedPomPath);
            if (!visitedPomFile.exists()) {
                continue; // Skip non-existing POMs
            }
            try {
                Document parentDoc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(visitedPomFile);
                parentDoc.getDocumentElement().normalize();
                NodeList propsNodes = parentDoc.getElementsByTagName("properties");
                if (propsNodes.getLength() > 0) {
                    Element properties = (Element) propsNodes.item(0);
                    String propertyValue = getTagValue(properties, propertyName);
                    if (propertyValue != null && !propertyValue.startsWith("${")) {
                        return propertyValue;
                    }
                }
            } catch (Exception e) {
                log.info("Failed to parse parent pom.xml: {}", visitedPomFile, e);
            }
        }
        return null;
    }

    private static String getDependencyManagementVersion(
        @NotNull Document doc,
        @NotNull String groupId,
        @NotNull String artifactId,
        @NotNull LinkedHashSet<String> visitedPomPaths,
        @NotNull File currentPomFile
    ) throws IOException, ParserConfigurationException, SAXException {
        NodeList depMgmtList = doc.getElementsByTagName("dependencyManagement");
        for (int i = 0; i < depMgmtList.getLength(); i++) {
            Element depMgmt = (Element) depMgmtList.item(i);
            NodeList dependencies = depMgmt.getElementsByTagName("dependency");
            for (int j = 0; j < dependencies.getLength(); j++) {
                Element dependency = (Element) dependencies.item(j);
                String gid = getTagValue(dependency, "groupId");
                String aid = getTagValue(dependency, "artifactId");

                if (groupId.equals(gid) && artifactId.equals(aid)) {
                    return resolveVersion(doc, groupId, artifactId, getTagValue(dependency, "version"), visitedPomPaths, currentPomFile);
                }
            }
        }
        return null;
    }

    @Nullable
    private static String resolveDependencyManagementVersionRecursive(
        @NotNull Document doc,
        @NotNull String groupId,
        @NotNull String artifactId,
        @NotNull File currentPomFile,
        @NotNull LinkedHashSet<String> visitedPomPaths
    ) throws IOException, ParserConfigurationException, SAXException {

        // Check local dependencyManagement
        String version = getDependencyManagementVersion(doc, groupId, artifactId, visitedPomPaths, currentPomFile);
        if (version != null) {
            return version;
        }
        String versionFromBoms = tryToGetVersionFromBoms(doc, groupId, artifactId, currentPomFile, visitedPomPaths);
        if (versionFromBoms != null) {
            return versionFromBoms;
        }

        // Check parent if not found
        NodeList parentNodes = doc.getElementsByTagName(PARENT_TAG);
        if (parentNodes.getLength() > 0) {
            Element parent = (Element) parentNodes.item(0);
            String relativePath = getTagValue(parent, RELATIVE_PATH_TAG);
            if (relativePath == null || relativePath.isBlank()) {
                relativePath = "../pom.xml"; // default
            }
            if (!relativePath.endsWith("pom.xml")) {
                relativePath = relativePath + "/pom.xml";
            }

            File parentPom = new File(
                currentPomFile.isDirectory() ? currentPomFile : currentPomFile.getParentFile(),
                relativePath
            ).getAbsoluteFile();

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
                log.info("Failed to parse parent pom.xml: {}", parentPom, e);
            }
        }

        return null;
    }

    private static String tryToGetVersionFromBoms(
        @NotNull Document doc,
        @NotNull String groupId,
        @NotNull String artifactId,
        @NotNull File currentPomFile,
        @NotNull LinkedHashSet<String> visitedPomPaths
    ) throws IOException, ParserConfigurationException, SAXException {
        List<MavenDependency> mavenDependencies = listBOMDependencies(doc, visitedPomPaths, currentPomFile);
        String version = null;
        for (MavenDependency bomDependency : mavenDependencies) {
            if (!MavenLocalArtifactRegistry.INSTANCE.isLocalThirdParty(bomDependency)) {
                try {
                    List<Pair<MavenDependency, Path>> resolvedDependencies = MavenArtifactDownloader.resolve(List.of(bomDependency), true);
                    for (Pair<MavenDependency, Path> resolvedDependency : resolvedDependencies) {
                        MavenLocalArtifactRegistry.INSTANCE.addLocalThirdPartyDependency(
                            resolvedDependency.getFirst(),
                            resolvedDependency.getSecond()
                        );
                    }
                } catch (DependencyResolutionException | NoLocalRepositoryManagerException e) {
                    log.error("Failed to resolve BOM dependency: {}", bomDependency, e);
                }
            }
            Path dowloadedDependencyPath = MavenLocalArtifactRegistry.INSTANCE.getDowloadedDependencyPath(bomDependency);
            try (var inputStream = Files.newInputStream(dowloadedDependencyPath)) {
                // Parse the pom.xml file.
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document bomDoc = builder.parse(inputStream);
                doc.getDocumentElement().normalize();
                String dependencyManagementVersion = getDependencyManagementVersion(
                    bomDoc,
                    groupId,
                    artifactId,
                    new LinkedHashSet<>(),
                    dowloadedDependencyPath.toFile()
                );
                if (dependencyManagementVersion != null) {
                    version = dependencyManagementVersion;
                }
            }
        }
        return version;
    }

    private static List<MavenDependency> listBOMDependencies(
        Document doc,
        LinkedHashSet<String> visitedPomPaths,
        File currentPomFile
    ) throws IOException, ParserConfigurationException, SAXException {
        List<MavenDependency> bomDependencies = new ArrayList<>();
        // Check BOM (dependencyManagement with type "pom")
        NodeList bomList = doc.getElementsByTagName("dependencyManagement");
        for (int i = 0; i < bomList.getLength(); i++) {
            Element bomElement = (Element) bomList.item(i);
            NodeList dependencies = bomElement.getElementsByTagName("dependency");
            for (int j = 0; j < dependencies.getLength(); j++) {
                Element dependency = (Element) dependencies.item(j);
                if (getTagValue(dependency, "type") == null || !"pom".equals(getTagValue(dependency, "type"))) {
                    continue; // Only consider BOMs
                }
                // TODO potentially resolve local BOMS
                String gid = getTagValue(dependency, "groupId");
                String aid = getTagValue(dependency, "artifactId");
                String bomVersion = getTagValue(dependency, "version");
                if (gid == null || aid == null || bomVersion == null) {
                    log.warn("Skipping BOM dependency with missing groupId, artifactId or version: {}:{}:{}", gid, aid, bomVersion);
                    continue;
                }
                bomVersion = resolveVersion(doc, aid, gid, bomVersion, visitedPomPaths, currentPomFile);
                bomDependencies.add(new MavenDependency(gid, aid, bomVersion));
            }
        }
        return bomDependencies;
    }

    public static String getParentTagValue(Document doc, String tag, File currentPomFile, LinkedHashSet<String> visitedPomPaths) {
        NodeList parentNodes = doc.getElementsByTagName(PARENT_TAG);
        if (parentNodes.getLength() > 0) {
            Element parent = (Element) parentNodes.item(0);
            String relativePath = getTagValue(parent, RELATIVE_PATH_TAG);
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
                log.info("Failed to parse parent pom.xml: {}", parentPom, e);
            }
        }

        return null;
    }

    private static void tryToDownloadNonProvidedDependencies(
        @NotNull List<MavenDependency> dependencyList
    ) throws DependencyResolutionException, NoLocalRepositoryManagerException {
        List<MavenDependency> dependenciesToDownload = dependencyList.stream()
            .filter(it -> !MavenLocalArtifactRegistry.INSTANCE.isProvided(it))
            .toList();
        List<Pair<MavenDependency, Path>> resolvedDependencies = MavenArtifactDownloader.resolve(dependenciesToDownload, false);
        for (Pair<MavenDependency, Path> resolvedDependency : resolvedDependencies) {
            MavenLocalArtifactRegistry.INSTANCE.addLocalThirdPartyDependency(
                resolvedDependency.getFirst(),
                resolvedDependency.getSecond()
            );
            if (!MavenLocalArtifactRegistry.INSTANCE.isProvided(resolvedDependency.getFirst())) {
                dependencyList.add(resolvedDependency.getFirst());
            }
        }
    }
}
