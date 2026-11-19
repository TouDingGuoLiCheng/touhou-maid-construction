package com.touhoumaid.construction.chat;

/** Tick window for construction chat bubbles. */
public final class ChatBubbleCooldown {
    public static final long NEVER = Long.MIN_VALUE;

    private ChatBubbleCooldown() {
    }

    public static boolean isOnCooldown(long now, long lastTick, long cooldownTicks) {
        if (lastTick == NEVER) {
            return false;
        }
        return now - lastTick < cooldownTicks;
    }
}
