package org.patchbukkit.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;

public final class LibraryResolver {
    private static final java.util.logging.Logger LOGGER =
        java.util.logging.Logger.getLogger("LibraryResolver");

    private LibraryResolver() {}

    public static List<File> resolveLibraries(
        String coordinates,
        File baseDir
    ) {
        if (coordinates == null || coordinates.isBlank()) {
            return List.of();
        }

        try {
            var config = patchbukkit.bridge.NativeBridgeFfi.getPatchBukkitConfig(patchbukkit.common.EmptyRequest.newBuilder().build());
            if (config != null && !config.getDownloadDependencies()) {
                LOGGER.info("[PatchBukkit] Library downloading is disabled in configuration, skipping resolution.");
                return List.of();
            }
        } catch (Throwable ignored) {}

        Set<String> unique = new LinkedHashSet<>();
        for (String line : coordinates.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                unique.add(trimmed);
            }
        }

        if (unique.isEmpty()) {
            return List.of();
        }

        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }

        RepositorySystem system = newRepositorySystem();
        RepositorySystemSession session = newSession(system, baseDir);
        List<RemoteRepository> repos = new ArrayList<>(List.of(
            new RemoteRepository.Builder("papermc", "default", "https://repo.papermc.io/repository/maven-public/").build(),
            new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build(),
            new RemoteRepository.Builder("jitpack", "default", "https://jitpack.io/").build(),
            new RemoteRepository.Builder("codemc", "default", "https://repo.codemc.io/repository/maven-public/").build(),
            new RemoteRepository.Builder("enginehub", "default", "https://maven.enginehub.org/repo/").build(),
            new RemoteRepository.Builder("sonatype-snapshots", "default", "https://oss.sonatype.org/content/repositories/snapshots/").build(),
            new RemoteRepository.Builder("sonatype-releases", "default", "https://oss.sonatype.org/content/repositories/releases/").build(),
            new RemoteRepository.Builder("sponge", "default", "https://repo.spongepowered.org/repository/maven-public/").build(),
            new RemoteRepository.Builder("xenondevs", "default", "https://repo.xenondevs.xyz/releases").build(),
            new RemoteRepository.Builder("spigotmc", "default", "https://hub.spigotmc.org/nexus/content/repositories/snapshots/").build(),
            new RemoteRepository.Builder("miraculixx-snapshots", "default", "https://repo.miraculixx.de/snapshots").build(),
            new RemoteRepository.Builder("miraculixx-releases", "default", "https://repo.miraculixx.de/releases").build()
        ));

        Set<String> coordsToResolve = new LinkedHashSet<>();
        int customRepoIdx = 0;
        for (String item : unique) {
            String trimmed = item;
            if (trimmed.startsWith("repo:")) {
                trimmed = trimmed.substring(5).trim();
            }
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                customRepoIdx++;
                repos.add(new RemoteRepository.Builder("custom-repo-" + customRepoIdx, "default", trimmed).build());
            } else {
                coordsToResolve.add(trimmed);
            }
        }

        Set<File> files = new LinkedHashSet<>();
        for (String coord : coordsToResolve) {
            try {
                Dependency dependency = new Dependency(
                    new DefaultArtifact(coord),
                    "runtime"
                );
                CollectRequest collectRequest = new CollectRequest(
                    dependency,
                    repos
                );
                DependencyRequest request = new DependencyRequest(
                    collectRequest,
                    null
                );
                DependencyResult result = system.resolveDependencies(
                    session,
                    request
                );
                result
                    .getArtifactResults()
                    .forEach(artifactResult -> {
                        File file = artifactResult.getArtifact().getFile();
                        if (file != null && file.exists()) {
                            files.add(file);
                        }
                    });
            } catch (Exception e) {
                System.err.println(
                    "[PatchBukkit] Failed to resolve library " +
                    coord +
                    ": " +
                    e.getMessage()
                );
            }
        }

        return new ArrayList<>(files);
    }

    private static RepositorySystem newRepositorySystem() {
        var locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(
            RepositoryConnectorFactory.class,
            BasicRepositoryConnectorFactory.class
        );
        locator.addService(
            TransporterFactory.class,
            HttpTransporterFactory.class
        );
        return locator.getService(RepositorySystem.class);
    }

    private static RepositorySystemSession newSession(
        RepositorySystem system,
        File baseDir
    ) {
        DefaultRepositorySystemSession session =
            MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(baseDir);
        session.setLocalRepositoryManager(
            system.newLocalRepositoryManager(session, localRepo)
        );
        return session;
    }
}
