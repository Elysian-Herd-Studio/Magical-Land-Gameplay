package top.elysianherd.magicaland.gameplay.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import top.elysianherd.magicaland.gameplay.remote.RemoteToolEntity;
import top.elysianherd.magicaland.api.client.Appearances;
import top.elysianherd.magicaland.api.client.AppearanceVisuals;
import top.elysianherd.magicaland.api.client.ItemVisualContext;

public final class RemoteToolRenderer extends EntityRenderer<RemoteToolEntity> {
    public RemoteToolRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public Identifier getTexture(RemoteToolEntity entity) { return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE; }
    @Override public void render(RemoteToolEntity entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light) {
        var client=MinecraftClient.getInstance();
        if (entity.owner()==null) return;
        if (client.getCameraEntity()==entity && client.options.getPerspective().isFirstPerson()) return;
        int color=Appearances.magicColor(entity.owner());
        var frame=RemoteHeldAnimation.sample(entity,RemoteToolClient.visualStack(entity),RemoteToolClient.visualSlot(entity),delta);
        matrices.push();
        try {
            matrices.translate(0,.1,0);
            float heading=MathHelper.lerpAngleDegrees(delta,entity.prevYaw,entity.getYaw());
            if (frame.stack().isEmpty()) {
                AppearanceVisuals.renderFlame(matrices,entity,color,delta);
                return;
            }
            if (entity.returning()) {
                matrices.push();
                try {
                    matrices.scale(.55f,.55f,.55f);
                    AppearanceVisuals.renderFlame(matrices,entity,color,delta);
                } finally { matrices.pop(); }
            }
            var owner=entity.getWorld().getPlayerByUuid(entity.owner());
            boolean left=owner!=null && owner.getMainArm()==net.minecraft.util.Arm.LEFT;
            var side=RemoteAimMath.itemSideOffset(dispatcher.getRotation(),left);
            matrices.translate(side.x,side.y,side.z);
            var mode=left
                    ? ModelTransformationMode.THIRD_PERSON_LEFT_HAND : ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
            var model=client.getItemRenderer().getModel(frame.stack(),entity.getWorld(),owner,entity.getId());
            boolean flat=!model.hasDepth() && !model.isBuiltin();
            var item=frame.stack().getItem();
            boolean tool=item instanceof ToolItem || item instanceof RangedWeaponItem || item instanceof TridentItem
                    || item instanceof FishingRodItem || item instanceof ShearsItem
                    || item instanceof FlintAndSteelItem || item instanceof BrushItem;
            var pose=tool ? RemoteItemPose.tool(heading,
                    MathHelper.lerp(delta,entity.prevPitch,entity.getPitch()),frame.swing(),left,flat)
                    : flat ? RemoteItemPose.billboard(dispatcher.getRotation(),frame.swing(),left)
                    : RemoteItemPose.upright(heading);
            var display=model.getTransformation().getTransformation(mode).rotation;
            // 只抵消模型显示旋转；平移留给原版，避免移动惯性锚点。
            matrices.multiply(RemoteItemPose.withoutDisplayRotation(pose,display.x(),display.y(),display.z(),left));
            var view=new ItemVisualContext(entity,frame.stack(),1-frame.equip(),frame.swing(),frame.acting(),false);
            if (owner!=null) AppearanceVisuals.renderLevitatingItem(owner,view,mode,
                    matrices,buffers,entity.getWorld(),light,entity.getId(),color,delta);
            else AppearanceVisuals.renderGlowingItem(frame.stack(),mode,matrices,buffers,entity.getWorld(),light,entity.getId(),color);
        } finally {
            matrices.pop();
        }
    }
}
