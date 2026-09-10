package top.csituka.magicaland.gameplay.client.sense;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.PacketByteBuf;
import top.csituka.magicaland.gameplay.sense.EarthSenseProtocol;
import static top.csituka.magicaland.gameplay.client.sense.EarthSenseTransitionState.Action.*;

public final class EarthSenseTransitionLifecycleTest {
    private static final String OVERWORLD = "minecraft:overworld", NETHER = "minecraft:the_nether";
    private static int checks;

    public static void main(String[] args) {
        var test = new Harness();
        test.tick(NONE, "initial inactive state is silent");
        long pending = test.session.begin(true);
        test.tick(NONE, "request alone cannot play entry");
        for (int i = 0; i < 5; i++) test.tick(NONE, "pending end ticks stay silent");
        test.receive(pending, 0, OVERWORLD, false, false, "stance", NONE, true);
        test.tick(NONE, "denied pending request cannot play an exit");
        test.receive(pending, 1, OVERWORLD, true, true, "", NONE, false);

        pending = test.session.begin(true);
        test.tick(NONE, "retry waits for its own confirmation");
        test.receive(pending - 1, 9, OVERWORLD, true, true, "", NONE, false);
        test.receive(pending, 0, NETHER, true, true, "", NONE, false);
        test.receive(pending, 0, OVERWORLD, true, true, "", ENTER, true);
        test.tick(NONE, "end tick after receive cannot replay entry");
        test.receive(pending, 0, OVERWORLD, true, true, "", NONE, false);
        test.receive(pending, 1, OVERWORLD, true, true, "", NONE, true);
        test.receive(pending, 2, OVERWORLD, true, false, "airborne", NONE, true);
        test.receive(pending, 3, OVERWORLD, true, true, "", NONE, true);
        check(test.enters == 1 && test.exits == 0, "heartbeats and contact changes never retrigger entry");
        long stopped = test.stop(EXIT);
        check(stopped > pending, "normal stop increments token before emitting exit");
        test.tick(NONE, "subsequent end tick cannot replay local exit");
        test.receive(pending, 99, OVERWORLD, true, true, "", NONE, false);
        test.receive(stopped, 0, OVERWORLD, false, false, "manual", NONE, true);
        check(test.enters == 1 && test.exits == 1, "stop acknowledgement cannot duplicate local exit");

        pending = test.session.begin(true);
        test.tick(NONE, "restarting during exit waits for confirmation");
        test.stop(NONE);
        test.receive(pending, 0, OVERWORLD, true, true, "", NONE, false);
        test.tick(NONE, "cancelling unconfirmed restart emits neither transition");
        check(test.enters == 1 && test.exits == 1, "failed rapid restart is silent");

        for (String reason : List.of("hurt", "race", "timeout", "remote", "invalid")) {
            long token = test.session.begin(true);
            test.receive(token, 0, OVERWORLD, true, true, "", ENTER, true);
            test.receive(token, 1, OVERWORLD, false, false, reason, EXIT, true);
            test.tick(NONE, "server termination is emitted once: " + reason);
            test.receive(token, 2, OVERWORLD, false, false, reason, NONE, true);
        }
        int beforePendingTimeout = test.enters + test.exits;
        test.session.begin(true);
        for (int i = 0; i < 60; i++) check(!test.session.tickExpired(), "pending lease has not expired");
        check(test.session.tickExpired(), "pending lease eventually expires");
        test.stop(NONE);
        check(test.enters + test.exits == beforePendingTimeout, "pending timeout has no sound");

        long token = test.session.begin(true);
        test.receive(token, 0, OVERWORLD, true, true, "", ENTER, true);
        for (int i = 0; i < 61; i++) test.session.tickExpired();
        test.stop(EXIT);
        test.tick(NONE, "active heartbeat timeout emits one exit despite token increment");

        token = test.session.begin(true);
        test.receive(token, 0, OVERWORLD, true, true, "", ENTER, true);
        int beforeReset = test.enters + test.exits;
        test.silentReset();
        test.tick(NONE, "disconnect/reset does not emit exit");
        test.receive(token, 999, OVERWORLD, true, true, "", NONE, false);
        check(test.enters + test.exits == beforeReset, "old connection packets cannot produce sound after reset");

        token = test.session.begin(true);
        test.receive(token, 0, OVERWORLD, true, true, "", ENTER, true);
        beforeReset = test.enters + test.exits;
        // World-change END tick silences audio before the normal token-incrementing stop.
        test.audio.reset();
        test.stop(NONE);
        test.silentReset();
        test.dimension = NETHER;
        test.tick(NONE, "world-change stop is silent after explicit transition reset");
        test.receive(token, 999, NETHER, false, false, "dimension", NONE, false);
        check(test.enters + test.exits == beforeReset, "late new-dimension termination cannot add exit");

        token = test.session.begin(true);
        test.receive(token, 0, NETHER, true, true, "", ENTER, true);
        beforeReset = test.enters + test.exits;
        // Changed-world STATE callback resets and returns before accepting any state.
        test.silentReset();
        test.dimension = OVERWORLD;
        test.tick(NONE, "changed-world receiver clears silently before ordinary end tick");
        test.receive(token, 1, OVERWORLD, false, false, "dimension", NONE, false);
        check(test.enters + test.exits == beforeReset, "receiver cleanup and dimension notice cannot race into an exit");

        int entersBefore = test.enters, exitsBefore = test.exits;
        for (int i = 0; i < 12; i++) {
            token = test.session.begin(true);
            test.tick(NONE, "rapid new request is pending");
            test.receive(token, 0, OVERWORLD, true, true, "", ENTER, true);
            test.tick(NONE, "rapid accepted request enters only once");
            test.stop(EXIT);
            test.tick(NONE, "rapid stop exits only once");
        }
        check(test.enters - entersBefore == 12 && test.exits - exitsBefore == 12,
                "rapid confirmed switches produce exactly one entry and exit per session");
        System.out.println("PASS EarthSenseTransitionLifecycleTest: " + checks
                + " real-protocol session/transition confirmation, token, replay and silent-reset checks");
    }

