package top.elysianherd.magicaland.gameplay.remote;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class RemoteTargeting {
    private RemoteTargeting() {}

    public static boolean attackable(Entity target) {
        if (target instanceof EnderDragonPart part)
            return !part.isRemoved() && part.canHit() && part.owner.isAlive() && !part.owner.isSpectator()
                    && part.getWorld()==part.owner.getWorld();
        return target instanceof LivingEntity && target.isAlive() && !target.isSpectator() && target.canHit();
    }

    public static Vec3d hitPoint(Box box,Vec3d from,Vec3d to) {
        return box.contains(from)?from:box.raycast(from,to).orElse(null);
    }
}
