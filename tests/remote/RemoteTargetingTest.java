import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import top.csituka.magicaland.gameplay.remote.RemoteTargeting;

public final class RemoteTargetingTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        SharedConstants.createGameVersion(); Bootstrap.initialize();
        Box body=new Box(-.3,0,2,.3,1.8,2.6), wall=new Box(-1,0,1,1,1.2,1.5);
        Vec3d from=new Vec3d(0,1.65,0),to=from.add(0,0,3);
        Vec3d head=RemoteTargeting.hitPoint(body,from,to);
        check(head!=null && Math.abs(head.z-2)<1e-9,"three-block ray hits exposed head");
        check(wall.raycast(from,body.getCenter()).isPresent(),"old center ray is blocked by half-wall");
        check(wall.raycast(from,head).isEmpty(),"actual head hit is unobstructed");
        check(new Box(-1,0,1,1,2,1.5).raycast(from,head).isPresent(),"full wall still blocks actual hit");
        check(RemoteTargeting.hitPoint(body,from,from.add(0,0,1.99))==null,"reach cannot be bypassed");
        check(RemoteTargeting.hitPoint(body,from,from.add(3,0,0))==null,"turning away before revalidation misses");
        check(RemoteTargeting.hitPoint(body.offset(3,0,0),from,to)==null,"callback target movement is rechecked");
        Vec3d inside=body.getCenter();
        check(RemoteTargeting.hitPoint(body,inside,inside.add(0,0,3)).equals(inside),"inside box has a zero-length hit");
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)field.get(null);
        // 仅提供实体构造所需的世界对象，不加载区块或运行世界循环。
        var world=(ClientWorld)unsafe.allocateInstance(ClientWorld.class);
        var dragon=new EnderDragonEntity(EntityType.ENDER_DRAGON,world);
        check(!RemoteTargeting.attackable(dragon),"dragon body delegates hits to parts");
        for (var part:dragon.getBodyParts()) check(RemoteTargeting.attackable(part),"living dragon part can be targeted");
        dragon.head.discard();
        check(!RemoteTargeting.attackable(dragon.head),"removed part is not targetable");
        dragon.discard();
        for (var part:dragon.getBodyParts()) check(!RemoteTargeting.attackable(part),"dead owner invalidates every part");
        check(!RemoteTargeting.attackable(new MarkerEntity(EntityType.MARKER,null)),"unrelated entities remain excluded");
        System.out.println("PASS RemoteTargetingTest: "+checks+" checks");
    }
    private static void check(boolean ok,String label) { checks++; if (!ok) throw new AssertionError(label); }
}
