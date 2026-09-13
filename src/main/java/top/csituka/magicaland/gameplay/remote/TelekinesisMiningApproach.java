package top.csituka.magicaland.gameplay.remote;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

final class TelekinesisMiningApproach {
    private record Candidate(Vec3d goal, Direction side, boolean direct, double distance) {}
    private TelekinesisMiningApproach() {}

    static List<Vec3d> goals(RemoteToolEntity tool, BlockPos target) {
        var result = new ArrayList<Vec3d>();
        if (closeEnough(tool, target) && RemoteFlightCollision.clear(tool, point(tool.getPos()), point(tool.getPos()))) {
            var hit = TelekinesisSight.blockHit(tool, tool.getEyePos(), target, 2.6);
            if (hit != null) result.add(tool.getPos());
        }
        result.addAll(goals(tool.getPos(), tool.getEyeY() - tool.getY(), TelekinesisSight.surfaces(tool, target),
                at -> RemoteFlightCollision.clear(tool, point(at), point(at)), (eye, surface) -> {
                    if (!TelekinesisServer.loadedBetween(tool.getWorld(), eye, surface)) return false;
                    var hit = TelekinesisSight.raycast(tool, eye, surface);
                    return hit.getType() == HitResult.Type.BLOCK && target.equals(hit.getBlockPos());
                }, (from, to) -> RemoteFlightCollision.clear(tool, point(from), point(to))));
        return result.stream().distinct().limit(12).toList();
    }

    static boolean closeEnough(RemoteToolEntity tool, BlockPos target) {
        return new Box(target).expand(2.6).contains(tool.getEyePos());
    }

    static List<Vec3d> goals(Vec3d current, double eyeOffset, List<TelekinesisSight.Surface> surfaces,
                            Predicate<Vec3d> fits, BiPredicate<Vec3d, Vec3d> visible,
                            BiPredicate<Vec3d, Vec3d> clear) {
        var candidates = new ArrayList<Candidate>();
        for (var surface : surfaces) {
            Vec3d eye = surface.point().add(Vec3d.of(surface.side().getVector()).multiply(.65));
            Vec3d goal = eye.add(0, -eyeOffset, 0);
            if (!fits.test(goal) || !visible.test(eye, surface.point())) continue;
            candidates.add(new Candidate(goal, surface.side(), clear.test(current, goal), current.squaredDistanceTo(goal)));
        }
        candidates.sort(Comparator.comparing((Candidate c) -> !c.direct()).thenComparingDouble(Candidate::distance));
        var perSide = new EnumMap<Direction, Integer>(Direction.class);
        var result = new ArrayList<Vec3d>();
        for (var candidate : candidates) {
            if (perSide.getOrDefault(candidate.side(), 0) >= 2
                    || result.stream().anyMatch(at -> at.squaredDistanceTo(candidate.goal()) < .04)) continue;
            result.add(candidate.goal());
            perSide.merge(candidate.side(), 1, Integer::sum);
        }
        return List.copyOf(result);
    }

    private static RemoteReturnNavigator.Point point(Vec3d at) { return new RemoteReturnNavigator.Point(at.x, at.y, at.z); }
}
