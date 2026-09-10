package top.csituka.magicaland.gameplay.client.sense;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EarthSenseAudioHookTest {
    private static int checks;
    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }
    private static ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = ClassLoader.getSystemResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError(name);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }
    private static MethodNode method(ClassNode owner, String name, String desc) {
        return owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow();
    }
    private static int call(MethodNode method, String owner, String name) {
        for (int i = 0; i < method.instructions.size(); i++) {
            if (method.instructions.get(i) instanceof MethodInsnNode m && m.owner.equals(owner) && m.name.equals(name)) return i;
        }
        return -1;
    }
    public static void main(String[] args) throws Exception {
        String base = "net/minecraft/client/sound/";
        ClassNode sound = read(base + "SoundSystem");
        check(sound.fields.stream().anyMatch(f -> f.name.equals("taskQueue") && f.desc.equals("L" + base + "SoundExecutor;")), "sound executor shadow");
        check(sound.fields.stream().anyMatch(f -> f.name.equals("sources") && f.desc.equals("Ljava/util/Map;")), "sources shadow");
        MethodNode play = method(sound, "play", "(L" + base + "SoundInstance;)V");
        int firstRun = call(play, base + "Channel$SourceManager", "run");
        check(firstRun > 0, "pre-play injection exists");
        int sourceMapRead = -1;
        int sourceMapPut = -1;
        for (int i = 0; i < firstRun; i++) {
            if (play.instructions.get(i) instanceof FieldInsnNode field && field.name.equals("sources")) sourceMapRead = i;
            if (sourceMapRead >= 0 && play.instructions.get(i) instanceof MethodInsnNode invoke
                    && invoke.owner.equals("java/util/Map") && invoke.name.equals("put")) sourceMapPut = i;
        }
        check(sourceMapRead >= 0 && sourceMapPut > sourceMapRead && sourceMapPut < firstRun,
                "actual sources map filled before first manager callback");
        check(firstRun < call(play, base + "SoundLoader", "loadStatic"), "filter registration queued before static load/play");
        check(firstRun < call(play, base + "SoundLoader", "loadStreamed"), "filter registration queued before stream load/play");
        MethodNode stopAll = method(sound, "stopAll", "()V");
        check(call(stopAll, base + "SoundExecutor", "restart") < call(stopAll, base + "Channel", "close"), "cleanup must precede executor restart");
        MethodNode stop = method(sound, "stop", "()V");
        check(call(stop, base + "SoundSystem", "stopAll") < call(stop, base + "SoundEngine", "close"), "stopAll cleanup before context destruction");
        MethodNode run = method(read(base + "Channel$SourceManager"), "run", "(Ljava/util/function/Consumer;)V");
        check(call(run, "java/util/concurrent/Executor", "execute") >= 0, "source callback goes through sound executor");
        ClassNode source = read(base + "Source");
        check(source.fields.stream().anyMatch(f -> f.name.equals("pointer") && f.desc.equals("I")
                && (f.access & org.objectweb.asm.Opcodes.ACC_FINAL) != 0), "final source pointer shadow");
        check(call(method(source, "close", "()V"), "org/lwjgl/openal/AL10", "alDeleteSources") >= 0, "close hook precedes real source deletion");
        ClassNode audio = read("top/csituka/magicaland/gameplay/client/sense/EarthSenseAudio");
        for (String name : new String[]{"tick", "reset", "init", "bind"}) {
            for (MethodNode m : audio.methods) if (m.name.equals(name)) {
                for (AbstractInsnNode instruction : m.instructions) if (instruction instanceof MethodInsnNode invoke) {
                    check(!invoke.owner.startsWith("org/lwjgl/openal/"), name + " has no main-thread AL calls");
                }
            }
        }
        ClassNode nativeBackend = read("top/csituka/magicaland/gameplay/client/sense/EarthSenseAudio$OpenAlBackend");
        for (MethodNode m : nativeBackend.methods) for (AbstractInsnNode instruction : m.instructions) {
            if (instruction instanceof MethodInsnNode invoke) {
                check(!invoke.name.equals("alGetSourcei"), "production never queries write-only direct filter");
                check(!invoke.name.equals("alSourcef") && !invoke.name.startsWith("alListener"), "production never changes volume/listener");
            }
        }
        if (args.length > 0) {
            String mixins = Files.readString(Path.of(args[0], "src/client/resources/magicaland.gameplay.client.mixins.json"));
            check(mixins.contains("EarthSenseSoundSystemMixin") && mixins.contains("EarthSenseSourceMixin"), "client mixins registered");
        }
        System.out.println("EarthSenseAudioHookTest: " + checks + " checks passed");
    }
}
