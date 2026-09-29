package com.lllllwjgl3.platform;

import java.util.Locale;
import java.util.Map;

/** Pure platform policy used by the early bootstrap and unit tests. */
public final class PlatformInfo {
    public enum OperatingSystem { WINDOWS, MACOS, LINUX, OTHER }
    public enum Backend { AUTO, WAYLAND, X11 }

    private PlatformInfo() {
    }

    public static OperatingSystem detectOperatingSystem(String osName) {
        String value = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (value.contains("win")) return OperatingSystem.WINDOWS;
        if (value.contains("mac") || value.contains("darwin")) return OperatingSystem.MACOS;
        if (value.contains("linux")) return OperatingSystem.LINUX;
        return OperatingSystem.OTHER;
    }

    public static boolean isWayland(Map<String, String> environment) {
        if (environment == null) return false;
        String session = environment.get("XDG_SESSION_TYPE");
        if (session != null && "wayland".equalsIgnoreCase(session.trim())) return true;
        String display = environment.get("WAYLAND_DISPLAY");
        return display != null && !display.trim().isEmpty();
    }

    public static Backend parseBackend(String value) {
        if (value == null) return Backend.AUTO;
        if ("wayland".equalsIgnoreCase(value.trim())) return Backend.WAYLAND;
        if ("x11".equalsIgnoreCase(value.trim())) return Backend.X11;
        return Backend.AUTO;
    }

    public static Backend chooseBackend(String requested, Map<String, String> environment) {
        return chooseBackend(requested, environment, true, "xwayland");
    }

    /**
     * GLFW 3.x has no Wayland text-input-v3/XIM integration. In AUTO mode we
     * therefore use the XWayland surface when a Wayland session exposes a
     * DISPLAY socket. Fcitx5/IBus then talks to the GLFW X11 backend through
     * XIM and committed Unicode text reaches Keyboard's char callback.
     */
    public static Backend chooseBackend(String requested, Map<String, String> environment,
            boolean ximEnabled, String waylandImeMode) {
        Backend parsed = parseBackend(requested);
        if (parsed != Backend.AUTO) return parsed;
        if (!isWayland(environment)) return Backend.X11;
        if (ximEnabled && shouldUseXwaylandForIme(environment, waylandImeMode)) {
            return Backend.X11;
        }
        return Backend.WAYLAND;
    }

    public static boolean shouldUseXwaylandForIme(Map<String, String> environment,
            String waylandImeMode) {
        if (!isWayland(environment)) return false;
        if ("native".equalsIgnoreCase(waylandImeMode == null ? "" : waylandImeMode.trim())) {
            return false;
        }
        String display = environment.get("DISPLAY");
        return display != null && !display.trim().isEmpty();
    }

    public static boolean isXimEnabled(String value) {
        return value == null || !("0".equals(value.trim()) || "false".equalsIgnoreCase(value.trim()));
    }
}
