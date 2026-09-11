import java.util.HashMap;
import java.util.Map;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.state.property.Properties;
import net.minecraft.block.enums.SlabType;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import top.csituka.magicaland.gameplay.remote.RemoteVisibility;

public final class RemoteVisibilityTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("visibility "+checks); }
    private static final class View implements BlockView {
        final Map<BlockPos,BlockState> blocks=new HashMap<>();
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos,Blocks.AIR.getDefaultState()); }
        public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.getDefaultState(); }
        public int getHeight() { return 128; }
        public int getBottomY() { return -64; }
    }
    public static void main(String[] args) {
        SharedConstants.createGameVersion(); Bootstrap.initialize();
        check(RemoteVisibility.OCCLUSION_SAMPLE_RADIUS==.75);
        Vec3d eye=Vec3d.ZERO;
        var owner=new MarkerEntity(EntityType.MARKER,null);
        for (Vec3d center:new Vec3d[]{new Vec3d(4,0,0),new Vec3d(0,4,0),new Vec3d(0,-4,0),new Vec3d(-4,2,3)}) {
            for (int i=0;i<RemoteVisibility.SAMPLE_COUNT;i++) {
                Vec3d point=RemoteVisibility.sample(eye,center,i),offset=point.subtract(center);
                check(Double.isFinite(point.x+point.y+point.z) && offset.length()<=.750001);
                check(Math.abs(offset.dotProduct(center.normalize()))<1e-8);
            }
        }
        View world=new View();
        check(RemoteVisibility.occlusion(world,owner,eye,new Vec3d(4,.5,0))==0);
        for (int y=-4;y<=4;y++) for (int z=-4;z<0;z++) world.blocks.put(new BlockPos(2,y,z),Blocks.STONE.getDefaultState());
        check(RemoteVisibility.occlusion(world,owner,eye,new Vec3d(4,.5,-2))==1);
        float partial=RemoteVisibility.occlusion(world,owner,eye,new Vec3d(4,.5,0));
        check(partial>0 && partial<1);
        check(RemoteVisibility.occlusion(world,owner,eye,new Vec3d(4,.5,2))==0);
        int transitions=0;
        for (int i=-10;i<=10;i++) { float value=RemoteVisibility.occlusion(world,owner,eye,new Vec3d(4,.5,i*.05)); if (value>0 && value<1) transitions++; }
        check(transitions>=10);
        world.blocks.replaceAll((pos,state)->Blocks.GLASS.getDefaultState());
        check(RemoteVisibility.occlusion(world,owner,eye,new Vec3d(4,.5,-2))==0);
        check(world.raycast(new RaycastContext(eye,new Vec3d(4,.5,-2),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,owner)).getType()==HitResult.Type.BLOCK);
        world.blocks.replaceAll((pos,state)->Blocks.TINTED_GLASS.getDefaultState());
        check(RemoteVisibility.occlusion(world,owner,eye,new Vec3d(4,.5,-2))==1);
        world.blocks.clear();
        for (int x=-4;x<=4;x++) for (int z=-4;z<0;z++) world.blocks.put(new BlockPos(x,2,z),Blocks.STONE.getDefaultState());
        check(RemoteVisibility.occlusion(world,owner,eye,new Vec3d(.5,4,-2))==1);
        float up=RemoteVisibility.occlusion(world,owner,eye,new Vec3d(.5,4,0)); check(up>0 && up<1);
        check(RemoteVisibility.occlusion(world,owner,eye,new Vec3d(.5,4,2))==0);
        surfaceContacts(owner);
        decorativePlants(owner);
        solidBoundaries(owner);
        System.out.println("PASS RemoteVisibilityTest: "+checks+" checks");
    }
    private static void surfaceContacts(MarkerEntity owner) {
        View world=new View();
        Vec3d eye=new Vec3d(.5,1.62,.5);
        plane(world,-1,Blocks.STONE.getDefaultState());
        for (double height:new double[]{.15,.05,.0001,.3,.65,.76}) {
            float coverage=RemoteVisibility.occlusion(world,owner,eye,new Vec3d(4.5,height,.5));
            checks++;
            if (coverage!=0) throw new AssertionError("clear floor at height "+height+": "+coverage);
        }
        check(RemoteVisibility.occlusion(world,owner,new Vec3d(.5,3.5,-1.5),new Vec3d(4.5,.15,.5))==0);
        // 同一块真实地板隔在观察者与球体之间时，仍然完全遮挡。
        check(RemoteVisibility.occlusion(world,owner,eye,new Vec3d(4.5,-1.2,.5))==1);
        check(RemoteVisibility.occlusion(world,owner,new Vec3d(.5,-1.5,.5),new Vec3d(4.5,.15,.5))==1);
        check(RemoteVisibility.occlusion(world,owner,eye,new Vec3d(4.5,-.25,.5))==1);
        for (int y=0;y<4;y++) for (int z=-3;z<=3;z++)
            world.blocks.put(new BlockPos(2,y,z),Blocks.STONE.getDefaultState());
        check(RemoteVisibility.occlusion(world,owner,eye,new Vec3d(3.15,.15,.5))==1);
        check(RemoteVisibility.occlusion(world,owner,new Vec3d(.5,1.62,-1.5),new Vec3d(1.85,.15,.5))==0);
        for (int y=0;y<4;y++) for (int z=-3;z<=3;z++)
            world.blocks.put(new BlockPos(2,y,z),Blocks.GLASS.getDefaultState());
        check(RemoteVisibility.occlusion(world,owner,eye,new Vec3d(3.15,.15,.5))==0);
        check(world.raycast(new RaycastContext(eye,new Vec3d(3.15,.15,.5),RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,owner)).getType()==HitResult.Type.BLOCK);

        world.blocks.clear();
        plane(world,0,Blocks.STONE_SLAB.getDefaultState());
        check(RemoteVisibility.occlusion(world,owner,eye,new Vec3d(4.5,.65,.5))==0);
        check(RemoteVisibility.occlusion(world,owner,eye,new Vec3d(4.5,-.2,.5))==1);
        world.blocks.clear();
        plane(world,1,Blocks.STONE_SLAB.getDefaultState().with(Properties.SLAB_TYPE,SlabType.TOP));
        Vec3d lowEye=new Vec3d(.5,.6,.5);
        check(RemoteVisibility.occlusion(world,owner,lowEye,new Vec3d(4.5,1.35,.5))==0);
        check(RemoteVisibility.occlusion(world,owner,lowEye,new Vec3d(4.5,2.2,.5))==1);

        world.blocks.clear();
        plane(world,2,Blocks.STONE.getDefaultState());
        check(RemoteVisibility.occlusion(world,owner,eye,new Vec3d(4.5,1.85,.5))==0);
        check(RemoteVisibility.occlusion(world,owner,eye,new Vec3d(4.5,3.15,.5))==1);

        world.blocks.clear();
        world.blocks.put(new BlockPos(3,0,0),Blocks.STONE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.EAST));
        Vec3d tread=new Vec3d(3.25,.65,.5), above=new Vec3d(3.25,2,.5);
        check(world.raycast(new RaycastContext(above,tread,RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,owner)).getType()==HitResult.Type.MISS);
        check(RemoteVisibility.occlusion(world,owner,above,tread)==0);
        check(RemoteVisibility.occlusion(world,owner,new Vec3d(3.75,2,.5),new Vec3d(3.75,-.2,.5))>0);
    }
    private static void plane(View world,int y,BlockState state) {
        for (int x=-4;x<=8;x++) for (int z=-4;z<=4;z++) world.blocks.put(new BlockPos(x,y,z),state);
    }
    private static void decorativePlants(MarkerEntity owner) {
        View world=new View(); BlockPos sample=new BlockPos(2,0,0);
        Vec3d eye=new Vec3d(.5,.5,.5),center=new Vec3d(4.5,.5,.5);
        for (BlockState plant:new BlockState[]{
                Blocks.GRASS.getDefaultState(),Blocks.TALL_GRASS.getDefaultState(),
                Blocks.FERN.getDefaultState(),Blocks.LARGE_FERN.getDefaultState(),
                Blocks.DANDELION.getDefaultState(),Blocks.POPPY.getDefaultState(),Blocks.BLUE_ORCHID.getDefaultState(),
                Blocks.WHEAT.getDefaultState().with(Properties.AGE_7,7),
                Blocks.CARROTS.getDefaultState().with(Properties.AGE_7,7),
                Blocks.POTATOES.getDefaultState().with(Properties.AGE_7,7),
                Blocks.BEETROOTS.getDefaultState().with(Properties.AGE_3,3),
                Blocks.NETHER_WART.getDefaultState().with(Properties.AGE_3,3),
                Blocks.SWEET_BERRY_BUSH.getDefaultState().with(Properties.AGE_3,3),
                Blocks.SUGAR_CANE.getDefaultState(),Blocks.OAK_SAPLING.getDefaultState(),
                Blocks.DEAD_BUSH.getDefaultState(),Blocks.SEAGRASS.getDefaultState()}) {
            world.blocks.clear();
            check(!plant.getOutlineShape(world,sample).isEmpty() && plant.getCollisionShape(world,sample).isEmpty());
            check(RemoteVisibility.opticalShape(plant,world,sample).isEmpty());
            wall(world,2,plant);
            check(RemoteVisibility.occlusion(world,owner,eye,center)==0);
            if (plant.isOf(Blocks.GRASS)) check(world.raycast(new RaycastContext(eye,center,RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,owner)).getType()==HitResult.Type.BLOCK);
            wall(world,3,Blocks.STONE.getDefaultState());
            check(RemoteVisibility.occlusion(world,owner,eye,center)==1);
        }
    }
    private static void solidBoundaries(MarkerEntity owner) {
        View world=new View(); BlockPos sample=new BlockPos(2,0,0);
        for (BlockState state:new BlockState[]{Blocks.STONE.getDefaultState(),Blocks.GRASS_BLOCK.getDefaultState(),
                Blocks.OAK_LEAVES.getDefaultState(),Blocks.OAK_DOOR.getDefaultState().with(Properties.OPEN,false),
                Blocks.OAK_FENCE.getDefaultState(),Blocks.OAK_FENCE_GATE.getDefaultState().with(Properties.OPEN,false),
                Blocks.STONE_STAIRS.getDefaultState(),Blocks.STONE_SLAB.getDefaultState(),Blocks.SNOW.getDefaultState(),
                Blocks.LILY_PAD.getDefaultState(),Blocks.PUMPKIN.getDefaultState(),Blocks.MELON.getDefaultState()}) {
            var optical=RemoteVisibility.opticalShape(state,world,sample);
            check(!optical.isEmpty());
            check(!VoxelShapes.matchesAnywhere(optical,state.getOutlineShape(world,sample),BooleanBiFunction.NOT_SAME));
        }
        for (BlockState glass:new BlockState[]{Blocks.GLASS.getDefaultState(),Blocks.GLASS_PANE.getDefaultState(),
                Blocks.WHITE_STAINED_GLASS.getDefaultState(),Blocks.WHITE_STAINED_GLASS_PANE.getDefaultState()})
            check(RemoteVisibility.opticalShape(glass,world,sample).isEmpty());
        check(!RemoteVisibility.opticalShape(Blocks.TINTED_GLASS.getDefaultState(),world,sample).isEmpty());
        Vec3d eye=new Vec3d(.5,.5,.5),center=new Vec3d(4.5,.5,.5);
        for (BlockState solid:new BlockState[]{Blocks.OAK_LEAVES.getDefaultState(),Blocks.GRASS_BLOCK.getDefaultState(),
                Blocks.OAK_DOOR.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.EAST).with(Properties.OPEN,false)}) {
            world.blocks.clear(); wall(world,2,solid);
            check(RemoteVisibility.occlusion(world,owner,eye,center)==1);
        }
    }
    private static void wall(View world,int x,BlockState state) {
        for (int y=-3;y<=3;y++) for (int z=-3;z<=3;z++) world.blocks.put(new BlockPos(x,y,z),state);
    }
}
