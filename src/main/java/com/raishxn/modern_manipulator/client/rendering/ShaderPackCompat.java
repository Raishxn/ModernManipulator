package com.raishxn.modern_manipulator.client.rendering;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Detects an active Oculus/Iris shader pack. Our core shaders drawn during level rendering end up in the pack's
 * buffers and are wiped out by its composite passes, so with a pack the preview is drawn after the level instead.
 */
public final class ShaderPackCompat {

    private static final MethodHandle IS_IN_USE = lookup();
    private static boolean failed;

    private ShaderPackCompat() {}

    private static MethodHandle lookup() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup()
                    .findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class))
                    .bindTo(instance);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isShaderPackInUse() {
        if (IS_IN_USE == null || failed) return false;
        try {
            return (boolean) IS_IN_USE.invokeExact();
        } catch (Throwable t) {
            failed = true;
            return false;
        }
    }
}
