package com.lllllwjgl3.forge;

import com.lllllwjgl3.platform.Lwjgl3Platform;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = Lwjgl3Mod.MOD_ID, name = Lwjgl3Mod.NAME, version = Lwjgl3Mod.VERSION,
        acceptedMinecraftVersions = "[1.8.9]", clientSideOnly = true)
public final class Lwjgl3Mod {
    public static final String MOD_ID = "lllllwjgl3";
    public static final String NAME = "LLLLLwjgl3";
    public static final String VERSION = "1.0.0";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        event.getModLog().info("LLLLLwjgl3 active: LWJGL3/GLFW backend={}, IME={}, XWayland-XIM={}",
                Lwjgl3Platform.getBackend(), Lwjgl3Platform.isXimEnabled(),
                Lwjgl3Platform.isXwaylandIme());
    }
}
