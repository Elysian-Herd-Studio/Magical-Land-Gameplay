package net.minecraft.client.gui.tooltip;
import net.minecraft.text.Text;
public record Tooltip(Text text){public static Tooltip of(Text text){return new Tooltip(text);}}
