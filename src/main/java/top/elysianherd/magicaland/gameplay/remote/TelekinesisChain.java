package top.elysianherd.magicaland.gameplay.remote;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

final class TelekinesisChain {
    private final int limit;
    private final Set<BlockPos> seen = new HashSet<>();
    private final Set<BlockPos> completed = new HashSet<>();
    private final Set<BlockPos> pending = new LinkedHashSet<>();
    private BlockState material;
    private int collected;

    TelekinesisChain(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Invalid chain limit");
        this.limit = limit;
    }

    void start(BlockPos origin, BlockState state) {
        clear(); material = state; seen.add(origin.toImmutable());
    }

    void mined(BlockPos pos, Function<BlockPos, BlockState> loadedState) {
        if (material == null || !seen.contains(pos) || collected >= limit || !completed.add(pos.toImmutable())) return;
        pending.remove(pos); collected++;
        if (!TelekinesisRules.chainable(material) || collected >= limit) return;
        // 先面邻接，再棱和角；树枝与斜向矿脉也可延续。
        for (int distance = 1; distance <= 3; distance++)
            for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                if (Math.abs(x) + Math.abs(y) + Math.abs(z) != distance) continue;
                BlockPos next = pos.add(x, y, z);
                if (seen.size() >= limit || seen.contains(next)) continue;
                BlockState state = loadedState.apply(next);
                if (state != null && TelekinesisRules.sameChain(material, state)) {
                    seen.add(next); pending.add(next);
                }
            }
    }

    void defer(BlockPos pos) {
        if (material != null && collected < limit && seen.contains(pos) && !completed.contains(pos)) pending.add(pos.toImmutable());
    }

    BlockPos next(Function<BlockPos, BlockState> loadedState, Predicate<BlockPos> available) {
        if (material == null || collected >= limit) return null;
        var iterator = pending.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            BlockState state = loadedState.apply(pos);
            if (state == null || !TelekinesisRules.sameChain(material, state)) { iterator.remove(); continue; }
            // 暂时挡住的邻块留在队列，挖开其他邻块后再检查。
            if (available.test(pos)) { iterator.remove(); return pos; }
        }
        return null;
    }

    void clear() { material = null; collected = 0; seen.clear(); completed.clear(); pending.clear(); }
}
