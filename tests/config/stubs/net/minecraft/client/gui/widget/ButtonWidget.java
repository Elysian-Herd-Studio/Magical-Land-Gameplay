package net.minecraft.client.gui.widget;
import net.minecraft.text.Text;
import net.minecraft.client.gui.tooltip.Tooltip;
public final class ButtonWidget {
    public final int x,y,width,height;
    public int messageChanges;
    public boolean active=true;
    public Tooltip tooltip;
    private Text message;
    private final PressAction action;
    public interface PressAction{void onPress(ButtonWidget button);}
    private ButtonWidget(int x,int y,int width,int height,Text message,PressAction action){
        this.x=x;this.y=y;this.width=width;this.height=height;this.message=message;this.action=action;
    }
    public void onPress(){action.onPress(this);}
    public Text getMessage(){return message;}
    public void setMessage(Text text){message=text;messageChanges++;}
    public void setTooltip(Tooltip value){tooltip=value;}
    public static Builder builder(Text text,PressAction action){return new Builder(text,action);}
    public static final class Builder {
        private final Text message;
        private final PressAction action;
        private int x,y,width,height;
        private Builder(Text text,PressAction action){message=text;this.action=action;}
        public Builder dimensions(int x,int y,int width,int height){
            this.x=x;this.y=y;this.width=width;this.height=height;return this;
        }
        public ButtonWidget build(){return new ButtonWidget(x,y,width,height,message,action);}
    }
}
