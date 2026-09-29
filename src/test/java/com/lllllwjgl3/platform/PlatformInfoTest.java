package com.lllllwjgl3.platform;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlatformInfoTest {
    @Test
    public void detectsWaylandFromSessionTypeOrSocket() {
        Map<String, String> session = new HashMap<String, String>();
        session.put("XDG_SESSION_TYPE", "Wayland");
        assertTrue(PlatformInfo.isWayland(session));

        Map<String, String> socket = new HashMap<String, String>();
        socket.put("WAYLAND_DISPLAY", "wayland-0");
        assertTrue(PlatformInfo.isWayland(socket));
    }

    @Test
    public void doesNotTreatX11AsWayland() {
        Map<String, String> env = new HashMap<String, String>();
        env.put("XDG_SESSION_TYPE", "x11");
        env.put("WAYLAND_DISPLAY", " ");
        assertFalse(PlatformInfo.isWayland(env));
        assertFalse(PlatformInfo.isWayland(Collections.<String, String>emptyMap()));
        assertFalse(PlatformInfo.isWayland(null));
    }

    @Test
    public void explicitBackendWinsOverEnvironment() {
        Map<String, String> env = new HashMap<String, String>();
        env.put("WAYLAND_DISPLAY", "wayland-0");
        env.put("DISPLAY", ":0");
        assertEquals(PlatformInfo.Backend.X11, PlatformInfo.chooseBackend("x11", env));
        assertEquals(PlatformInfo.Backend.X11, PlatformInfo.chooseBackend(null, env));
        assertEquals(PlatformInfo.Backend.WAYLAND,
                PlatformInfo.chooseBackend(null, env, true, "native"));
    }

    @Test
    public void waylandImeFallsBackToXwaylandWhenDisplayIsAvailable() {
        Map<String, String> env = new HashMap<String, String>();
        env.put("XDG_SESSION_TYPE", "wayland");
        env.put("WAYLAND_DISPLAY", "wayland-0");
        env.put("DISPLAY", ":0");
        assertTrue(PlatformInfo.shouldUseXwaylandForIme(env, "xwayland"));
        assertFalse(PlatformInfo.shouldUseXwaylandForIme(env, "native"));
        env.remove("DISPLAY");
        assertFalse(PlatformInfo.shouldUseXwaylandForIme(env, "xwayland"));
    }

    @Test
    public void ximIsEnabledByDefaultAndCanBeDisabled() {
        assertTrue(PlatformInfo.isXimEnabled(null));
        assertTrue(PlatformInfo.isXimEnabled("true"));
        assertFalse(PlatformInfo.isXimEnabled("0"));
        assertFalse(PlatformInfo.isXimEnabled("false"));
    }
}
