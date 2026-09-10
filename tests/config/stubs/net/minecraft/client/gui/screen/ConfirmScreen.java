package net.minecraft.client.gui.screen;
import java.util.function.Consumer;
import net.minecraft.text.Text;
public class ConfirmScreen extends Screen {
    public final Consumer<Boolean> callback;
    public ConfirmScreen(Consumer<Boolean> callback,Text title,Text message,Text yes,Text no){
        super(title);this.callback=callback;
    }
}
