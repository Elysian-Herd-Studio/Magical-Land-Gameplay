package top.elysianherd.magicaland.gameplay.client.telekinesis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import top.elysianherd.magicaland.gameplay.remote.TelekinesisProtocol.Task;
import top.elysianherd.magicaland.gameplay.remote.TelekinesisRules;
import top.elysianherd.magicaland.gameplay.remote.TelekinesisSight;

public final class TelekinesisAim {
    public record Target(BlockPos block, int entityId) {}
    private TelekinesisAim() {}

    public static Target sample(MinecraftClient client, ItemStack stack) {
        if (client.player == null || client.world == null || client.getCameraEntity() != client.player
                || !TelekinesisRules.supports(stack)) return null;
        var player = client.player;
        var start = player.getEyePos();
        var end = start.add(player.getRotationVec(1).multiply(TelekinesisRules.RANGE));
        BlockHitResult block = blockHit(client);
        double nearest = block != null && block.getType() == HitResult.Type.BLOCK
                ? start.squaredDistanceTo(block.getPos()) : TelekinesisRules.RANGE * TelekinesisRules.RANGE;
        Entity nearestEntity = null;
        for (var entity : client.world.getOtherEntities(player, new Box(start, end).expand(1),
                candidate -> !candidate.isRemoved() && !candidate.isSpectator() && candidate.canHit())) {
            var box = entity.getBoundingBox().expand(entity.getTargetingMargin());
            var hit = box.contains(start) ? start : box.raycast(start, end).orElse(null);
            if (hit != null && start.squaredDistanceTo(hit) < nearest) {
                nearest = start.squaredDistanceTo(hit); nearestEntity = entity;
            }
        }
        if (nearestEntity != null) return TelekinesisRules.supports(Task.GUARD, stack)
                && TelekinesisRules.directTarget(player, nearestEntity) ? new Target(null, nearestEntity.getId()) : null;
        if (block == null || block.getType() != HitResult.Type.BLOCK) return null;
        var state = client.world.getBlockState(block.getBlockPos());
        return TelekinesisRules.canGather(stack, state, false) && state.getHardness(client.world, block.getBlockPos()) >= 0
                ? new Target(block.getBlockPos(), -1) : null;
    }

    private static BlockHitResult blockHit(MinecraftClient client) {
        if (client.player == null || client.world == null || client.getCameraEntity() != client.player) return null;
        var player = client.player;
        var start = player.getEyePos();
        var end = start.add(player.getRotationVec(1).multiply(TelekinesisRules.RANGE));
        return TelekinesisSight.raycast(player, start, end);
    }
}
