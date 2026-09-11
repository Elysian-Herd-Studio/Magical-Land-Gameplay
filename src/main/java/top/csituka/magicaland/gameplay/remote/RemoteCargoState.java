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
            if (entry.containsUuid("Owner")) state.inventory(entry.getUuid("Owner")).readSaved(entry);
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList entries=new NbtList();
        inventories.forEach((owner,inventory) -> {
            if (inventory.isEmpty() && inventory.unlockedSlots()==RemoteCapabilities.CARGO_SLOTS
                    && inventory.selectedSlot()==0) return;
            NbtCompound entry=new NbtCompound();
            entry.putUuid("Owner",owner); inventory.writeSaved(entry); entries.add(entry);
        });
        nbt.putInt("Version",2); nbt.put("Players",entries);
        return nbt;
    }
}
