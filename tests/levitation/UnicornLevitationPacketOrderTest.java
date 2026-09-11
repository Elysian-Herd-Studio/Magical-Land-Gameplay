package top.csituka.magicaland.gameplay.levitation;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import static top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath.*;

/** 真实移动包先到、END tick 后补给，不把客户端和服务器虚构为同刻积分。 */
public final class UnicornLevitationPacketOrderTest {
    private static int checks;
    public static void main(String[] args) {
        check(legacyRejects(), "old fixed .12 balance rejects fifth .125 W packet before tick refill");
        for (Mode mode : new Mode[]{Mode.ASCEND, Mode.HOVER, Mode.SURFACE}) for (int batch : new int[]{1, 2, 3}) {
            var budget = new UnicornLevitationBudget(0, .15, 0, Motion.ZERO);
            Motion motion = Motion.ZERO; double x = 0, y = .15, z = 0;
            for (int tick = 0; tick < 25; tick++) budget.advance(tick, 0, 0, 0, mode, y, 0, mode == Mode.HOVER);
            for (int tick = 25; tick < 625; tick++) {
                float yaw = (tick / 80) % 2 == 0 ? 0 : 45;
                float forward = tick % 120 < 100 ? 1 : 0;
                motion = step(motion, yaw, forward, 0, mode, y, 0, mode == Mode.HOVER);
                x += motion.x(); y += motion.y(); z += motion.z();
                if ((tick - 24) % batch == 0) accept(budget, tick, x, y, z, false, mode + " batch " + batch + " tick " + tick);
                budget.advance(tick, yaw, forward, 0, mode, y - motion.y(), 0, mode == Mode.HOVER);
            }
        }
        for (double multiplier : new double[]{1.2, 2, 5}) {
            var budget = new UnicornLevitationBudget(0, 0, 0, Motion.ZERO);
            Motion motion = Motion.ZERO; double z = 0; boolean rejected = false;
            for (int tick = 0; tick < 1000; tick++) {
                motion = step(motion, 0, 1, 0, Mode.HOVER, 0, 0, true); z += motion.z() * multiplier;
                if (!budget.accept(tick, 0, 0, z)) { rejected = true; break; }
                budget.advance(tick, 0, 1, 0, Mode.HOVER, 0, 0, true);
            }
            check(rejected, "forecast is repaid, never sustainable extra speed " + multiplier);
        }
        transitions(); borrowedAxisStop(); landing(); liquidBuffer();
        System.out.println("PASS UnicornLevitationPacketOrderTest: " + checks + " real movement packets and fall handoff cases");
    }
    private static void borrowedAxisStop() {
        var budget = new UnicornLevitationBudget(0, 0, 0, Motion.ZERO);
        for (int tick = 0; tick < 50; tick++) budget.advance(tick, 0, 0, 0, Mode.ASCEND, 0, 0);
        double y = 0;
        for (int packet = 0; packet < 4; packet++) {
            y += .16; accept(budget, 50, 0, y, 0, false, "four ASCEND packets use finite forecast");
        }
        budget.advance(50, 0, 0, 0, Mode.HOVER, y, 0, true);
        y += .08; accept(budget, 51, 0, y, 0, false, "new HOVER frame retains committed upward credit");
        budget.advance(51, 0, 0, 0, Mode.HOVER, y, 0, true);
        accept(budget, 52, 0, y, 0, false, "stopped axis does not retain permanent negative debt");
        budget.advance(52, 0, 0, 0, Mode.ASCEND, y, 0);
        y += .08; accept(budget, 53, 0, y, 0, false, "legal upward restart after zero axis");
        for (int tick = 54; tick < 154; tick++) {
            budget.advance(tick, 0, 0, 0, Mode.HOVER, y, 0, true);
            accept(budget, tick, 0, y, 0, false, "zero move remains valid without per-packet reset");
        }
        check(!budget.accept(154, 0, y + 1, 0), "mode change cannot bank unbounded previous forecasts");
    }
    private static void transitions() {
        var budget = new UnicornLevitationBudget(0, .15, 0, Motion.ZERO);
        Motion motion = Motion.ZERO; double x = 0, y = .15, z = 0;
        for (int tick = 0; tick < 1800; tick++) {
            Mode mode = switch ((tick / 60) % 3) { case 0 -> Mode.ASCEND; case 1 -> Mode.HOVER; default -> Mode.SURFACE; };
            float yaw = tick % 180;
            double surface = y - .15;
            motion = step(motion, yaw, 1, .5f, mode, y, surface, mode == Mode.HOVER);
            x += motion.x(); y += motion.y(); z += motion.z();
            accept(budget, tick, x, y, z, false, "ASCEND/HOVER/SURFACE continuous movement " + tick);
            budget.advance(tick, yaw, 1, .5f, mode, y - motion.y(), surface, mode == Mode.HOVER);
        }
    }
    private static void accept(UnicornLevitationBudget budget, int tick, double x, double y, double z, boolean ground, String label) {
        var bytes = new PacketByteBuf(Unpooled.buffer());
        try {
            new PlayerMoveC2SPacket.Full(x, y, z, 0, 0, ground).write(bytes);
            var packet = PlayerMoveC2SPacket.Full.read(bytes);
            check(!bytes.isReadable() && packet.isOnGround() == ground, "vanilla movement wire roundtrip");
            boolean accepted = budget.accept(tick, packet.getX(0), packet.getY(0), packet.getZ(0));
            if (!accepted) {
                try { for (var field : UnicornLevitationBudget.class.getDeclaredFields()) {
                    field.setAccessible(true); System.err.println(field.getName() + "=" + field.get(budget));
                } } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
                System.err.println("packet=" + x + "," + y + "," + z);
            }
            check(accepted, label);
        } finally { bytes.release(); }
    }
    private static boolean legacyRejects() {
        double credit = .12, expected = 0, velocity = 0;
        for (int tick = 1; tick <= 5; tick++) {
            velocity = Math.min(.18, velocity + .025);
            if (velocity > credit + 1e-5) return tick == 5;
            credit -= velocity; expected = Math.min(.18, expected + .025);
            credit = Math.min(.12 + 3 * expected, credit + expected);
        }
        return false;
    }
    private static void landing() {
        var rules = new UnicornLevitationRules();
        check(!rules.protectsFall(true, 1), "no session no protection");
        rules.input(1, 0, true, 1);
        for (int tick = 1; tick <= 21; tick++) {
            check(rules.protectsFall(true, tick), "armed protects even if no slow samples or mode is OFF");
            check(!rules.protectsFall(false, tick), "unarmed baseline never protects");
        }
        check(!rules.protectsFall(true, 22), "expired lease cannot protect");
        rules.input(1, 1, true, 23); check(rules.protectsFall(true, 23), "fresh active input");
        rules.close(); check(!rules.protectsFall(true, 23), "manual close removes protection immediately");
        check(!rules.input(1, 2, true, 24), "late enable cannot reopen closed token");
        rules.input(2, 0, true, 24); check(rules.protectsFall(true, 24), "new explicit activation restores protection");
    }
    private static void liquidBuffer() {
        for (double height : new double[]{1, 2, 8, 64}) for (int batch : new int[]{1, 2, 3}) {
            double x=0,y=height,z=0,fall=0,observedY=y;Motion motion=Motion.ZERO;Mode mode=Mode.OFF;
            UnicornLevitationBudget budget=null;int controlledTicks=0;
            for (int tick=0;tick<500;tick++) {
                mode=chooseMode(true,false,false,false,mode,motion.y(),y<=probeDepth(motion.y())?y:Double.NaN,true,fall);
                if (mode==Mode.OFF) motion=new Motion(0,(motion.y()-.08)*.98,0);
                else {
                    motion=step(motion,45,1,0,mode,y,0);
                    controlledTicks++;
                }
                x+=motion.x();y+=motion.y();z+=motion.z();fall+=Math.max(0,-motion.y());
                if (mode!=Mode.OFF) {
                    if (budget==null) {budget=new UnicornLevitationBudget(x,y,z,motion);observedY=y;}
                    else if ((controlledTicks-1)%batch==0) {
                        accept(budget,tick,x,y,z,false,"fluid buffer packets height "+height+" batch "+batch+" tick "+tick);
                        observedY=y;
                    }
                    budget.advance(tick,45,1,0,mode,observedY,0);
                    check(y>=SURFACE_CLEARANCE-1e-8,"packet trajectory retains liquid clearance");
                }
            }
            check(budget!=null&&Math.abs(y-SURFACE_CLEARANCE)<1e-8,"networked liquid fall settles into moving surface hover");
        }
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
