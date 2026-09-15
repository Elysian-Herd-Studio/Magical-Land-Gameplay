package top.elysianherd.magicaland.gameplay.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.elysianherd.magicaland.gameplay.remote.TelekinesisToken;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class TelekinesisTokenInputMixin {
    @Shadow @Final private MinecraftClient client;
    @Inject(method={"attackBlock", "updateBlockBreakingProgress"}, at=@At("HEAD"), cancellable=true)
    private void reservedMine(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> ci) {
        if (client.player != null && TelekinesisToken.isToken(client.player.getMainHandStack())) ci.setReturnValue(false);
    }
    @Inject(method="attackEntity", at=@At("HEAD"), cancellable=true)
    private void reservedAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (TelekinesisToken.isToken(player.getMainHandStack())) ci.cancel();
    }
    @Inject(method="interactBlock", at=@At("HEAD"), cancellable=true)
    private void reservedBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> ci) {
        if (TelekinesisToken.isToken(player.getStackInHand(hand))) ci.setReturnValue(ActionResult.FAIL);
    }
    @Inject(method="interactItem", at=@At("HEAD"), cancellable=true)
    private void reservedItem(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> ci) {
        if (TelekinesisToken.isToken(player.getStackInHand(hand))) ci.setReturnValue(ActionResult.FAIL);
    }
    @Inject(method="interactEntity", at=@At("HEAD"), cancellable=true)
    private void reservedEntity(PlayerEntity player, Entity target, Hand hand, CallbackInfoReturnable<ActionResult> ci) {
        if (TelekinesisToken.isToken(player.getStackInHand(hand))) ci.setReturnValue(ActionResult.FAIL);
    }
    @Inject(method="interactEntityAtLocation", at=@At("HEAD"), cancellable=true)
    private void reservedEntityAt(PlayerEntity player, Entity target, EntityHitResult hit, Hand hand, CallbackInfoReturnable<ActionResult> ci) {
        if (TelekinesisToken.isToken(player.getStackInHand(hand))) ci.setReturnValue(ActionResult.FAIL);
    }
}
