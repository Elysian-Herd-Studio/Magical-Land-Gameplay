package top.csituka.magicaland.gameplay.client.sense;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

public final class EarthSenseTransitionSound {
    private static final EarthSenseTransitionState STATE = new EarthSenseTransitionState();
    private static TransitionInstance current;

    private EarthSenseTransitionSound() {}

    public static void tick(boolean active, long epoch) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) { reset(); return; }
        EarthSenseTransitionState.Action action = STATE.tick(active, epoch);
        if (action == EarthSenseTransitionState.Action.NONE) return;
        stopCurrent(client);
        current = new TransitionInstance(action == EarthSenseTransitionState.Action.ENTER);
        client.getSoundManager().play(current);
    }

    public static void reset() {
        STATE.reset();
        stopCurrent(MinecraftClient.getInstance());
    }

    private static void stopCurrent(MinecraftClient client) {
        if (current == null) return;
        current.cancel();
        client.getSoundManager().stop(current);
        current = null;
    }

    static final class TransitionInstance extends AbstractSoundInstance implements TickableSoundInstance, EarthSenseTransitionSources.CancellableSound {
        private volatile boolean cancelled;

        TransitionInstance(boolean entering) {
            super(new Identifier("magicaland_gameplay", entering ? "sense.enter" : "sense.exit"),
                    SoundCategory.PLAYERS, SoundInstance.createRandom());
            volume = 0.5f;
            pitch = 1;
            relative = true;
            repeat = false;
            attenuationType = AttenuationType.NONE;
        }

        void cancel() {
            cancelled = true;
            volume = 0;
        }

        @Override public boolean magicaland$canStart() { return !cancelled; }
        @Override public boolean canPlay() { return !cancelled; }
        @Override public boolean isDone() { return cancelled; }
        @Override public void tick() { }
    }
}
