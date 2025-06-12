package org.jkiss.tools.rcplaunchconfig.maven.processors;

import com.dbeaver.osgi.dependency.processing.BundleInfo;
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
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MavenPomProcessor {
    private static final Logger log = LoggerFactory.getLogger(MavenPomProcessor.class);

    private Map<String, Path> coordinatesToPath = new HashMap<>();

    /**
     *
     * check pom.xml if packaging is not eclipse-plugin return true
     */
    public static boolean isMavenBundle(Path bundlePath) {
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
    public static boolean collectArtifacts(BundleInfo bundleInfo) {
        if (bundleInfo == null || bundleInfo.getPath() == null) {
            return false;
        }
        Path pomPath = bundleInfo.getPath().resolve("pom.xml");
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
            if (groupId == null || groupId.isEmpty()) {
                NodeList parentNodes = doc.getElementsByTagName("parent");
                if (parentNodes != null && parentNodes.getLength() > 0) {
                    Element parentElement = (Element) parentNodes.item(0);
                    groupId = getTagValue(parentElement, "groupId");
                }
            }
            String artifactId = getTagValue(doc, "artifactId");
            String version = getTagValue(doc, "version");
            MavenDependency artifact = new MavenDependency(groupId, artifactId, version);
            MavenLocalArtifactRegistry.INSTANCE.addProvidedDependency(artifact, bundleInfo);
            log.info("Added artifact: {}", artifact);

            return true;
        } catch (Exception e) {
            log.error("Error processing pom.xml", e);
            return false;
        }
    }

    // Helper method to retrieve the text content of a given tag from an Element.
    private static String getTagValue(Element element, String tag) {
        NodeList nodeList = element.getElementsByTagName(tag);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent().trim();
        }
            return null;
        }

        // Helper method to retrieve the text content of the first occurrence of a given tag from the Document.
        private static String getTagValue(Document doc, String tag) {
            NodeList nodeList = doc.getElementsByTagName(tag);
            if (nodeList != null && nodeList.getLength() > 0) {
                return nodeList.item(0).getTextContent().trim();
        }
        return null;
    }


    public static List<MavenDependency> processDependencies(Path path) {
        Path pomXMl = path.resolve("pom.xml");
        try {
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
                    String version = getTagValue(dependencyElement, "version");
                    if (groupId != null && artifactId != null && version != null) {
                        dependencyList.add(new MavenDependency(groupId, artifactId, version));
                    }
                }
                tryToDownloadNonProvidedDependencies(dependencyList);
                return dependencyList;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            log.error("Error processing pom.xml", e);
            return List.of();
        }
    }

    private static void tryToDownloadNonProvidedDependencies(List<MavenDependency> dependencyList)
    throws DependencyResolutionException, NoLocalRepositoryManagerException {
        List<MavenDependency> dependenciesToDownload = dependencyList.stream().filter(it -> !MavenLocalArtifactRegistry.INSTANCE.isProvidedOrDownloaded(it))
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
