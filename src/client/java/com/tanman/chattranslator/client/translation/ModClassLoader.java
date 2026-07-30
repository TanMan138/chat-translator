package com.tanman.chattranslator.client.translation;

import java.util.concurrent.Callable;

/**
 * Runs DJL work with our own classloader as the thread context classloader.
 *
 * <p>DJL discovers engines and model zoos with {@link java.util.ServiceLoader}, which
 * reads the <em>context</em> classloader of whatever thread happens to call it. Our
 * inference runs on pool threads whose context loader is the system loader, not the
 * Fabric (Knot) loader that actually holds DJL. When both loaders can see a copy of
 * DJL — which is exactly the case in the dev runtime — {@code ServiceLoader} builds a
 * provider against the wrong copy and {@code ModelZoo.<clinit>} dies with
 * "HfZooProvider not a subtype". That failure is permanent for the JVM: every later
 * load then reports {@code NoClassDefFoundError: Could not initialize class
 * ai.djl.repository.zoo.DefaultModelZoo}.
 */
public final class ModClassLoader {

    private ModClassLoader() {
    }

    public static ClassLoader get() {
        return ModClassLoader.class.getClassLoader();
    }

    /** Runs {@code work} with {@link #get()} installed as the context classloader. */
    public static <T> T call(Callable<T> work) throws Exception {
        Thread current = Thread.currentThread();
        ClassLoader previous = current.getContextClassLoader();
        current.setContextClassLoader(get());
        try {
            return work.call();
        } finally {
            current.setContextClassLoader(previous);
        }
    }
}