    private static final class Harness {
        final EarthSenseSession session = new EarthSenseSession();
        final EarthSenseTransitionState audio = new EarthSenseTransitionState();
        String dimension = OVERWORLD;
        int enters, exits;

        void tick(EarthSenseTransitionState.Action expected, String reason) {
            var actual = audio.tick(session.active(), session.token());
            check(actual == expected, reason + ": expected " + expected + ", got " + actual);
            if (actual == ENTER) enters++;
            if (actual == EXIT) exits++;
        }

        void receive(long token, int sequence, String packetDimension, boolean active, boolean grounded,
                     String reason, EarthSenseTransitionState.Action expected, boolean accepted) {
            var state = new EarthSenseProtocol.State(token, sequence, packetDimension, active, grounded, reason, List.of());
            var buffer = new PacketByteBuf(Unpooled.buffer());
            try {
                EarthSenseProtocol.writeState(buffer, state);
                var decoded = EarthSenseProtocol.readState(buffer);
                check(state.equals(decoded), "state fixture roundtrips through real PacketByteBuf");
                boolean actual = session.accept(decoded, dimension);
                check(actual == accepted, "server-state acceptance matches token/sequence/world boundary");
                if (actual) tick(expected, "accepted server state triggers authoritative transition");
                else check(expected == NONE, "rejected states cannot request any sound");
            } finally { buffer.release(); }
        }

        long stop(EarthSenseTransitionState.Action expected) {
            long token = session.begin(false);
            tick(expected, "stop uses new cancellation token");
            return token;
        }

        void silentReset() { session.clear(); audio.reset(); }
    }

    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}
