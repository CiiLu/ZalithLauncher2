/*
 * 本文件改编自 Mojang 随库官方发行的上游源码
 * https://libraries.minecraft.net/com/mojang/text2speech/1.19.12/text2speech-1.19.12-sources.jar
 */
package com.mojang.text2speech;

import com.movtery.text2speech_bridge.AndroidNarrator;

public interface Narrator {
    default void say(final String msg) {
        say(msg, false);
    }

    default void say(final String msg, final boolean interrupt) {
        say(msg, false, 1.0F);
    }

    void say(final String msg, final boolean interrupt, final float volume);

    void clear();

    default boolean active() {
        return true;
    }

    void destroy();

    Narrator EMPTY = AndroidNarrator.getInstance();

    static Narrator getNarrator() {
        return AndroidNarrator.getInstance();
    }

    static void setJNAPath(final String sep) {
    }

    class InitializeException extends Exception {
        public InitializeException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public InitializeException(final String message) {
            super(message);
        }
    }

    class FatalException extends RuntimeException {
        public FatalException(final String message) {
            super(message);
        }
    }
}
