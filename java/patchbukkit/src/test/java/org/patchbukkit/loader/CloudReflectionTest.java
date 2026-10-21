package org.patchbukkit.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.patchbukkit.bootstrap.HeadlessPaperServer;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class CloudReflectionTest {

    @TempDir
    static Path root;

    @Test
    public void testGrimAcCloudReflection() throws Exception {
        HeadlessPaperServer.boot(root);

        File grimFile = new File("build/test-plugins/GrimAC.jar");
        if (!grimFile.exists()) {
            grimFile = new File("java/patchbukkit/build/test-plugins/GrimAC.jar");
        }
        if (!grimFile.exists()) return;

        PatchBukkitPluginClassLoader loader = new PatchBukkitPluginClassLoader(
                getClass().getClassLoader(),
                grimFile
        );

        try {
            Class<?> vcw = getClass().getClassLoader().loadClass("org.bukkit.craftbukkit.command.VanillaCommandWrapper");
            System.out.println("VanillaCommandWrapper in AppClassLoader: " + vcw);
        } catch (Throwable t) {
            System.out.println("VanillaCommandWrapper NOT in AppClassLoader: " + t);
        }

        try {
            Class<?> remappedTest = loader.loadClass("org.bukkit.craftbukkitcommand.VanillaCommandWrapper");
            System.out.println("loader.loadClass with dots: " + remappedTest);
        } catch (Throwable t) {
            System.out.println("loader.loadClass with dots FAILED: " + t);
        }
        try {
            Class<?> remappedSlashTest = loader.loadClass("org/bukkit/craftbukkitcommand/VanillaCommandWrapper");
            System.out.println("loader.loadClass with slashes: " + remappedSlashTest);
        } catch (Throwable t) {
            System.out.println("loader.loadClass with slashes FAILED: " + t);
        }

        try {
            Class<?> forNameDotTest = Class.forName("org.bukkit.craftbukkitcommand.VanillaCommandWrapper", true, loader);
            System.out.println("Class.forName with dots: " + forNameDotTest);
        } catch (Throwable t) {
            System.out.println("Class.forName with dots FAILED: " + t);
        }

        Class<?> cbrClass = loader.loadClass("ac.grim.grimac.shaded.incendo.cloud.bukkit.internal.CraftBukkitReflection");
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);

        Field cbPkgField2 = cbrClass.getDeclaredField("CB_PKG_VERSION");
        cbPkgField2.setAccessible(true);
        unsafe.putObject(unsafe.staticFieldBase(cbPkgField2), unsafe.staticFieldOffset(cbPkgField2), ".");
        System.out.println("After unsafe putObject, CB_PKG_VERSION: '" + cbPkgField2.get(null) + "'");
        assertNotNull(cbrClass);

        Field cbPkgField = cbrClass.getDeclaredField("CB_PKG_VERSION");
        cbPkgField.setAccessible(true);
        Object version = cbPkgField.get(null);
        System.out.println("CB_PKG_VERSION: '" + version + "'");

        for (Field f : cbrClass.getDeclaredFields()) {
            f.setAccessible(true);
            try {
                System.out.println("Field " + f.getName() + " = " + f.get(null));
            } catch (Exception ignored) {}
        }
        Method needObcMethod = cbrClass.getDeclaredMethod("needOBCClass", String.class);
        needObcMethod.setAccessible(true);

        try {
            Object res = needObcMethod.invoke(null, "command.VanillaCommandWrapper");
            System.out.println("Found OBC Class: " + res);
            assertNotNull(res);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
