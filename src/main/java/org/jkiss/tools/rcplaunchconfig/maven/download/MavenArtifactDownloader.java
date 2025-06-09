package org.jkiss.tools.rcplaunchconfig.maven.download;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import java.io.File;

public class MavenArtifactDownloader {

    public static File downloadDependency(String groupId, String artifactId, String version) throws Exception {
        RepositorySystem system = newRepositorySystem();
        RepositorySystemSession session = newSession(system);
        RemoteRepository central = new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build();

        Artifact artifact = new DefaultArtifact(groupId + ":" + artifactId + ":" + version);
        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(artifact);
        request.addRepository(central);

        ArtifactResult result = system.resolveArtifact(session, request);
        return result.getArtifact().getFile(); // Return the downloaded JAR file
    }

    private static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = DefaultServiceLocator.;;
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(org.eclipse.aether.spi.connector.transport.TransporterFactory.class, HttpTransporterFactory.class);
        return locator.getService(RepositorySystem.class);
    }

    private static RepositorySystemSession newSession(RepositorySystem system) {
        return .newSession();
    }

    public static void main(String[] args) {
        try {
            File jar = downloadDependency("org.apache.commons", "commons-lang3", "3.12.0");
            System.out.println("Downloaded: " + jar.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}