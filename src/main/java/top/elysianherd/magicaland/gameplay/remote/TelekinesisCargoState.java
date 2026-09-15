package top.elysianherd.magicaland.gameplay.remote;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

final class TelekinesisCargoState extends PersistentState {
    record Cargo(UUID owner, int sourceSlot, RemoteCargoInventory inventory) {}
    private final Map<Long, Cargo> cargos = new LinkedHashMap<>();
    private long nextId;
    static TelekinesisCargoState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TelekinesisCargoState::read,
                TelekinesisCargoState::new, "magicaland_telekinesis_cargo");
    }
    Map<Long, Cargo> entries() { return Map.copyOf(cargos); }
    long create(UUID owner, int sourceSlot) {
        long id = Math.incrementExact(nextId);
        nextId = id;
        add(id, owner, sourceSlot); markDirty(); return id;
    }
    Cargo cargo(long id) { return cargos.get(id); }
    void prune(long id) { var cargo = cargos.get(id); if (cargo != null && cargo.inventory.isEmpty()) { cargos.remove(id); markDirty(); } }
    private Cargo add(long id, UUID owner, int sourceSlot) {
        var inventory = new RemoteCargoInventory(); inventory.unlock(9); inventory.select(0);
        inventory.addListener(changed -> markDirty());
        var cargo = new Cargo(owner, sourceSlot, inventory); cargos.put(id, cargo); return cargo;
    }
    static TelekinesisCargoState read(NbtCompound nbt) {
        var state = new TelekinesisCargoState(); state.nextId = Math.max(0, nbt.getLong("NextId"));
        NbtList entries = nbt.getList("Tasks", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i); long id = entry.getLong("Id");
            if (id <= 0 || !entry.containsUuid("Owner") || state.cargos.containsKey(id)) continue;
            state.nextId = Math.max(state.nextId, id);
            state.add(id, entry.getUuid("Owner"), entry.getInt("SourceSlot")).inventory.readSaved(entry);
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList entries = new NbtList();
        cargos.forEach((id, cargo) -> {
            if (cargo.inventory.isEmpty()) return;
            NbtCompound entry = new NbtCompound(); entry.putLong("Id", id); entry.putUuid("Owner", cargo.owner);
            entry.putInt("SourceSlot", cargo.sourceSlot); cargo.inventory.writeSaved(entry); entries.add(entry);
        });
        nbt.putLong("NextId", nextId); nbt.put("Tasks", entries); return nbt;
    }
}
