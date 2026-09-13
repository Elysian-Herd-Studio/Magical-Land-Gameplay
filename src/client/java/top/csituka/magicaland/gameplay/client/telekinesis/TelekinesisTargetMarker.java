package top.csituka.magicaland.gameplay.client.telekinesis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import top.csituka.magicaland.api.client.Appearances;

public final class TelekinesisTargetMarker {
    private static boolean initialized;
    private TelekinesisTargetMarker() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        WorldRenderEvents.AFTER_ENTITIES.register(TelekinesisTargetMarker::render);
    }

    private static void render(WorldRenderContext context) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || context.consumers() == null || client.options.hudHidden
                || !client.player.isAlive() || client.player.isSpectator() || !TelekinesisClient.aiming()) return;
        var target = TelekinesisClient.aimTarget();
        if (target == null) return;
        int color = Appearances.magicColor(client.player.getUuid());
        float red = (color >> 16 & 255) / 255f * .7f + .3f;
        float green = (color >> 8 & 255) / 255f * .7f + .3f;
        float blue = (color & 255) / 255f * .7f + .3f;
        var matrices = context.matrixStack();
        var camera = context.camera().getPos();
        matrices.push();
        try {
            var vertices = context.consumers().getBuffer(MarkerLayer.GLOW);
            draw(client, matrices, vertices, target, red, green, blue, context.tickDelta(), camera);
        } finally { matrices.pop(); }
    }

    private static void draw(MinecraftClient client, MatrixStack matrices, VertexConsumer vertices,
                             TelekinesisAim.Target target, float red, float green, float blue, float delta, Vec3d camera) {
        TelekinesisMarkerGeometry.Vertex sink = (x, y, z, alpha) -> vertices
                .vertex(matrices.peek().getPositionMatrix(), (float) x, (float) y, (float) z)
                .color(red, green, blue, alpha).next();
        if (target.block() != null) {
            if (!client.world.isChunkLoaded(target.block())) return;
            var state = client.world.getBlockState(target.block());
            if (state.isAir()) return;
            var shape = state.getOutlineShape(client.world, target.block());
            Box box = (shape.isEmpty() ? new Box(0, 0, 0, 1, 1, 1) : shape.getBoundingBox())
                    .offset(target.block()).expand(.008).offset(-camera.x, -camera.y, -camera.z);
            TelekinesisMarkerGeometry.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, sink);
            return;
        }
        var entity = client.world.getEntityById(target.entityId());
        if (entity == null || !entity.isAlive() || entity.isInvisible()) return;
        var box = entity.getBoundingBox().offset(entity.getLerpedPos(delta).subtract(entity.getPos()));
        double radius = Math.max(.65, Math.min(8, Math.max(box.maxX - box.minX, box.maxZ - box.minZ) * .5 + .28));
        double x = box.getCenter().x - camera.x, z = box.getCenter().z - camera.z, y = box.minY + .025 - camera.y;
        TelekinesisMarkerGeometry.ring(x, y, z, radius, sink);
    }

    private static final class MarkerLayer extends RenderLayer {
        private static final RenderLayer GLOW = of("magicaland_telekinesis_target", VertexFormats.POSITION_COLOR,
                VertexFormat.DrawMode.QUADS, 4096, false, true, MultiPhaseParameters.builder()
                        .program(COLOR_PROGRAM).transparency(TRANSLUCENT_TRANSPARENCY)
                        .depthTest(LEQUAL_DEPTH_TEST).writeMaskState(COLOR_MASK)
                        .cull(DISABLE_CULLING).build(false));
        private MarkerLayer() {
            super("magicaland_unused", VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS, 256,
                    false, true, () -> {}, () -> {});
        }
    }
}
