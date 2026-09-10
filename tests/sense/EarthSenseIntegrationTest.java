package top.csituka.magicaland.gameplay.sense;

import java.nio.file.Files;
import java.nio.file.Path;

/** 只检查源码接入，不声称运行过真实服务器或多人游戏。 */
public final class EarthSenseIntegrationTest {
    private static int checks;
    public static void main(String[] args)throws Exception {
        Path repo=Path.of(args[1]);String base="src/main/java/top/csituka/magicaland/gameplay/";
        String server=Files.readString(repo.resolve(base+"sense/EarthSenseServer.java")).replaceAll("\\s+"," ");
        for(String guard:new String[]{"EarthSenseProtocol.readControl(buf)","PENDING.compute(player.getUuid()", "!control.enabled()", "ServerTickEvents.END_SERVER_TICK.register", "connected(pending.player)", "EarthSenseLease.Action.IGNORE", "state.rules.expired(tick)", "ACTIVE.size() >= EarthSenseRules.MAX_ACTIVE", "RaceDefinitions.EARTH_PONY_ID.equals", "RemoteToolServer.active(player)", "!player.isAlive()", "player.isSpectator()", "player.hasVehicle()", "player.getAbilities().flying", "player.isTouchingWater()", "session.dimension.equals(dimension(player))", "session.focus.denial(", "session.focus.ready(tick)", "player.getLastAttackedTime()", "player.getAbsorptionAmount()", "tick % EarthSenseRules.SAMPLE_TICKS", "collectEntitiesByType(TypeFilter.instanceOf(LivingEntity.class)", "nearby, EarthSenseRules.MAX_CANDIDATES", "EarthSenseGround.search(origin, grid, EarthSenseRules.MAX_NODES, EarthSenseRules.MAX_PROBES)", "entity.isRemoved()", "entity.isSpectator()", "entity.hasVehicle()", "entity.isOnGround()", "entity.isSneaking()", "session.signals.update(connected, tick, range)", "reach.contains(candidate.support)", "bounds.getCenter()", "state.getFluidState().isEmpty()", "EarthSenseGround.touches(a, from, b, to)", "cache.size() >= budget", "ServerLivingEntityEvents.AFTER_DEATH", "ServerPlayConnectionEvents.DISCONNECT", "LEASES.remove(id)", "SERVER_STOPPED", "PENDING.clear()"})check(server.contains(guard),"guard "+guard);
        check(server.indexOf("world.isChunkLoaded(pos)")<server.indexOf("world.getBlockState(pos)"),"loaded check before block access");
        check(!server.contains("world.getChunk("),"no explicit chunk loading");
        check(!server.contains("writeUuid(")&&!server.contains("entity.getId()"),"no actual entity IDs or UUID payload");
        check(!server.contains("setPosition(")&&!server.contains("setVelocity(")&&!server.contains("setNoGravity("),"focus never teleports or suppresses real knockback/gravity");
        check(!server.contains("stableMotion(")&&!server.contains("\"moved\""),"no moving-start gate or movement cancellation");
        check(server.contains("session.motion.observe(player.getX(), player.getZ(), tick)"),"viewer speed uses server-observed positions every tick");
        check(server.contains("EarthSenseViewerMotion.range(session.motion.quality())")&&server.contains("expand(range, 3, range)")&&server.contains("if (distance > range) continue;"),"server narrows both candidate query and exact distance");
        check(server.contains("public static double viewerQuality(ServerPlayerEntity player)"),"server session quality accessible without a new packet");
        check(!server.contains("AIR_GRACE_TICKS")&&!server.contains("airTicks"),"focus airborne cancellation no legacy grace");
        check(server.indexOf("if (!session.focus.ready(tick)) return;")<server.indexOf("if (sampling) sample(session"),"no target sample before stable warmup");
        check(!server.contains("canSee("),"wall propagation is not a visual ray test");
        check(!server.contains("!entity.isInvisible()"),"invisible stepping remains physically detectable");
        String initializer=Files.readString(repo.resolve(base+"MagicalLandGameplay.java"));check(initializer.contains("EarthSenseServer.register()"),"common initialized");
        System.out.println("PASS EarthSenseIntegrationTest: "+checks+" SOURCE guards only; not live world/event integration");
    }
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}
