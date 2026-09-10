package top.csituka.magicaland.gameplay.mixin;

import java.util.UUID;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemEntity.class)
public interface RemoteItemPickupOwnerAccessor {
    @Accessor("owner") UUID magicaland$getPickupOwner();
}
