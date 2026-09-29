package com.lllllwjgl3.boot;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjglx.input.Keyboard;
import org.lwjglx.opengl.Display;
import org.lwjglx.opengl.DisplayMode;

/** Feeds installed native callbacks and checks the public legacy input API. */
public final class KeyboardCompatibilitySmoke {
    public static void main(String[] args) throws Exception {
        GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_X11);
        Display.setDisplayMode(new DisplayMode(320, 240));
        Display.setTitle("Keyboard compatibility regression");
        try {
            Display.class.getMethod("create").invoke(null);
            Keyboard.poll();
            while (Keyboard.next()) { }
            GLFWKeyCallback keys = keyCallback();
            GLFWCharCallback chars = charCallback();
            checkLegacyKeyMapping(keys);
            checkMovementChords(keys);
            keys.invoke(Display.getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT, 0, GLFW.GLFW_PRESS, GLFW.GLFW_MOD_SHIFT);
            keys.invoke(Display.getHandle(), GLFW.GLFW_KEY_W, 0, GLFW.GLFW_PRESS, GLFW.GLFW_MOD_SHIFT);
            // GLFW normally delivers the key callback before the char callback;
            // the facade must merge the character onto that key event.
            chars.invoke(Display.getHandle(), 'W');
            Keyboard.poll();
            if (!Keyboard.next() || Keyboard.getEventKey() != Keyboard.KEY_LSHIFT
                    || !Keyboard.getEventKeyState() || Keyboard.isRepeatEvent())
                throw new AssertionError("Shift press was not preserved");
            expectKeyCharacter(Keyboard.KEY_W, true, 'W');
            if (Keyboard.next()) throw new AssertionError("Uppercase text leaked as a separate key event");
            keys.invoke(Display.getHandle(), GLFW.GLFW_KEY_W, 0, GLFW.GLFW_RELEASE, GLFW.GLFW_MOD_SHIFT);
            keys.invoke(Display.getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT, 0, GLFW.GLFW_RELEASE, 0);
            Keyboard.poll();
            if (!Keyboard.next() || Keyboard.getEventKey() != Keyboard.KEY_W || Keyboard.getEventKeyState())
                throw new AssertionError("W release was not preserved");
            if (!Keyboard.next() || Keyboard.getEventKey() != Keyboard.KEY_LSHIFT || Keyboard.getEventKeyState())
                throw new AssertionError("Shift release was not preserved");
            if (Keyboard.next()) throw new AssertionError("Duplicate release event");
            keys.invoke(Display.getHandle(), GLFW.GLFW_KEY_W, 0, GLFW.GLFW_PRESS, 0);
            Keyboard.poll();
            if (!Keyboard.isKeyDown(Keyboard.KEY_W)) throw new AssertionError("W press lost");
            expectKey(Keyboard.KEY_W, true, false);
            Keyboard.enableRepeatEvents(false);
            keys.invoke(Display.getHandle(), GLFW.GLFW_KEY_W, 0, GLFW.GLFW_REPEAT, 0);
            Keyboard.poll();
            if (Keyboard.next()) throw new AssertionError("Disabled repeat delivered");
            Keyboard.enableRepeatEvents(true);
            keys.invoke(Display.getHandle(), GLFW.GLFW_KEY_W, 0, GLFW.GLFW_REPEAT, 0);
            Keyboard.poll();
            expectKey(Keyboard.KEY_W, true, true);
            keys.invoke(Display.getHandle(), GLFW.GLFW_KEY_W, 0, GLFW.GLFW_RELEASE, 0);
            Keyboard.poll();
            expectKey(Keyboard.KEY_W, false, false);
            if (Keyboard.isKeyDown(Keyboard.KEY_W)) throw new AssertionError("W stuck after release");
            // Uppercase text is the regression case: its character values are
            // 87/88, which must never become F11/F12 key events.
            String text = "aWX中文\uD83D\uDE00";
            text.codePoints().forEach(cp -> chars.invoke(Display.getHandle(), cp));
            Keyboard.poll();
            StringBuilder actual = new StringBuilder();
            while (Keyboard.next()) {
                if (Keyboard.getEventKey() != Keyboard.KEY_NONE)
                    throw new AssertionError("Text commit has invalid key: " + Keyboard.getEventKey());
                if (!Keyboard.getEventKeyState()) throw new AssertionError("Text commit not delivered as press");
                actual.append(Keyboard.getEventCharacter());
            }
            if (!text.equals(actual.toString())) throw new AssertionError("Unicode commit corrupted: " + actual);
            keys.invoke(Display.getHandle(), GLFW.GLFW_KEY_W, 0, GLFW.GLFW_PRESS, 0);
            // Destroy before polling, so both a held key and an unread event remain.
            Keyboard.destroy();
            Keyboard.create();
            Keyboard.poll();
            if (Keyboard.isKeyDown(Keyboard.KEY_W) || Keyboard.next())
                throw new AssertionError("Keyboard recreation retained held keys or events");
            System.out.println("PASS: Shift+W and modifier isolation; press/release/repeat; Chinese and supplementary Unicode; destroy/recreate clears state");
        } finally { Display.destroy(); }
    }

    private static void checkMovementChords(GLFWKeyCallback keys) {
        int[][] modifiers = {
                {GLFW.GLFW_KEY_LEFT_SHIFT, Keyboard.KEY_LSHIFT, GLFW.GLFW_MOD_SHIFT},
                {GLFW.GLFW_KEY_RIGHT_SHIFT, Keyboard.KEY_RSHIFT, GLFW.GLFW_MOD_SHIFT},
                {GLFW.GLFW_KEY_LEFT_SUPER, Keyboard.KEY_LMETA, GLFW.GLFW_MOD_SUPER},
                {GLFW.GLFW_KEY_RIGHT_SUPER, Keyboard.KEY_RMETA, GLFW.GLFW_MOD_SUPER}
        };
        for (int[] modifier : modifiers) {
            // Exercise both release orders: movement first and modifier first.
            for (boolean releaseModifierFirst : new boolean[] {false, true}) {
                keys.invoke(Display.getHandle(), modifier[0], 0, GLFW.GLFW_PRESS, modifier[2]);
                Keyboard.poll();
                expectKey(modifier[1], true, false);
                expectHeld(modifier[1]);
                keys.invoke(Display.getHandle(), GLFW.GLFW_KEY_W, 0, GLFW.GLFW_PRESS, modifier[2]);
                Keyboard.poll();
                expectKey(Keyboard.KEY_W, true, false);
                expectHeld(modifier[1], Keyboard.KEY_W);
                Keyboard.enableRepeatEvents(true);
                keys.invoke(Display.getHandle(), GLFW.GLFW_KEY_W, 0, GLFW.GLFW_REPEAT, modifier[2]);
                Keyboard.poll();
                expectKey(Keyboard.KEY_W, true, true);
                expectHeld(modifier[1], Keyboard.KEY_W);
                int firstGlfw = releaseModifierFirst ? modifier[0] : GLFW.GLFW_KEY_W;
                int firstLegacy = releaseModifierFirst ? modifier[1] : Keyboard.KEY_W;
                int secondGlfw = releaseModifierFirst ? GLFW.GLFW_KEY_W : modifier[0];
                int secondLegacy = releaseModifierFirst ? Keyboard.KEY_W : modifier[1];
                keys.invoke(Display.getHandle(), firstGlfw, 0, GLFW.GLFW_RELEASE,
                        releaseModifierFirst ? 0 : modifier[2]);
                Keyboard.poll();
                expectKey(firstLegacy, false, false);
                expectHeld(secondLegacy);
                keys.invoke(Display.getHandle(), secondGlfw, 0, GLFW.GLFW_RELEASE, 0);
                Keyboard.poll();
                expectKey(secondLegacy, false, false);
                expectHeld();
            }
        }
        Keyboard.enableRepeatEvents(false);
    }

    private static void checkLegacyKeyMapping(GLFWKeyCallback keys) {
        int[][] mapping = {
                {GLFW.GLFW_KEY_W, Keyboard.KEY_W}, {GLFW.GLFW_KEY_X, Keyboard.KEY_X},
                {GLFW.GLFW_KEY_F11, Keyboard.KEY_F11}, {GLFW.GLFW_KEY_F12, Keyboard.KEY_F12}
        };
        for (int[] pair : mapping) {
            keys.invoke(Display.getHandle(), pair[0], 0, GLFW.GLFW_PRESS, 0);
            Keyboard.poll();
            expectKey(pair[1], true, false);
            keys.invoke(Display.getHandle(), pair[0], 0, GLFW.GLFW_RELEASE, 0);
            Keyboard.poll();
            expectKey(pair[1], false, false);
        }
    }

    private static void expectHeld(int... expected) {
        for (int key = 0; key < Keyboard.KEYBOARD_SIZE; key++) {
            boolean down = false;
            for (int held : expected) if (held == key) down = true;
            if (Keyboard.isKeyDown(key) != down)
                throw new AssertionError("Unexpected held state for key " + key);
        }
    }

    private static GLFWKeyCallback keyCallback() {
        GLFWKeyCallback callback = GLFW.glfwSetKeyCallback(Display.getHandle(), null);
        if (callback == null) throw new AssertionError("Missing key callback");
        GLFW.glfwSetKeyCallback(Display.getHandle(), callback);
        return callback;
    }

    private static GLFWCharCallback charCallback() {
        GLFWCharCallback callback = GLFW.glfwSetCharCallback(Display.getHandle(), null);
        if (callback == null) throw new AssertionError("Missing char callback");
        GLFW.glfwSetCharCallback(Display.getHandle(), callback);
        return callback;
    }

    private static void expectKey(int key, boolean down, boolean repeat) {
        if (!Keyboard.next() || Keyboard.getEventKey() != key || Keyboard.getEventKeyState() != down
                || Keyboard.isRepeatEvent() != repeat || Keyboard.getEventNanoseconds() <= 0)
            throw new AssertionError("Incorrect key event");
        if (Keyboard.next()) throw new AssertionError("Duplicate key event");
    }

    private static void expectKeyCharacter(int key, boolean down, char character) {
        if (!Keyboard.next() || Keyboard.getEventKey() != key || Keyboard.getEventKeyState() != down
                || Keyboard.getEventCharacter() != character || Keyboard.isRepeatEvent())
            throw new AssertionError("Key character was not merged");
    }
}
