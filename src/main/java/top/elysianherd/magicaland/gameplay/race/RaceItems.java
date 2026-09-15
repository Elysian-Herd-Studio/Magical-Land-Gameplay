package top.elysianherd.magicaland.gameplay.race;

import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public final class RaceItems {
    private static final Map<String, RacePotionItem> POTIONS = new LinkedHashMap<>();
    private RaceItems() {}
    public static void register() {
        for (var race : RaceDefinitions.all()) {
            var id = new Identifier(race.id());
            String path = id.getPath() + "_race_potion";
            var item = new RacePotionItem(race, new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON));
            Registry.register(Registries.ITEM, new Identifier(id.getNamespace(), path), item);
            POTIONS.put(race.id(), item);
        }
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> POTIONS.values().forEach(entries::add));
    }
}
