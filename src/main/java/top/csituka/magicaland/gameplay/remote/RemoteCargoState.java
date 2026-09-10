package top.csituka.magicaland.gameplay.remote;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

public final class RemoteCargoState extends PersistentState {
    private final Map<UUID,RemoteCargoInventory> inventories=new HashMap<>();
    public static RemoteCargoState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                RemoteCargoState::read,RemoteCargoState::new,"magicaland_remote_cargo");
    }
    public RemoteCargoInventory inventory(UUID owner) {
        return inventories.computeIfAbsent(owner,id -> {
            var inventory=new RemoteCargoInventory();
            inventory.addListener(changed -> markDirty());
            return inventory;
        });
    }
    public static RemoteCargoState read(NbtCompound nbt) {
        var state=new RemoteCargoState();
        NbtList entries=nbt.getList("Players",NbtElement.COMPOUND_TYPE);
        for (int i=0;i<entries.size();i++) {
            NbtCompound entry=entries.getCompound(i);
            if (entry.containsUuid("Owner")) state.inventory(entry.getUuid("Owner"))
                    .readNbtList(entry.getList("Items",NbtElement.COMPOUND_TYPE));
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList entries=new NbtList();
        inventories.forEach((owner,inventory) -> {
            if (inventory.isEmpty()) return;
            NbtCompound entry=new NbtCompound();
            entry.putUuid("Owner",owner); entry.put("Items",inventory.toNbtList()); entries.add(entry);
        });
        nbt.put("Players",entries);
        return nbt;
    }
}
