package org.jkiss.tools.rcplaunchconfig.maven.download;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.classpath.ClasspathTransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.jkiss.code.NotNull;
import org.jkiss.tools.rcplaunchconfig.maven.model.MavenDependency;
import org.jkiss.utils.Pair;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MavenArtifactDownloader {

    private static final String DEFAULT_REPO_LOCAL = String.format("%s/.m2/repository", System.getProperty("user.home"));
    private static final RemoteRepository DEFAULT_REPO_REMOTE = new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build();
    private static final Set<String> DEFAULT_SCOPES = Set.of(JavaScopes.COMPILE, JavaScopes.RUNTIME, JavaScopes.TEST);

    private static final Set<String> IMPORT_SCOPE = Set.of("import");

    private static final RepositorySystem system;

    static {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();

        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.addService(TransporterFactory.class, ClasspathTransporterFactory.class);

        system = locator.getService(RepositorySystem.class);
    }

    /**
     * resolve
     *
     * @param mavenDependencies eg: org.apache.logging.log4j:log4j-core:2.19.0
     * @param isBOM   if true, the dependencies are treated as a BOM (Bill of Materials) and will use the import scope
     * @return jar files absolute path
     **/
    public static List<Pair<MavenDependency, Path>> resolve(List<MavenDependency> mavenDependencies, boolean isBOM) throws DependencyResolutionException, NoLocalRepositoryManagerException {
        return resolve(mavenDependencies, isBOM ? DEFAULT_SCOPES : IMPORT_SCOPE, DEFAULT_REPO_LOCAL, List.of(DEFAULT_REPO_REMOTE), isBOM);
    }

    /**
     * resolve
     *
     * @param mavenDependencies      eg: org.apache.logging.log4j:log4j-core:2.19.0
     * @param scopes      default to DEFAULT_SCOPES if null or empty
     * @param localRepo   default to DEFAULT_REPO_LOCAL if null
     * @param remoteRepos default to DEFAULT_REPO_REMOTE if null or empty
     * @return jar files absolute path
     **/
    public static List<Pair<MavenDependency, Path>> resolve(
        @NotNull List<MavenDependency> mavenDependencies,
        @NotNull Set<String> scopes,
        String localRepo,
        List<RemoteRepository> remoteRepos,
        boolean isBOM
    ) throws DependencyResolutionException, NoLocalRepositoryManagerException {
        if (mavenDependencies.isEmpty()) {
            return Collections.emptyList();
        }
        if (scopes.isEmpty()) {
            scopes = DEFAULT_SCOPES;
        }
        if (localRepo == null) {
            localRepo = DEFAULT_REPO_LOCAL;
        }
        if (remoteRepos == null || remoteRepos.isEmpty()) {
            remoteRepos = List.of(DEFAULT_REPO_REMOTE);
        }

        RepositorySystemSession session = buildSession(localRepo);

        CollectRequest collectRequest = getCollectRequest(mavenDependencies, remoteRepos, isBOM);

        DependencyRequest request = new DependencyRequest(collectRequest, DependencyFilterUtils.classpathFilter(scopes));
        DependencyResult result = system.resolveDependencies(session, request);
        PreorderNodeListGenerator nodeListGenerator = new PreorderNodeListGenerator();
        result.getRoot().accept(nodeListGenerator);
        List<Pair<MavenDependency, Path>> resolvedDependencies = new ArrayList<>();
        for (ArtifactResult artifactResult : result.getArtifactResults()) {
            if (artifactResult.isResolved()) {
                MavenDependency mavenDependency = mavenDependencies.stream()
                    .filter(it -> it.getCoordinates().equals(artifactResult.getArtifact().getGroupId() + ":" +
                        artifactResult.getArtifact().getArtifactId() + ":" + artifactResult.getArtifact().getVersion()))
                    .findFirst().orElse(null);
                resolvedDependencies.add(new Pair<>(
                    mavenDependency,
                    Path.of(artifactResult.getArtifact().getFile().getAbsolutePath())
                ));

            }
        }
        return resolvedDependencies;
    }

    @NotNull
    private static CollectRequest getCollectRequest(
        @NotNull List<MavenDependency> mavenDependencies,
        List<RemoteRepository> remoteRepos,
        boolean isBOM
    ) {
        List<Dependency> dependencies = new ArrayList<>();
        for (MavenDependency mavenDependency : mavenDependencies) {
            String coordinates = mavenDependency.getCoordinates();
            Dependency dependency;
            if (!isBOM) {
                DefaultArtifact artifact = new DefaultArtifact(coordinates);
                dependency = new Dependency(artifact, null);
                dependencies.add(dependency);
            } else {
                // For BOM, we use the import scope
                DefaultArtifact artifact = new DefaultArtifact(
                    mavenDependency.group(),
                    mavenDependency.name(),
                    "pom",
                    mavenDependency.version());
                dependency = new Dependency(artifact, "import");
            }
            dependencies.add(dependency);
        }
        return new CollectRequest(dependencies, null, remoteRepos);
    }

    private static RepositorySystemSession buildSession(String localRepo) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, new LocalRepository(localRepo)));
        session.setCache(new DefaultRepositoryCache());
        return session;
    }
}