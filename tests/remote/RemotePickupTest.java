import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import top.csituka.magicaland.gameplay.mixin.RemoteItemPickupOwnerAccessor;
import top.csituka.magicaland.gameplay.remote.RemoteCargoInventory;
import top.csituka.magicaland.gameplay.remote.RemotePickup;

public final class RemotePickupTest {
    private static int checks;
    private static final UUID PLAYER=new UUID(11,22),OTHER=new UUID(33,44);
    private static void check(boolean value,String detail) {
        checks++;
        if (!value) throw new AssertionError("pickup "+checks+": "+detail);
    }
    private static final class DroppedItem extends ItemEntity {
        Entity loadedThrower;
        DroppedItem(ItemStack stack) { super(EntityType.ITEM,null); setStack(stack); }
        @Override public Entity getOwner() { return loadedThrower; }
    }
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
        ownershipAndDelay(); transfer(); dropAndRecover(); groundAndWalls();
        System.out.println("PASS RemotePickupTest: "+checks+" checks");
    }
    private static DroppedItem item(ItemStack stack) { return new DroppedItem(stack); }
    private static void ownershipAndDelay() {
        var item=item(new ItemStack(Items.STONE,3));
        var thrower=new MarkerEntity(EntityType.MARKER,null); thrower.setUuid(PLAYER);
        item.loadedThrower=thrower; item.setThrower(PLAYER);
        check((ItemEntity)item instanceof RemoteItemPickupOwnerAccessor,"real ItemEntity accessor transformed");
        check(!PLAYER.equals(item.getOwner()),"old UUID-versus-Entity predicate rejects its own thrower");
        check(RemotePickup.canPickup(item,PLAYER),"online thrower can recollect an ordinary drop");
        check(RemotePickup.canPickup(item,OTHER),"ordinary drop is not reserved for its thrower");
        item.setOwner(OTHER);
        check(OTHER.equals(((RemoteItemPickupOwnerAccessor)(ItemEntity)item).magicaland$getPickupOwner()),"actual private pickup owner is read");
        check(!RemotePickup.canPickup(item,PLAYER),"thrower cannot bypass another recipient's ownership");
        check(RemotePickup.canPickup(item,OTHER),"recipient is allowed despite a different thrower");
        item.loadedThrower=null;
        check(!RemotePickup.canPickup(item,PLAYER),"offline thrower does not remove recipient protection");
        item.setOwner(PLAYER);
        check(RemotePickup.canPickup(item,PLAYER),"own reserved drop allowed");
        item.setOwner(null);
        for (int delay=40;delay>0;delay--) {
            item.setPickupDelay(delay);
            check(!RemotePickup.canPickup(item,PLAYER),"pickup delay "+delay);
        }
        item.resetPickupDelay(); check(RemotePickup.canPickup(item,PLAYER),"zero delay permits pickup");
        item.setPickupDelayInfinite(); check(!RemotePickup.canPickup(item,PLAYER),"infinite delay preserved");
        item.resetPickupDelay(); item.discard(); check(!RemotePickup.canPickup(item,PLAYER),"removed entity rejected");
        check(!RemotePickup.canPickup(item(ItemStack.EMPTY),PLAYER),"empty entity rejected");
    }
    private static void transfer() {
        var cargo=new RemoteCargoInventory();
        var item=item(new ItemStack(Items.STONE,13));
        check(RemotePickup.collect(cargo,item,PLAYER),"partial transfer accepted");
        check(cargo.getStack(0).getCount()==8 && item.getStack().getCount()==5 && !item.isRemoved(),"eight-item capacity leaves remainder in world");
        check(!RemotePickup.collect(cargo,item,PLAYER),"full slot does not consume item");
        check(item.getStack().getCount()==5,"full-slot remainder unchanged");
        cargo.removeStack(0,4);
        check(RemotePickup.collect(cargo,item,PLAYER),"free capacity accepts remaining stack");
        check(cargo.getStack(0).getCount()==8 && item.getStack().getCount()==1,"partial transfer conserves total");
        check(cargo.unlock(2),"second slot unlocked");
        check(RemotePickup.collect(cargo,item,PLAYER) && item.isRemoved(),"final transfer removes world entity");
        check(cargo.getStack(1).getCount()==1,"overflow fills only unlocked slot");
        check(!RemotePickup.collect(cargo,item,PLAYER),"same removed entity cannot duplicate items");
        var sword=item(new ItemStack(Items.DIAMOND_SWORD));
        check(!RemotePickup.collect(cargo,sword,PLAYER) && !sword.isRemoved(),"incompatible occupied slots preserved");
        cargo.removeStack(1);
        sword.getStack().setDamage(17); sword.getStack().getOrCreateNbt().putString("CustomFixture","kept");
        check(RemotePickup.collect(cargo,sword,PLAYER),"tool can be collected");
        check(cargo.getStack(1).getCount()==1 && cargo.getStack(1).getDamage()==17
                && cargo.getStack(1).getNbt().getString("CustomFixture").equals("kept"),"tool count durability and NBT preserved");
        var reserved=item(new ItemStack(Items.DIRT,4)); reserved.setOwner(OTHER);
        var empty=new RemoteCargoInventory();
        check(!RemotePickup.collect(empty,reserved,PLAYER) && empty.isEmpty() && reserved.getStack().getCount()==4,"ownership rejection cannot mutate either inventory");
        reserved.setOwner(PLAYER); reserved.setPickupDelay(40);
        check(!RemotePickup.collect(empty,reserved,PLAYER) && empty.isEmpty(),"delay rejection cannot mutate inventory");
    }
    private static void dropAndRecover() {
        for (boolean whole:new boolean[]{false,true}) for (int count=1;count<=8;count++) {
            var cargo=new RemoteCargoInventory(); cargo.setStack(0,new ItemStack(Items.GOLDEN_CARROT,count));
            DroppedItem[] dropped={null};
            check(cargo.dropFrom(0,whole,stack -> {
                dropped[0]=item(stack); dropped[0].setThrower(PLAYER); dropped[0].setPickupDelay(40);
                dropped[0].loadedThrower=new MarkerEntity(EntityType.MARKER,null);
                return true;
            }),"Q drop succeeds");
            check(!RemotePickup.collect(cargo,dropped[0],PLAYER),"Q drop retains two-second pickup delay");
            dropped[0].resetPickupDelay();
            check(RemotePickup.collect(cargo,dropped[0],PLAYER),"Q drop recollects after delay");
            check(cargo.getStack(0).getCount()==count && dropped[0].isRemoved(),"drop/recollect conserves all items");
        }
    }
    private static void groundAndWalls() {
        var world=new View(); var source=new MarkerEntity(EntityType.MARKER,null);
        var item=item(new ItemStack(Items.STONE));
        for (int x=-2;x<=3;x++) for (int z=-2;z<=2;z++) world.blocks.put(new BlockPos(x,-1,z),Blocks.STONE.getDefaultState());
        Vec3d from=new Vec3d(.25,.3,.5); item.setPosition(.75,0,.5);
        for (double y:new double[]{0,.00001,.05,.2,.6}) {
            item.setPosition(.75,y,.5); check(RemotePickup.clearPath(world,source,from,item),"ground pickup at height "+y);
        }
        for (int y=0;y<3;y++) for (int z=-1;z<=1;z++) world.blocks.put(new BlockPos(1,y,z),Blocks.STONE.getDefaultState());
        item.setPosition(1.3,.1,.5);
        check(!RemotePickup.clearPath(world,source,from,item),"solid wall still blocks pickup");
        world.blocks.replaceAll((pos,state)->pos.getX()==1 && pos.getY()>=0?Blocks.GLASS.getDefaultState():state);
        check(!RemotePickup.clearPath(world,source,from,item),"glass permits sight but still blocks pickup reach");
        world.blocks.clear(); world.blocks.put(new BlockPos(0,0,0),Blocks.STONE_SLAB.getDefaultState());
        item.setPosition(.75,.5,.5);
        check(RemotePickup.clearPath(world,source,new Vec3d(.25,.8,.5),item),"slab supporting surface permits pickup");
        world.blocks.put(new BlockPos(0,0,0),Blocks.STONE_SLAB.getDefaultState().with(Properties.SLAB_TYPE,SlabType.TOP));
        item.setPosition(.75,1,.5);
        check(RemotePickup.clearPath(world,source,new Vec3d(.25,1.3,.5),item),"top slab supporting surface permits pickup");
        item.setPosition(.75,.6,.5);
        check(!RemotePickup.clearPath(world,source,new Vec3d(.25,1.3,.5),item),"buried item remains blocked");
    }
}
