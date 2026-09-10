package net.minecraft.client;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.text.Text;
public final class MinecraftClient {
    public final Player player=new Player();
    public static final class Player {
        public record Message(Text text,boolean actionBar){}
        public final List<Message> messages=new ArrayList<>();
        public void sendMessage(Text text,boolean actionBar){messages.add(new Message(text,actionBar));}
    }
}
