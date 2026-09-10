package top.csituka.magicaland.gameplay.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.MathHelper;
import top.csituka.magicaland.gameplay.remote.RemoteToolEntity;
import top.csituka.magicaland.api.client.Appearances;
import top.csituka.magicaland.api.client.AppearanceVisuals;

public final class RemoteToolRenderer extends EntityRenderer<RemoteToolEntity> {
    public RemoteToolRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public Identifier getTexture(RemoteToolEntity entity) { return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE; }
    @Override public void render(RemoteToolEntity entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light) {
        var client=MinecraftClient.getInstance();
        if (entity.owner()==null) return;
        if (client.getCameraEntity()==entity && client.options.getPerspective().isFirstPerson()) return;
        int color=Appearances.magicColor(entity.owner());
        matrices.push();
        matrices.translate(0,.1,0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerpAngleDegrees(delta,entity.prevYaw,entity.getYaw())));
        if (entity.stack().isEmpty()) AppearanceVisuals.renderOrb(matrices,color,entity.age+delta,entity.getId());
        else {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(delta,entity.prevPitch,entity.getPitch())));
            matrices.translate(-.20,.025*Math.sin((entity.age+delta)*.12),0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(65));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-12));
            AppearanceVisuals.renderGlowingItem(entity.stack(),ModelTransformationMode.GROUND,
                    matrices,buffers,entity.getWorld(),light,entity.getId(),color);
        }
        matrices.pop();
    }
}
