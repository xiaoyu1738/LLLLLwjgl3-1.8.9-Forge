package com.lllllwjgl3.platform;

import java.util.Locale;

/** Runtime platform state shared by the bytecode hook and the Forge mod. */
public final class Lwjgl3Platform {
    public static final String BACKEND_PROPERTY = "lllllwjgl3.backend";
    public static final String XIM_PROPERTY = "lllllwjgl3.xim";
    public static final String WAYLAND_IME_PROPERTY = "lllllwjgl3.waylandIme";
    private static volatile PlatformInfo.Backend backend = PlatformInfo.Backend.AUTO;
    private static volatile boolean xim = true;
    private static volatile boolean xwaylandIme;

    private Lwjgl3Platform() {
    }

    public static void detect() {
        xim = PlatformInfo.isXimEnabled(System.getProperty(XIM_PROPERTY));
        String imeMode = System.getProperty(WAYLAND_IME_PROPERTY, "xwayland");
        backend = PlatformInfo.chooseBackend(System.getProperty(BACKEND_PROPERTY),
                System.getenv(), xim, imeMode);
        xwaylandIme = PlatformInfo.isWayland(System.getenv()) && backend == PlatformInfo.Backend.X11
                && PlatformInfo.parseBackend(System.getProperty(BACKEND_PROPERTY)) == PlatformInfo.Backend.AUTO;
        System.setProperty("lllllwjgl3.detectedBackend", backend.name().toLowerCase(Locale.ROOT));
        System.setProperty("lllllwjgl3.detectedIme",
                xwaylandIme ? "xwayland-xim" : (xim ? "unicode-callback" : "disabled"));
    }

    public static PlatformInfo.Backend getBackend() {
        return backend;
    }

    public static boolean isXimEnabled() {
        return xim;
    }

    public static boolean isXwaylandIme() {
        return xwaylandIme;
    }
}
