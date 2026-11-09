package net.bitbylogic.module;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleManagerCycleTest {

    private static class A {
    }

    private static class B {
    }

    private static class C {
    }

    @Test
    void moduleDependingOnItselfIsACycle() {
        assertTrue(ModuleManager.hasDependencyCycle(new HashMap<>(), A.class, A.class));
    }

    @Test
    void emptyGraphHasNoCycle() {
        assertFalse(ModuleManager.hasDependencyCycle(new HashMap<>(), A.class, B.class));
    }

    @Test
    void directCycleIsDetected() {
        Map<Class<?>, List<Class<?>>> pending = new HashMap<>();
        pending.put(B.class, List.of(A.class));

        assertTrue(ModuleManager.hasDependencyCycle(pending, B.class, A.class));
    }

    @Test
    void indirectCycleIsDetected() {
        Map<Class<?>, List<Class<?>>> pending = new HashMap<>();
        pending.put(B.class, List.of(C.class));
        pending.put(C.class, List.of(A.class));

        assertTrue(ModuleManager.hasDependencyCycle(pending, B.class, A.class));
    }

    @Test
    void sharedDependencyIsNotACycle() {
        Map<Class<?>, List<Class<?>>> pending = new HashMap<>();
        pending.put(C.class, List.of(A.class, B.class));

        assertFalse(ModuleManager.hasDependencyCycle(pending, A.class, C.class));
        assertFalse(ModuleManager.hasDependencyCycle(pending, B.class, C.class));
    }

    @Test
    void cycleInUnrelatedPartOfGraphDoesNotLoopForever() {
        Map<Class<?>, List<Class<?>>> pending = new HashMap<>();
        pending.put(B.class, List.of(C.class));
        pending.put(C.class, List.of(B.class));

        assertFalse(ModuleManager.hasDependencyCycle(pending, A.class, B.class));
    }

}
