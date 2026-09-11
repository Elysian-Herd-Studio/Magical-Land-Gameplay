package top.csituka.magicaland.gameplay.remote;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.GlassBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;

public final class RemoteVisibility {
    public static final double OCCLUSION_SAMPLE_RADIUS=.75;
    public static final int SAMPLE_COUNT=25;
    private static final double SURFACE_MARGIN=1e-4;
    private RemoteVisibility() {}

    public static Vec3d sample(Vec3d eye,Vec3d center,int index) {
        if (index==0) return center;
        Vec3d normal=center.subtract(eye).normalize();
        if (normal.lengthSquared()<1e-6) normal=new Vec3d(0,0,1);
        Vec3d reference=Math.abs(normal.y)>.95?new Vec3d(1,0,0):new Vec3d(0,1,0);
        Vec3d right=normal.crossProduct(reference).normalize(), up=right.crossProduct(normal).normalize();
        double radius=OCCLUSION_SAMPLE_RADIUS*Math.sqrt((index-.5)/(SAMPLE_COUNT-1));
        double angle=index*Math.PI*(3-Math.sqrt(5));
        return center.add(right.multiply(Math.cos(angle)*radius)).add(up.multiply(Math.sin(angle)*radius));
    }
    public static float occlusion(BlockView world,Entity owner,Vec3d eye,Vec3d center) {
        if (world instanceof net.minecraft.world.World level && !level.isRegionLoaded(
                BlockPos.ofFloored(Math.min(eye.x,center.x)-1-OCCLUSION_SAMPLE_RADIUS,
                        Math.min(eye.y,center.y)-OCCLUSION_SAMPLE_RADIUS,Math.min(eye.z,center.z)-1-OCCLUSION_SAMPLE_RADIUS),
                BlockPos.ofFloored(Math.max(eye.x,center.x)+1+OCCLUSION_SAMPLE_RADIUS,
                        Math.max(eye.y,center.y)+OCCLUSION_SAMPLE_RADIUS,Math.max(eye.z,center.z)+1+OCCLUSION_SAMPLE_RADIUS))) return 1;
        int blocked=0;
        for (int i=0;i<SAMPLE_COUNT;i++) {
            Vec3d target=sample(eye,center,i);
            if (i!=0) {
                var surface=raycast(world,owner,center,target);
                if (surface.getType()!=HitResult.Type.MISS) {
                    Vec3d offset=surface.getPos().subtract(center);
                    double distance=offset.length();
                    // 虚拟观察点留在球心这侧的可见空间，不能钻入贴近的地板或墙体。
                    target=surface.isInsideBlock() || distance<=SURFACE_MARGIN?center:
                            center.add(offset.multiply((distance-SURFACE_MARGIN)/distance));
                }
            }
            if (raycast(world,owner,eye,target).getType()!=HitResult.Type.MISS) blocked++;
        }
        return (float)blocked/SAMPLE_COUNT;
    }
    private static BlockHitResult raycast(BlockView world,Entity owner,Vec3d from,Vec3d to) {
        return world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,owner) {
            @Override public VoxelShape getBlockShape(BlockState state,BlockView view,BlockPos pos) {
                return opticalShape(state,view,pos);
            }
        });
    }
    public static VoxelShape opticalShape(BlockState state,BlockView world,BlockPos pos) {
        if (world instanceof net.minecraft.world.World level && !level.isChunkLoaded(pos)) return VoxelShapes.fullCube();
        var block=state.getBlock();
        if (block==Blocks.GLASS || block==Blocks.GLASS_PANE || block instanceof StainedGlassBlock
                || block instanceof StainedGlassPaneBlock || block instanceof GlassBlock && block!=Blocks.TINTED_GLASS)
            return VoxelShapes.empty();
        if ((block instanceof PlantBlock || block instanceof SugarCaneBlock)
                && state.getCollisionShape(world,pos).isEmpty()) return VoxelShapes.empty();
        return state.getOutlineShape(world,pos);
    }
}
