package top.elysianherd.magicaland.gameplay.remote;

import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import top.elysianherd.magicaland.gameplay.mixin.RemoteItemPickupOwnerAccessor;

public final class RemotePickup {
    private RemotePickup() {}

    public static boolean canPickup(ItemEntity item,UUID player) {
        if (item.isRemoved() || item.cannotPickup() || item.getStack().isEmpty()) return false;
        // getOwner() 是投掷者实体，原版拾取权限使用另一个 owner UUID 字段。
        UUID owner=((RemoteItemPickupOwnerAccessor)item).magicaland$getPickupOwner();
        return owner==null || owner.equals(player);
    }

    public static boolean clearPath(BlockView world,Entity source,Vec3d from,ItemEntity item) {
        return world.raycast(new RaycastContext(from,item.getBoundingBox().getCenter(),
                RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,source))
                .getType()==HitResult.Type.MISS;
    }

    public static boolean collect(RemoteCargoInventory cargo,ItemEntity item,UUID player) {
        if (!canPickup(item,player)) return false;
        ItemStack original=item.getStack(),remainder=cargo.addStack(original);
        if (remainder.getCount()==original.getCount()) return false;
        if (remainder.isEmpty()) item.discard(); else item.setStack(remainder);
        return true;
    }
}
