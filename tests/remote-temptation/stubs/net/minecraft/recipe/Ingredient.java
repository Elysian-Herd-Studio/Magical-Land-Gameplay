package net.minecraft.recipe;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;
public final class Ingredient implements Predicate<ItemStack> {
    private final String food;
    public Ingredient(String food) { this.food = food; }
    @Override public boolean test(ItemStack stack) { return stack.item.equals(food); }
}
