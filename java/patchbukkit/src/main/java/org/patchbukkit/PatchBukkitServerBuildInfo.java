package org.patchbukkit;

import io.papermc.paper.ServerBuildInfo;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.OptionalInt;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

public final class PatchBukkitServerBuildInfo implements ServerBuildInfo {

    Key brandId;
    Instant startTime = Instant.now();
    private static final String BUILD_DEV = "DEV";

    @Override
    public Key brandId() {
        this.brandId = Key.key("pumpkinmc:paper");
        return brandId;
    }

    @Override
    public boolean isBrandCompatible(Key brandId) {
        return (
            brandId.equals(this.brandId) ||
            brandId.equals(Key.key("papermc:paper"))
        );
    }

    @Override
    public String brandName() {
        return "Pumpkin";
    }

    @Override
    public String minecraftVersionId() {
        return "1.21.11";
    }

    @Override
    public String minecraftVersionName() {
        return "1.21.11";
    }

    @Override
    public OptionalInt buildNumber() {
        return OptionalInt.empty();
    }

    @Override
    public Instant buildTime() {
        return this.startTime;
    }

    @Override
    public Optional<String> gitBranch() {
        return Optional.empty();
    }

    @Override
    public Optional<String> gitCommit() {
        return Optional.empty();
    }

    @Override
    public @NotNull String asString(
        final @NotNull StringRepresentation representation
    ) {
        final StringBuilder sb = new StringBuilder();
        sb.append(this.minecraftVersionId());
        sb.append('-');
        if (this.buildNumber().isPresent()) {
            sb.append(this.buildNumber().getAsInt());
        } else {
            sb.append(BUILD_DEV);
        }
        final boolean hasGitBranch = this.gitBranch().isPresent();
        final boolean hasGitCommit = this.gitCommit().isPresent();
        if (hasGitBranch || hasGitCommit) {
            sb.append('-');
        }
        if (
            hasGitBranch && representation == StringRepresentation.VERSION_FULL
        ) {
            sb.append(this.gitBranch().get());
            if (hasGitCommit) {
                sb.append('@');
            }
        }
        if (hasGitCommit) {
            sb.append(this.gitCommit().get());
        }
        if (representation == StringRepresentation.VERSION_FULL) {
            sb.append(' ');
            sb.append('(');
            sb.append(this.buildTime().truncatedTo(ChronoUnit.SECONDS));
            sb.append(')');
        }
        return sb.toString();
    }
}
