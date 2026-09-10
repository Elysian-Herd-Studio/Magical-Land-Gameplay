package net.minecraft.text;
public record Text(String key,Object[] arguments) {
    public static Text translatable(String key,Object... arguments){return new Text(key,arguments);}
    public static Text empty(){return translatable("");}
}
