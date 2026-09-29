package com.lllllwjgl3.boot;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.List;

import net.minecraft.launchwrapper.LaunchClassLoader;

/** Makes the coremod's LWJGL3 classes win over launcher-provided LWJGL2 jars. */
final class Lwjgl3Classpath {
    private Lwjgl3Classpath() {
    }

    static URL prioritizeCoremod(LaunchClassLoader launchLoader, Class<?> anchor) {
        CodeSource codeSource = anchor.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new IllegalStateException("Cannot locate the LWJGL3 coremod jar");
        }
        URL coremod = containerLocation(codeSource.getLocation());
        prioritizeSource(launchLoader, coremod);
        return coremod;
    }

    static void prioritizeSource(LaunchClassLoader launchLoader, URL coremod) {
        // LaunchClassLoader's public source list is bookkeeping only. Its
        // inherited URLClassPath has a separate loader list that performs the
        // actual resource lookup and must be reordered as well.
        if (!moveUrlToFront(launchLoader.getSources(), coremod)) {
            throw new IllegalStateException("The LWJGL3 coremod jar is not a LaunchClassLoader source: " + coremod);
        }

        try {
            Field ucpField = URLClassLoader.class.getDeclaredField("ucp");
            ucpField.setAccessible(true);
            Object urlClassPath = ucpField.get(launchLoader);

            Method disableCaches = findMethod(urlClassPath.getClass(), "disableAllLookupCaches");
            disableCaches.setAccessible(true);
            disableCaches.invoke(null);

            Field pathField = findField(urlClassPath.getClass(), "path");
            pathField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<URL> path = (List<URL>) pathField.get(urlClassPath);
            moveUrlToFront(path, coremod);

            Field loadersField = findField(urlClassPath.getClass(), "loaders");
            loadersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Object> loaders = (List<Object>) loadersField.get(urlClassPath);
            int loaderIndex = findLoader(loaders, coremod);
            if (loaderIndex < 0) {
                throw new IllegalStateException("The LWJGL3 coremod URLClassPath loader was not initialized: "
                        + coremod);
            }
            if (loaderIndex > 0) {
                Object coremodLoader = loaders.remove(loaderIndex);
                loaders.add(0, coremodLoader);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to prioritize the LWJGL3 coremod over vanilla LWJGL2; "
                    + "Minecraft 1.8.9 must run on Java 8", e);
        }
    }

    static boolean sameLocation(URL left, URL right) {
        if (left == null || right == null) return left == right;
        try {
            URI leftUri = containerLocation(left).toURI().normalize();
            URI rightUri = containerLocation(right).toURI().normalize();
            return leftUri.equals(rightUri);
        } catch (Exception ignored) {
            return left.toExternalForm().equals(right.toExternalForm());
        }
    }

    private static URL containerLocation(URL location) {
        if (!"jar".equalsIgnoreCase(location.getProtocol())) return location;
        try {
            return ((JarURLConnection) location.openConnection()).getJarFileURL();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot resolve the outer coremod jar from " + location, e);
        }
    }

    private static boolean moveUrlToFront(List<URL> urls, URL target) {
        for (int i = 0; i < urls.size(); i++) {
            if (!sameLocation(urls.get(i), target)) continue;
            if (i > 0) {
                URL value = urls.remove(i);
                urls.add(0, value);
            }
            return true;
        }
        return false;
    }

    private static int findLoader(List<Object> loaders, URL target)
            throws ReflectiveOperationException {
        for (int i = 0; i < loaders.size(); i++) {
            URL base = getBaseUrl(loaders.get(i));
            if (sameLocation(base, target)) return i;
        }
        return -1;
    }

    private static URL getBaseUrl(Object loader) throws ReflectiveOperationException {
        Method method = findMethod(loader.getClass(), "getBaseURL");
        method.setAccessible(true);
        try {
            return (URL) method.invoke(loader);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("Unable to inspect a LaunchClassLoader source", cause);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method findMethod(Class<?> type, String name) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }
}
