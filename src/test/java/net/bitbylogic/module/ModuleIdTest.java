package net.bitbylogic.module;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleIdTest {

    @Test
    void ordinaryIdsAreAccepted() {
        assertTrue(ModuleManager.isValidModuleId("economy"));
        assertTrue(ModuleManager.isValidModuleId("Player Data"));
        assertTrue(ModuleManager.isValidModuleId("shop_v2"));
        assertTrue(ModuleManager.isValidModuleId("my-module.core"));
    }

    @Test
    void pathTraversalIsRejected() {
        assertFalse(ModuleManager.isValidModuleId("../x"));
        assertFalse(ModuleManager.isValidModuleId("a/b"));
        assertFalse(ModuleManager.isValidModuleId("a\\b"));
        assertFalse(ModuleManager.isValidModuleId("a..b"));
        assertFalse(ModuleManager.isValidModuleId("."));
    }

    @Test
    void blankAndOddIdsAreRejected() {
        assertFalse(ModuleManager.isValidModuleId(""));
        assertFalse(ModuleManager.isValidModuleId(" "));
        assertFalse(ModuleManager.isValidModuleId("-leading"));
        assertFalse(ModuleManager.isValidModuleId("c:drive"));
    }

}
