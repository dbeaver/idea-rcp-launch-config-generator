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
import org.eclipse.aether.graph.Exclusion;
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
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.tools.rcplaunchconfig.maven.model.MavenDependency;
import org.jkiss.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

public class MavenArtifactDownloader {
    private static final Logger log = LoggerFactory.getLogger(MavenArtifactDownloader.class);

    private static final String DEFAULT_REPO_LOCAL = String.format("%s/.m2/repository", System.getProperty("user.home"));
    private static final List<RemoteRepository> REPOS = List.of(
        new RemoteRepository.Builder("spring-milestones", "default", "https://repo.spring.io/milestone").build(),
        new RemoteRepository.Builder("spring-releases", "default", "https://repo.spring.io/release").build(),
        new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build()
    );

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
     * @param isBOM             if true, the dependencies are treated as a BOM (Bill of Materials) and will use the import scope
     * @return jar files absolute path
     **/
    public static List<Pair<MavenDependency, Path>> resolve(@NotNull List<MavenDependency> mavenDependencies, boolean isBOM
    ) throws DependencyResolutionException, NoLocalRepositoryManagerException {
        return resolve(mavenDependencies, isBOM ? DEFAULT_SCOPES : IMPORT_SCOPE, DEFAULT_REPO_LOCAL, REPOS, isBOM);
    }

    /**
     * resolve
     *
     * @param mavenDependencies eg: org.apache.logging.log4j:log4j-core:2.19.0
     * @param scopes            default to DEFAULT_SCOPES if null or empty
     * @param localRepo         default to DEFAULT_REPO_LOCAL if null
     * @param remoteRepos       default to DEFAULT_REPO_REMOTE if null or empty
     * @return jar files absolute path
     **/
    public static List<Pair<MavenDependency, Path>> resolve(
        @NotNull List<MavenDependency> mavenDependencies,
        @NotNull Set<String> scopes,
        @Nullable String localRepo,
        @Nullable List<RemoteRepository> remoteRepos,
        boolean isBOM
    ) throws DependencyResolutionException {
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
            remoteRepos = REPOS;
        }

        RepositorySystemSession session = buildSession(localRepo);
        CollectRequest collectRequest = getCollectRequest(mavenDependencies, remoteRepos, isBOM);

        DependencyRequest request = new DependencyRequest(
            collectRequest,
            null
        );
        DependencyResult result = system.resolveDependencies(session, request);
        result.getRoot().accept(new org.eclipse.aether.graph.DependencyVisitor() {
            @Override
            public boolean visitEnter(org.eclipse.aether.graph.DependencyNode node) {
                //log.info("  Processing maven dependency: {}", node.getArtifact());
                return true;
            }

            @Override
            public boolean visitLeave(org.eclipse.aether.graph.DependencyNode node) {
                return true;
            }
        });
        List<Pair<MavenDependency, Path>> resolvedDependencies = new ArrayList<>();

        for (ArtifactResult artifactResult : result.getArtifactResults()) {
            if (artifactResult.isResolved()) {
                Optional<MavenDependency> existingDependency = mavenDependencies.stream().filter(
                    dep -> dep.getCoordinates().equals(artifactResult.getArtifact().getGroupId() + ":" +
                        artifactResult.getArtifact().getArtifactId() + ":" + artifactResult.getArtifact().getVersion())
                ).findFirst();
                if (existingDependency.isPresent()) {
                    resolvedDependencies.add(new Pair<>(
                        existingDependency.get(),
                        Path.of(artifactResult.getArtifact().getFile().getAbsolutePath())
                    ));
                } else {
                    resolvedDependencies.add(new Pair<>(
                        new MavenDependency(
                            artifactResult.getArtifact().getGroupId(),
                            artifactResult.getArtifact().getArtifactId(),
                            artifactResult.getArtifact().getVersion(),
                            List.of()
                        ),
                        Path.of(artifactResult.getArtifact().getFile().getAbsolutePath())
                    ));
                }
            } else {
                log.error("UNRESOLVED: " + artifactResult.getArtifact() + " - " + artifactResult.getExceptions());
            }
        }
        return resolvedDependencies;
    }

    @NotNull
    private static CollectRequest getCollectRequest(
        @NotNull List<MavenDependency> mavenDependencies,
        @NotNull List<RemoteRepository> remoteRepos,
        boolean isBOM
    ) {
        List<Dependency> dependencies = new ArrayList<>();
        for (MavenDependency mavenDependency : mavenDependencies) {
            List<Exclusion> exclusions = mavenDependency.exclusions().stream()
                .map(it -> new Exclusion(it.group(), it.name(), "*", "*")).toList();
            String coordinates = mavenDependency.getCoordinates();
            Dependency dependency;
            if (!isBOM) {
                DefaultArtifact artifact = new DefaultArtifact(coordinates);
                dependency = new Dependency(artifact, "compile", false, exclusions);
            } else {
                // For BOM, we use the import scope
                DefaultArtifact artifact = new DefaultArtifact(
                    mavenDependency.group(),
                    mavenDependency.name(),
                    "pom",
                    mavenDependency.version()
                );
                dependency = new Dependency(artifact, "import", false, exclusions);
            }

            dependencies.add(dependency);
        }

        return new CollectRequest(dependencies, null, remoteRepos);
    }

    @NotNull
    private static RepositorySystemSession buildSession(@NotNull String localRepo) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, new LocalRepository(localRepo)));
        session.setCache(new DefaultRepositoryCache());
        Properties systemProps = new Properties();
        systemProps.setProperty("java.version", "17");
        session.setSystemProperties(systemProps);
        return session;
    }
}