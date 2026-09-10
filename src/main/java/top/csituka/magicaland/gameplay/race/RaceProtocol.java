package top.csituka.magicaland.gameplay.race;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class RaceProtocol {
    public static final int VERSION = 1, MAX_PLAYERS = 4096, MAX_STATE_BYTES = 1_048_576;
    public static final Identifier REQUEST = id("race_request_v1"), STATE = id("race_state_v1"),
            RULES = id("race_rules_v1"), CHOOSE = id("race_choose_v1"), EFFECT = id("race_effect_v1"),
            RESULT = id("race_result_v1");
    public record Result(long requestId, String denial) {
        public Result {
            if (requestId <= 0 || denial == null || denial.length() > 128 || !denial.matches("[a-z_]*"))
                throw new IllegalArgumentException("Invalid race result");
        }
    }
    public record View(RaceRules rules, String ownRace, boolean canManage, Map<UUID, String> players) {
        public View {
            if (rules == null || ownRace == null || (!ownRace.isEmpty() && !RaceDefinition.validId(ownRace)))
                throw new IllegalArgumentException("Invalid race view");
            players = Map.copyOf(players);
            if (players.size() > MAX_PLAYERS || players.values().stream().anyMatch(id -> !RaceDefinition.validId(id)))
                throw new IllegalArgumentException("Invalid race players");
        }
    }

    private RaceProtocol() {}
    private static Identifier id(String path) { return new Identifier("magicaland_gameplay", path); }

    public static void writeResult(PacketByteBuf buf, Result result) {
        buf.writeLong(result.requestId());
        buf.writeString(result.denial(), 128);
    }

    public static Result readResult(PacketByteBuf buf) {
        if (buf.readableBytes() > 524) throw new IllegalArgumentException("Oversized race result");
        Result result = new Result(buf.readLong(), buf.readString(128));
        if (buf.isReadable()) throw new IllegalArgumentException("Trailing race result data");
        return result;
    }

    public static void writeRules(PacketByteBuf buf, RaceRules rules) {
        buf.writeVarInt(VERSION).writeByte(rules.mode().ordinal()).writeBoolean(rules.freeAppearance())
                .writeLong(rules.revision());
        buf.writeVarInt(rules.enabledRaces().size());
        rules.enabledRaces().stream().sorted().forEach(id -> buf.writeString(id, 128));
    }

    private static RaceRules readRulesBody(PacketByteBuf buf) {
        if (buf.readVarInt() != VERSION) throw new IllegalArgumentException("Unsupported race protocol");
        int mode = buf.readUnsignedByte();
        if (mode >= RaceRules.ChangeMode.values().length) throw new IllegalArgumentException("Invalid mode");
        boolean free = buf.readBoolean();
        long revision = buf.readLong();
        int size = buf.readVarInt();
        if (size < 1 || size > 64) throw new IllegalArgumentException("Invalid race count");
        var enabled = new LinkedHashSet<String>();
        for (int i = 0; i < size; i++) if (!enabled.add(buf.readString(128)))
            throw new IllegalArgumentException("Duplicate race");
        return new RaceRules(RaceRules.ChangeMode.values()[mode], free, enabled, revision);
    }

    public static RaceRules readRules(PacketByteBuf buf) {
        if (buf.readableBytes() > 16384) throw new IllegalArgumentException("Oversized race rules");
        RaceRules rules = readRulesBody(buf);
        if (buf.isReadable()) throw new IllegalArgumentException("Trailing rules data");
        return rules;
    }

    public static void writeView(PacketByteBuf buf, View view) {
        writeRules(buf, view.rules());
        buf.writeString(view.ownRace(), 128).writeBoolean(view.canManage());
        buf.writeVarInt(view.players().size());
        view.players().forEach((player, race) -> buf.writeUuid(player).writeString(race, 128));
    }

    public static View readView(PacketByteBuf buf) {
        if (buf.readableBytes() > MAX_STATE_BYTES) throw new IllegalArgumentException("Oversized race view");
        RaceRules rules = readRulesBody(buf);
        String own = buf.readString(128);
        boolean manage = buf.readBoolean();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_PLAYERS) throw new IllegalArgumentException("Invalid player count");
        var players = new LinkedHashMap<UUID, String>();
        for (int i = 0; i < size; i++) if (players.put(buf.readUuid(), buf.readString(128)) != null)
            throw new IllegalArgumentException("Duplicate player");
        if (buf.isReadable()) throw new IllegalArgumentException("Trailing view data");
        return new View(rules, own, manage, players);
    }
}
