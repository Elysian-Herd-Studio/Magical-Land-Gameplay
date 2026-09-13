package top.csituka.magicaland.gameplay.remote;

import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class TelekinesisRules {
    public static final int RANGE = 24, MAX_TASKS = 3, MAX_CHAIN = 32;
    public static final TagKey<net.minecraft.block.Block> RESOURCES = blockTag("telekinesis_resources");
    public static final TagKey<net.minecraft.block.Block> HIGH_VALUE = blockTag("telekinesis_high_value");
    public static final TagKey<net.minecraft.block.Block> MEDIUM_VALUE = blockTag("telekinesis_medium_value");
    private static final TagKey<net.minecraft.block.Block> CHAIN_ORES = blockTag("telekinesis_chain_ores");
    private static final java.util.List<TagKey<net.minecraft.block.Block>> ORE_FAMILIES = java.util.List.of(
            BlockTags.COAL_ORES, BlockTags.IRON_ORES, BlockTags.COPPER_ORES, BlockTags.GOLD_ORES,
            BlockTags.REDSTONE_ORES, BlockTags.LAPIS_ORES, BlockTags.DIAMOND_ORES, BlockTags.EMERALD_ORES);
    private static final TagKey<EntityType<?>> HOSTILE = entityTag("telekinesis_hostile");
    private static final TagKey<EntityType<?>> EXCLUDED = entityTag("telekinesis_excluded");
    record GuardPriority(boolean assigned, boolean threat, boolean current, double health, double distance, java.util.UUID id)
            implements Comparable<GuardPriority> {
        @Override public int compareTo(GuardPriority other) {
            int result = Boolean.compare(other.threat, threat);
            if (result == 0) result = Boolean.compare(assigned, other.assigned);
            if (result == 0) result = Boolean.compare(other.current, current);
            if (result == 0) result = Double.compare(health, other.health);
            if (result == 0) result = Double.compare(distance, other.distance);
            return result == 0 ? id.compareTo(other.id) : result;
        }
    }
    private TelekinesisRules() {}
    private static TagKey<net.minecraft.block.Block> blockTag(String path) { return TagKey.of(RegistryKeys.BLOCK, new Identifier("magicaland_gameplay", path)); }
    private static TagKey<EntityType<?>> entityTag(String path) { return TagKey.of(RegistryKeys.ENTITY_TYPE, new Identifier("magicaland_gameplay", path)); }
    public static boolean supports(TelekinesisProtocol.Task task, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return task == TelekinesisProtocol.Task.GUARD ? item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem
                : item instanceof MiningToolItem || item instanceof ShearsItem;
    }
    public static boolean supports(ItemStack stack) {
        return supports(TelekinesisProtocol.Task.GUARD, stack) || supports(TelekinesisProtocol.Task.GATHER, stack);
    }
    public static TelekinesisProtocol.Task automaticTask(ItemStack stack) {
        return supports(TelekinesisProtocol.Task.GUARD, stack) ? TelekinesisProtocol.Task.GUARD : null;
    }
    public static boolean needsRepair(ItemStack stack) {
        return stack != null && stack.isDamageable()
                && stack.getMaxDamage() - stack.getDamage() <= Math.max(2, (int) Math.ceil(stack.getMaxDamage() * .02));
    }
    public static boolean directTarget(PlayerEntity viewer, Entity entity) {
        if (!(entity instanceof LivingEntity target) || target == viewer || !target.isAlive() || target.isRemoved()
                || target.isSpectator() || !target.canHit() || target.isInvisible() || target.isInvisibleTo(viewer)) return false;
        if (target instanceof PlayerEntity other && (!viewer.shouldDamagePlayer(other)
                || viewer.getServer() != null && !viewer.getServer().isPvpEnabled())) return false;
        var team = viewer.getScoreboardTeam();
        return !viewer.isTeammate(target) || team == null || team.isFriendlyFireAllowed();
    }
    public static boolean canGather(ItemStack stack, BlockState state, boolean automatic) {
        if (automatic || !supports(TelekinesisProtocol.Task.GATHER, stack) || state.isAir() || state.hasBlockEntity()) return false;
        if (state.getBlock() instanceof CropBlock crop) return stack.getItem() instanceof HoeItem && crop.isMature(state);
        return stack.isSuitableFor(state) || !state.isToolRequired() && stack.getMiningSpeedMultiplier(state) > 1;
    }
    public static boolean assistTarget(PlayerEntity viewer, Entity entity) {
        return entity instanceof LivingEntity target && directTarget(viewer, target) && !protectedAlly(viewer, target);
    }
    private static boolean protectedAlly(PlayerEntity viewer, LivingEntity target) {
        return target == viewer || target.isTeammate(viewer)
                || target instanceof TameableEntity pet && viewer.getUuid().equals(pet.getOwnerUuid())
                || target instanceof AbstractHorseEntity horse && horse.isTame() && viewer.getUuid().equals(horse.getOwnerUuid());
    }
    public static boolean chainable(BlockState state) {
        return !state.isAir() && !state.hasBlockEntity() && (state.isIn(BlockTags.LOGS) || state.isIn(CHAIN_ORES));
    }
    public static boolean sameChain(BlockState first, BlockState next) {
        if (!chainable(first) || !chainable(next)) return false;
        if (first.isOf(next.getBlock())) return true;
        if (first.isIn(BlockTags.LOGS) || next.isIn(BlockTags.LOGS)) return false;
        for (var family : ORE_FAMILIES) if (first.isIn(family) && next.isIn(family)) return true;
        return false;
    }
    public static boolean threatens(PlayerEntity viewer, LivingEntity target) {
        if (TelekinesisThreats.recent(viewer, target)) return true;
        if (!(target instanceof MobEntity mob)) return false;
        var targetMemory = mob.getBrain().getOptionalMemory(MemoryModuleType.ATTACK_TARGET);
        return mob.getTarget() == viewer || targetMemory != null && targetMemory.orElse(null) == viewer;
    }
    public static boolean enemy(PlayerEntity viewer, Entity entity) {
        if (!(entity instanceof LivingEntity target) || target == viewer || target instanceof PlayerEntity
                || !target.isAlive() || target.isRemoved() || target.isSpectator() || !target.canHit()
                || target.isInvisible() || target.isInvisibleTo(viewer) || protectedAlly(viewer, target)) return false;
        if (threatens(viewer, target)) return true;
        if (target instanceof TameableEntity || target instanceof Angerable || target.getType().isIn(EXCLUDED)) return false;
        EntityType<?> type = target.getType();
        if (type == EntityType.PIGLIN || type == EntityType.ENDERMAN || type == EntityType.ZOMBIFIED_PIGLIN) return false;
        if (type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER) return false;
        return type.isIn(HOSTILE) || target instanceof Monster && "minecraft".equals(Registries.ENTITY_TYPE.getId(type).getNamespace());
    }
    public static int value(BlockState state) { return state.isIn(HIGH_VALUE) ? 3 : state.isIn(MEDIUM_VALUE) ? 2 : 1; }
}
