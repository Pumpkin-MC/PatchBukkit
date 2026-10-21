package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.material.MaterialData;
import org.patchbukkit.testplugin.DynamicTestProvider;
import org.patchbukkit.testplugin.TestCategory;
import org.patchbukkit.testplugin.TestExpectation;
import org.patchbukkit.testplugin.TestResult;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public final class LegacyMaterialTests implements DynamicTestProvider {

    @Override
    public List<TestResult> runDynamicTests() {
        List<TestResult> results = new ArrayList<>();

        for (Material mat : Material.values()) {
            if (!mat.isLegacy()) continue;
            if (mat == Material.LEGACY_AIR || mat == Material.LEGACY_SKULL) continue;

            String name = mat.name();
            try {
                Material result = Bukkit.getUnsafe().fromLegacy(new MaterialData(mat, (byte) 0));
                if (result != null && result != Material.AIR) {
                    results.add(new TestResult(
                        name + " -> " + result.name(),
                        TestCategory.LEGACY_MATERIALS,
                        TestExpectation.SHOULD_WORK,
                        true,
                        null
                    ));
                } else {
                    results.add(new TestResult(
                        name + " -> " + result,
                        TestCategory.LEGACY_MATERIALS,
                        TestExpectation.SHOULD_WORK,
                        false,
                        "Converted to " + result + " (expected non-AIR)"
                    ));
                }
            } catch (Exception e) {
                results.add(new TestResult(
                    name + " -> ERROR",
                    TestCategory.LEGACY_MATERIALS,
                    TestExpectation.SHOULD_WORK,
                    false,
                    e.getClass().getSimpleName() + ": " + e.getMessage()
                ));
            }
        }

        return results;
    }
}
