package top.csituka.magicaland.gameplay.easteregg;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

public final class CarrotTriggerFoodTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        for (Item item : Registries.ITEM)
            check(CarrotMisunderstanding.triggerFood(item) == (item == Items.GOLDEN_CARROT),
                    "only golden carrot is accepted: " + Registries.ITEM.getId(item));
        check(!CarrotMisunderstanding.triggerFood(null), "missing food cannot trigger");

        UUID player = new UUID(1, 1), horse = new UUID(2, 1);
        Item[] foods = {Items.GOLDEN_CARROT, Items.CARROT, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
                Items.APPLE, Items.WHEAT, Items.SUGAR, Items.AIR};
        for (Item fed : foods) for (Item eaten : foods) {
            var state = new CarrotMisunderstandingState();
            if (CarrotMisunderstanding.triggerFood(fed)) state.fed(player, horse, "test", 100);
            if (CarrotMisunderstanding.triggerFood(eaten)) state.ate(player,"test",110);
            var match = state.takeMatch(player,"test",110);
            check((match != null) == (fed == Items.GOLDEN_CARROT && eaten == Items.GOLDEN_CARROT),
                    "both feeding and eating must use golden carrot");
        }

        String source = Files.readString(Path.of(args[0],
                "gameplay/src/main/java/top/csituka/magicaland/gameplay/easteregg/CarrotMisunderstanding.java"));
        String feedRoute = source.substring(source.indexOf("public static void recordFeeding"),
                source.indexOf("public static void carrotFinished"));
        String eatRoute = source.substring(source.indexOf("public static void carrotFinished"),
                source.indexOf("static boolean triggerFood"));
        check(feedRoute.contains("!triggerFood(food.getItem())")
                && feedRoute.indexOf("!triggerFood(food.getItem())") < feedRoute.indexOf("STATE.fed("),
                "real feeding route applies the filter before recording");
        check(eatRoute.contains("if (!triggerFood(food)) return;")
                && eatRoute.indexOf("if (!triggerFood(food)) return;") < eatRoute.indexOf("STATE.ate("),
                "real eating route applies the same filter before consuming a record");
        check(feedRoute.contains("!horse.isInLove()") && feedRoute.contains("horse.getLovingPlayer() != player"),
                "successful original breeding feed and feeder association remain required");
        check(feedRoute.contains("tryStart(player)") && eatRoute.contains("tryStart(player)")
                && eatRoute.contains("STATE.takeMatch("),"both food orders use the same match path");
        check(source.contains("if (damaged) horse.playAngrySound();"),"rear only after successful damage via vanilla horse reaction");
        System.out.println("PASS CarrotTriggerFoodTest: " + checks + " exact-food and route checks");
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
