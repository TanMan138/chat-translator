package com.tanman.chattranslator.client.translation;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModClassLoaderTest {

    @Test
    void installsTheModClassLoaderForTheDurationOfTheCall() throws Exception {
        Thread current = Thread.currentThread();
        ClassLoader outside = new URLClassLoader(new java.net.URL[0]);
        current.setContextClassLoader(outside);
        try {
            ClassLoader seen = ModClassLoader.call(current::getContextClassLoader);

            assertSame(ModClassLoader.get(), seen);
            assertSame(outside, current.getContextClassLoader());
        } finally {
            current.setContextClassLoader(null);
        }
    }

    @Test
    void restoresTheContextClassLoaderWhenTheCallThrows() {
        Thread current = Thread.currentThread();
        ClassLoader outside = new URLClassLoader(new java.net.URL[0]);
        current.setContextClassLoader(outside);
        try {
            Exception thrown = assertThrows(Exception.class, () -> ModClassLoader.call(() -> {
                throw new IllegalStateException("load failed");
            }));

            assertEquals("load failed", thrown.getMessage());
            assertSame(outside, current.getContextClassLoader());
        } finally {
            current.setContextClassLoader(null);
        }
    }

    @Test
    void returnsTheValueFromTheWork() throws Exception {
        assertEquals("done", ModClassLoader.call(() -> "done"));
    }
}
