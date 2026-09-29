package com.lllllwjgl3.boot;

import org.lwjglx.openal.*;
import org.lwjgl.BufferUtils;
import java.nio.ShortBuffer;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit OpenAL lifecycle regression, using silent samples only. */
public final class AudioCompatibilitySmoke {
    public static void main(String[] args) throws Exception {
        String devices = ALC10.alcGetString(null, ALC10.ALC_DEFAULT_DEVICE_SPECIFIER);
        if (devices == null) throw new AssertionError("Default device enumeration failed");
        verifyIndependentContexts();
        verifyDeviceCloseWithLiveContext();
        verifyFailedInitialization();
        for (int cycle = 0; cycle < 3; cycle++) {
            AL.create();
            if (org.lwjgl.openal.ALC.getCapabilities().ALC_EXT_thread_local_context
                    && org.lwjgl.openal.EXTThreadLocalContext.alcGetThreadContext() != 0)
                throw new AssertionError("Legacy create unexpectedly installed a thread-local context");
            ALCdevice device = AL.getDevice();
            ALCcontext context = AL.getContext();
            try {
                if (!AL.isCreated() || context == null) throw new AssertionError("Context missing");
                if (!context.equals(ALC10.alcGetCurrentContext())) throw new AssertionError("Current context mismatch");
                AtomicReference<Throwable> failed = new AtomicReference<Throwable>();
                Thread audioThread = new Thread(() -> {
                    try {
                        if (!context.equals(ALC10.alcGetCurrentContext())) throw new AssertionError("Process context not visible on sound thread");
                        int buffer = AL10.alGenBuffers(), source = AL10.alGenSources();
                        try {
                            ShortBuffer silence = BufferUtils.createShortBuffer(4410);
                            AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, silence, 44100);
                            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
                            AL10.alSourcePlay(source);
                            if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING)
                                throw new AssertionError("Silent source not playing");
                            AL10.alSourceStop(source);
                            if (AL10.alGetError() != AL10.AL_NO_ERROR) throw new AssertionError("OpenAL source error");
                        } finally { AL10.alDeleteSources(source); AL10.alDeleteBuffers(buffer); }
                    } catch (Throwable error) { failed.set(error); }
                }, "OpenAL regression worker");
                audioThread.start(); audioThread.join(5000);
                if (audioThread.isAlive()) throw new AssertionError("Audio worker did not finish");
                if (failed.get() != null) throw new AssertionError("Cross-thread audio failed", failed.get());
            } finally { AL.destroy(); }
            if (AL.isCreated() || device.isValid() || context.isValid()) throw new AssertionError("Stale OpenAL state");
            if (ALC10.alcGetCurrentContext() != null) throw new AssertionError("Context still current");
            assertRegistrySize("contexts", 0);
            assertRegistrySize("devices", 0);
            AL.destroy();
        }
        System.out.println("PASS: default-device query, silent cross-thread playback, destroy/recreate x3");
    }

    private static void verifyIndependentContexts() throws Exception {
        AL.create();
        ALCcontext first = AL.getContext();
        ALCcontext second = null;
        try {
            AL10.alListenerf(AL10.AL_GAIN, 0.25f);
            second = ALC10.alcCreateContext(AL.getDevice(), null);
            if (second == null) throw new AssertionError("Second context creation failed");
            if (ALC10.alcGetCurrentContext() != first || AL10.alGetListenerf(AL10.AL_GAIN) != 0.25f)
                throw new AssertionError("Creating a second context changed the active audio context");
            if (ALC10.alcMakeContextCurrent(second) != ALC10.ALC_TRUE)
                throw new AssertionError("Cannot select second context");
            AL10.alListenerf(AL10.AL_GAIN, 0.75f);
            ALC10.alcMakeContextCurrent(first);
            if (AL10.alGetListenerf(AL10.AL_GAIN) != 0.25f)
                throw new AssertionError("Context switch lost listener state");
            ALC10.alcDestroyContext(second);
            second = null;
            assertRegistrySize("contexts", 1);
        } finally {
            ALC10.alcMakeContextCurrent(first);
            if (second != null && second.isValid()) ALC10.alcDestroyContext(second);
            AL.destroy();
        }
        System.out.println("PASS: independent context creation, switching and registry cleanup");
    }

    private static void verifyDeviceCloseWithLiveContext() throws Exception {
        AL.create();
        ALCdevice device = AL.getDevice();
        ALCcontext context = AL.getContext();
        try {
            boolean closed = ALC10.alcCloseDevice(device);
            if (closed) {
                if (device.isValid() || context.isValid()) throw new AssertionError("Closed device left valid handles");
                assertRegistrySize("contexts", 0);
                assertRegistrySize("devices", 0);
            } else {
                if (!device.isValid() || !context.isValid() || ALC10.alcGetContextsDevice(context) != device)
                    throw new AssertionError("Failed close invalidated live handles");
                ALC10.alcGetError(device);
            }
        } finally { AL.destroy(); }
        System.out.println("PASS: device close honors native success/failure and cleans owned contexts");
    }

    private static void verifyFailedInitialization() throws Exception {
        try {
            AL.create("LLLLLwjgl3 nonexistent test device", 44100, 60, false);
            throw new AssertionError("Nonexistent device unexpectedly opened");
        } catch (org.lwjglx.LWJGLException expected) {
            if (AL.isCreated() || AL.getDevice() != null || AL.getContext() != null)
                throw new AssertionError("Failed initialization left partial state", expected);
        }
        assertRegistrySize("contexts", 0);
        assertRegistrySize("devices", 0);
        System.out.println("PASS: failed initialization leaves no partial state");
    }

    private static void assertRegistrySize(String name, int size) throws Exception {
        java.lang.reflect.Field registry = ALC10.class.getDeclaredField(name);
        registry.setAccessible(true);
        if (((java.util.Map<?, ?>) registry.get(null)).size() != size)
            throw new AssertionError("Unexpected " + name + " registry size");
    }
}
