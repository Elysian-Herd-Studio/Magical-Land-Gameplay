package top.elysianherd.magicaland.gameplay.race;

import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

public final class RacePotionItem extends Item {
    private final RaceDefinition race;
    public RacePotionItem(RaceDefinition race, Settings settings) { super(settings); this.race = race; }
    public RaceDefinition race() { return race; }
    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.DRINK; }
    @Override public int getMaxUseTime(ItemStack stack) { return 32; }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (user instanceof ServerPlayerEntity player) {
            String denial = RaceServer.denial(player, race.id(), RacePolicy.ChangeCause.POTION, false);
            if (denial != null) {
                RaceServer.reject(player, denial);
                return TypedActionResult.fail(user.getStackInHand(hand));
            }
        }
        return ItemUsage.consumeHeldItem(world, user, hand);
    }

    @Override public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (user instanceof ServerPlayerEntity player && RaceServer.change(player, race.id(), RacePolicy.ChangeCause.POTION, true)) {
            player.incrementStat(Stats.USED.getOrCreateStat(this));
            if (!player.getAbilities().creativeMode) {
                stack.decrement(1);
                ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                if (stack.isEmpty()) return bottle;
                if (!player.getInventory().insertStack(bottle)) player.dropItem(bottle, false);
            }
        }
        return stack;
    }

    @Override public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("text.magicaland_gameplay.race.potion_hint", Text.translatable(race.translationKey())));
    }
}
