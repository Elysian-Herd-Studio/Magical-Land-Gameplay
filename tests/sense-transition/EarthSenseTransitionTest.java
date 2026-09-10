package top.csituka.magicaland.gameplay.client.sense;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EarthSenseTransitionTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = ClassLoader.getSystemResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError(name);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }
    private static int call(MethodNode method, String owner, String name) {
        for (int i = 0; i < method.instructions.size(); i++)
            if (method.instructions.get(i) instanceof MethodInsnNode m && m.owner.equals(owner) && m.name.equals(name)) return i;
        return -1;
    }
    public static void main(String[] args) throws Exception {
        var state = new EarthSenseTransitionState();
        check(state.tick(false, 1) == EarthSenseTransitionState.Action.NONE, "pending/failed request silent");
        check(state.tick(true, 1) == EarthSenseTransitionState.Action.ENTER, "server confirmation enters");
        for (int i = 0; i < 1000; i++) check(state.tick(true, 1) == EarthSenseTransitionState.Action.NONE, "repeated ticks silent");
        check(state.tick(false, 2) == EarthSenseTransitionState.Action.EXIT, "normal token increment still exits");
        check(state.tick(false, 3) == EarthSenseTransitionState.Action.NONE, "failed re-entry cannot exit again");
        check(state.tick(true, 4) == EarthSenseTransitionState.Action.ENTER, "new entry");
        check(state.tick(true, 5) == EarthSenseTransitionState.Action.ENTER, "same-tick new session replaces sound");
        state.reset();
        check(state.tick(false, 6) == EarthSenseTransitionState.Action.NONE, "disconnect reset silent");
        for (boolean enter : new boolean[]{false, true}) {
            var instance = new EarthSenseTransitionSound.TransitionInstance(enter);
            var resolved = net.minecraft.client.sound.AbstractSoundInstance.class.getDeclaredField("sound");
            resolved.setAccessible(true);
            resolved.set(instance, new net.minecraft.client.sound.Sound("magicaland_gameplay:sense/" + (enter ? "enter" : "exit"),
                    random -> 1, random -> 1, 1, net.minecraft.client.sound.Sound.RegistrationType.FILE, false, true, 16));
            check(instance.getId().toString().equals("magicaland_gameplay:sense." + (enter ? "enter" : "exit")), "asset event");
            check(instance.getCategory() == SoundCategory.PLAYERS, "player category respects existing slider");
            check(instance.isRelative() && instance.getAttenuationType() == SoundInstance.AttenuationType.NONE, "local, no world attenuation/filter");
            check(instance.getVolume() == 0.5f && instance.getPitch() == 1 && !instance.isRepeatable(), "quiet one shot, no speed mutation");
            Object source = new Object();
            EarthSenseTransitionSources.register(source, instance);
            check(EarthSenseTransitionSources.canPlay(source), "fresh decode may start");
            instance.cancel();
            check(instance.isDone() && !instance.canPlay() && instance.getVolume() == 0, "cancel silences existing tickable");
            check(!EarthSenseTransitionSources.canPlay(source), "late decode cannot audibly restart");
            check(EarthSenseTransitionSources.canPlay(new Object()), "unrelated world sounds unchanged");
            EarthSenseTransitionSources.closed(source);
            check(EarthSenseTransitionSources.canPlay(source), "source close clears association");
        }
        var instance = new EarthSenseTransitionSound.TransitionInstance(true);
        Object asynchronous = new Object();
        EarthSenseTransitionSources.register(asynchronous, instance);
        AtomicBoolean sawCancelled = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (System.nanoTime() < deadline) if (!EarthSenseTransitionSources.canPlay(asynchronous)) {
                sawCancelled.set(true); return;
            }
        });
        worker.start(); instance.cancel(); worker.join(2500);
        check(sawCancelled.get(), "sound thread observes volatile cancellation");
        EarthSenseTransitionSources.closed(asynchronous);
        String base = "net/minecraft/client/sound/";
        ClassNode sound = read(base + "SoundSystem");
        MethodNode play = sound.methods.stream().filter(m -> m.name.equals("play") && m.desc.equals("(L" + base + "SoundInstance;)V")).findFirst().orElseThrow();
        int run = call(play, base + "Channel$SourceManager", "run");
        int sourceMap = -1, put = -1;
        for (int i = 0; i < run; i++) {
            if (play.instructions.get(i) instanceof FieldInsnNode f && f.name.equals("sources")) sourceMap = i;
            if (sourceMap >= 0 && play.instructions.get(i) instanceof MethodInsnNode m && m.owner.equals("java/util/Map") && m.name.equals("put")) put = i;
        }
        check(sourceMap >= 0 && put > sourceMap && put < run, "actual sources registration before hook");
        check(run < call(play, base + "SoundLoader", "loadStatic"), "guard association queued before asynchronous decode");
        boolean staticPlayback = false, streamingPlayback = false;
        for (MethodNode m : sound.methods) if (call(m, base + "Source", "play") >= 0) {
            staticPlayback |= m.desc.contains("StaticSound;");
            streamingPlayback |= m.desc.contains("AudioStream;");
        }
        check(staticPlayback && streamingPlayback, "Source.play covers both actual decode paths");
        ClassNode implementation = read("top/csituka/magicaland/gameplay/client/sense/EarthSenseTransitionSound$TransitionInstance");
        check(implementation.fields.stream().anyMatch(f -> f.name.equals("cancelled") && (f.access & Opcodes.ACC_VOLATILE) != 0), "volatile field retained");
        if (args.length > 0) {
            String mixins = Files.readString(Path.of(args[0], "src/client/resources/magicaland.gameplay.client.mixins.json"));
            check(mixins.contains("EarthSenseTransitionSoundSystemMixin") && mixins.contains("EarthSenseTransitionSourceMixin"), "both mixins registered");
        }
        System.out.println("EarthSenseTransitionTest: " + checks + " checks passed");
    }
}
