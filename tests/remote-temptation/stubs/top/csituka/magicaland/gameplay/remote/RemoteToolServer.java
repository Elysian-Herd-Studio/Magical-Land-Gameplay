package top.csituka.magicaland.gameplay.remote;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.ItemStack;
public final class RemoteToolServer {
    public static final List<RemoteToolEntity> tools = new ArrayList<>();
    public static RemoteToolEntity temptingTool(PathAwareEntity mob, Predicate<ItemStack> food) {
        RemoteToolEntity result = null;
        for (RemoteToolEntity tool : tools) {
            if (isTempting(tool, mob, food) && (result == null || tool.distance < result.distance)) result = tool;
        }
        return result;
    }
    public static boolean isTempting(RemoteToolEntity tool, PathAwareEntity mob, Predicate<ItemStack> food) {
        return tools.contains(tool) && mob.valid && tool.active && !tool.returning && tool.visible
                && tool.world == mob.world && tool.distance < 10 && food.test(tool.stack);
    }
}
