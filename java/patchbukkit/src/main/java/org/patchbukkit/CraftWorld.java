package org.patchbukkit;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.patchbukkit.world.PatchBukkitWorld;

public class CraftWorld extends PatchBukkitWorld {

    private ServerLevel handle;

    public CraftWorld(UUID uuid) {
        super(uuid);
    }

    public ServerLevel getHandle() {
        if (this.handle == null && org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
            net.minecraft.server.dedicated.DedicatedServer dedicatedServer = org.patchbukkit.bootstrap.HeadlessPaperServer.get();
            if (dedicatedServer != null) {
                for (ServerLevel level : dedicatedServer.getAllLevels()) {
                    if (level.getWorld() != null && level.getWorld().getUID().equals(this.getUID())) {
                        this.handle = level;
                        break;
                    }
                }
                if (this.handle == null) {
                    this.handle = dedicatedServer.overworld();
                }
            }
        }
        return this.handle;
    }
}
