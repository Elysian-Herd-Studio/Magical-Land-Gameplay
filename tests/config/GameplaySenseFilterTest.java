import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.csituka.magicaland.gameplay.client.GameplaySettingsScreen;
import top.csituka.magicaland.gameplay.client.EarthSenseSettingsScreen;
import top.csituka.magicaland.gameplay.config.GameplayClientConfig;

public final class GameplaySenseFilterTest {
    private static final String FILTER="earthSenseFilterStrength",VIEW="automaticAbilityThirdPerson";
    private static int checks;
    private static Path output,file,legacy;
    public static void main(String[] args) throws Exception {
        Path repo=Path.of(args[0]);output=Path.of(args[1]);
        JsonObject[] languages={language(repo,"en_us"),language(repo,"zh_cn")};
        scenario("default");GameplayClientConfig.load();
        close(GameplayClientConfig.earthSenseFilterStrength(),.8f,"fresh setting defaults to standard strength");
        close(GameplayClientConfig.earthSenseBlurStrength(),.35f,"fresh blur is subtle");
        close(GameplayClientConfig.earthSenseAudioStrength(),.6f,"fresh muffling is moderate");
        check(!read().has(FILTER),"default getter adds no unnecessary persisted field");

        scenario("legacy-no-import");String legacyBytes="{\"automaticAbilityThirdPerson\":false,\"earthSenseFilterStrength\":0.4,\"future\":{\"value\":17}}\n";
        write(legacy,legacyBytes);GameplayClientConfig.load();
        close(GameplayClientConfig.earthSenseFilterStrength(),.8f,"appearance filter field is not imported");
        check(!GameplayClientConfig.automaticAbilityThirdPerson(),"existing view migration is preserved");
        check(!read().has(FILTER)&&Files.readString(legacy).equals(legacyBytes),"legacy bytes remain untouched and only view migrates");

        for(String value:new String[]{"null","true","false","\"0.4\"","[]","{}","1e309","-1e309"}) {
            scenario("invalid-filter");String bytes="{\"earthSenseFilterStrength\":"+value+",\"future\":[1,2]}";
            write(file,bytes);GameplayClientConfig.load();
            close(GameplayClientConfig.earthSenseFilterStrength(),.8f,"invalid or nonfinite filter safely defaults: "+value);
            check(Files.readString(file).equals(bytes),"invalid filter is not silently rewritten on read");
        }
        String[] values={"-2","-0.1","0","0.19","0.2","0.4","0.6","0.8","1","1.5"};
        float[] expected={0,0,0,.19f,.2f,.4f,.6f,.8f,1,1};
        for(int i=0;i<values.length;i++) {
            scenario("numeric-filter");String bytes="{\"earthSenseFilterStrength\":"+values[i]+",\"automaticAbilityThirdPerson\":false}";
            write(file,bytes);GameplayClientConfig.load();
            close(GameplayClientConfig.earthSenseFilterStrength(),expected[i],"finite numeric strength is clamped into range");
            check(!GameplayClientConfig.automaticAbilityThirdPerson(),"filter parsing leaves view preference unchanged");
            check(Files.readString(file).equals(bytes),"clamped read leaves source bytes unchanged");
        }

        scenario("old-own-file");String oldOwn="{\"automaticAbilityThirdPerson\":false,\"future\":{\"name\":\"保留\",\"list\":[1,true,null]}}\n";
        write(file,oldOwn);write(legacy,legacyBytes);GameplayClientConfig.load();
        close(GameplayClientConfig.earthSenseFilterStrength(),.8f,"old own config gains default without migration");
        check(Files.readString(file).equals(oldOwn),"opening old own config is read only");
        var other=read().get("future").deepCopy();
        for(float value:new float[]{0,.4f,.8f,1,.125f}) {
            check(GameplayClientConfig.setEarthSenseFilterStrength(value),"valid filter save succeeds");
            persisted(value);GameplayClientConfig.load();persisted(value);
            check(read().get("future").equals(other),"filter save preserves unknown structured fields");
            check(!GameplayClientConfig.automaticAbilityThirdPerson()&&!read().get(VIEW).getAsBoolean(),"filter save preserves view setting");
            check(Files.readString(legacy).equals(legacyBytes),"filter save leaves appearance config untouched");
        }
        String beforeInvalid=Files.readString(file);float beforeValue=GameplayClientConfig.earthSenseFilterStrength();
        for(float value:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-.01f,1.01f,Float.MAX_VALUE}) {
            check(!GameplayClientConfig.setEarthSenseFilterStrength(value),"invalid setter value is rejected");
            close(GameplayClientConfig.earthSenseFilterStrength(),beforeValue,"invalid setter preserves active strength");
            check(Files.readString(file).equals(beforeInvalid),"invalid setter preserves persisted bytes");
        }

        var client=new MinecraftClient();var parent=new Screen(Text.empty());
        GameplayClientConfig.setEarthSenseFilterStrength(.8f);
        var main=new GameplaySettingsScreen(parent);main.testInit(client,400,240);client.setScreen(main);
        check(main.widgets.size()==5,"main settings keeps one entry for sensory effects");
        main.widgets.get(3).onPress();
        check(client.currentScreen instanceof EarthSenseSettingsScreen,"sensory entry opens dedicated effect options");
        var screen=(EarthSenseSettingsScreen)client.currentScreen;screen.testInit(client,400,240);
        var filter=screen.widgets.get(0);var view=main.widgets.get(0);var done=screen.widgets.get(3);
        check(filter.tooltip.text().key().equals("text.magicaland_gameplay.sense.filter.hint"),"filter explains cosmetic-only effect");
        for(float value:new float[]{0,.4f,.8f,0,.4f,.8f}) {
            filter.onPress();persisted(value);label(filter,value,languages);
            check(screen.widgets.get(0)==filter&&screen.additions==4,"filter saves without rebuilding controls");
            check(client.currentScreen==screen,"filter click stays in gameplay settings");
        }
        view.onPress();persisted(.8f);check(read().get("future").equals(other),"view toggle preserves filter and unknown fields");
        done.onPress();check(client.currentScreen==main,"done returns to exact gameplay settings parent after filter edits");
        main.close();check(client.currentScreen==parent,"gameplay settings still returns to original parent");

        for(int option=1;option<=2;option++) {
            float normal=option==1?.35f:.6f;
            String field=option==1?"earthSenseBlurStrength":"earthSenseAudioStrength";
            for(float value:new float[]{0,normal*.5f,normal}) {
                screen.widgets.get(option).onPress();
                close(option==1?GameplayClientConfig.earthSenseBlurStrength():GameplayClientConfig.earthSenseAudioStrength(),value,"independent sensory option cycle");
                close(read().get(field).getAsFloat(),value,"independent sensory option persisted");
                persisted(.8f);check(read().get("future").equals(other),"sensory option preserves unrelated values");
            }
            String unchanged=Files.readString(file);
            for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,2}) {
                check(!(option==1?GameplayClientConfig.setEarthSenseBlurStrength(invalid):GameplayClientConfig.setEarthSenseAudioStrength(invalid)),"invalid sensory value rejected");
                check(Files.readString(file).equals(unchanged),"invalid sensory value leaves bytes intact");
            }
        }

        for(int[] size:new int[][]{{320,240},{400,240},{640,360},{854,480}}) {
            var current=new EarthSenseSettingsScreen(parent);current.testInit(client,size[0],size[1]);
            layout(current,languages,false);
            Path backup=file.resolveSibling("saved-filter.json");Files.move(file,backup);Files.createDirectory(file);
            Files.writeString(file.resolve("prevent-replacement"),"test");
            float original=GameplayClientConfig.earthSenseFilterStrength();current.widgets.get(0).onPress();
            close(GameplayClientConfig.earthSenseFilterStrength(),original,"failed UI save retains previous filter strength");
            label(current.widgets.get(0),original,languages);
            check(!Files.exists(file.resolveSibling("client.json.tmp")),"failed filter save cleans temporary file");
            layout(current,languages,true);
            check(Files.readString(legacy).equals(legacyBytes),"failed filter save does not change legacy config");
            Files.delete(file.resolve("prevent-replacement"));Files.delete(file);Files.move(backup,file);
            current.widgets.get(0).onPress();
            float next=original<.2f?.4f:original<.6f?.8f:0;persisted(next);
            layout(current,languages,false);
        }
        System.out.println("PASS GameplaySenseFilterTest: "+checks+" persistence, interaction and conservative layout checks");
    }
    private static void layout(Screen screen,JsonObject[] languages,boolean failed) {
        for(int i=0;i<screen.widgets.size();i++) {
            var a=screen.widgets.get(i);
            check(a.x>=0&&a.y>=0&&a.x+a.width<=screen.width&&a.y+a.height<=screen.height,"control inside window");
            for(int j=i+1;j<screen.widgets.size();j++) {var b=screen.widgets.get(j);check(!overlap(a.x,a.y,a.width,a.height,b.x,b.y,b.width,b.height),"settings controls do not overlap");}
        }
        var draw=new DrawContext();screen.render(draw,0,0,0);
        check(draw.drawn.stream().anyMatch(t->t.key().endsWith("save_failed"))==failed,"save failure visibility matches transaction result");
        for(var language:languages)for(var call:draw.calls) {
            String text=translate(call.text(),language);int w=call.wrapped()?call.width():textWidth(text);
            int h=(call.wrapped()?lines(text,w):1)*9,x=call.centered()?call.x()-w/2:call.x();
            check(x>=0&&call.y()>=0&&x+w<=screen.width&&call.y()+h<=screen.height,"localized text remains in viewport");
            for(var button:screen.widgets)check(!overlap(x,call.y(),w,h,button.x,button.y,button.width,button.height),"localized help/error does not overlap controls");
            for(var other:draw.calls)if(call!=other) {
                String otherText=translate(other.text(),language);int ow=other.wrapped()?other.width():textWidth(otherText);
                int oh=(other.wrapped()?lines(otherText,ow):1)*9,ox=other.centered()?other.x()-ow/2:other.x();
                check(!overlap(x,call.y(),w,h,ox,other.y(),ow,oh),"localized help and error never overlap each other");
            }
        }
    }
    private static boolean overlap(int ax,int ay,int aw,int ah,int bx,int by,int bw,int bh){return ax<bx+bw&&bx<ax+aw&&ay<by+bh&&by<ay+ah;}
    private static int textWidth(String text){return text.codePoints().map(c->c>127?9:c==' '?4:6).sum();}
    private static int lines(String text,int width){
        var matcher=java.util.regex.Pattern.compile("[\\x21-\\x7e]+| |[^\\x00-\\x7f]").matcher(text);int result=1,used=0;
        while(matcher.find()){String token=matcher.group();int next=textWidth(token);if(used>0&&used+next>width){result++;used=token.equals(" ")?0:next;}else used+=next;}
        return result;
    }
    private static void label(ButtonWidget button,float value,JsonObject[] languages){
        String level=value<.2f?"off":value<.6f?"reduced":"normal";
        check(button.getMessage().key().equals("text.magicaland_gameplay.sense.filter"),"filter uses translated template");
        check(((Text)button.getMessage().arguments()[0]).key().endsWith("filter."+level),"filter label matches retained active strength");
        for(var language:languages){String rendered=translate(button.getMessage(),language);check(!rendered.contains("%s")&&!rendered.contains("text.magicaland"),"filter label resolves in both languages");}
    }
    private static void scenario(String name)throws Exception{Path root=Files.createTempDirectory(output,"sense-filter-"+name+"-");System.setProperty("magicaland.test.configDir",root.toString());file=root.resolve("magicaland-gameplay/client.json");legacy=root.resolve("magicaland/config.json");}
    private static void write(Path path,String text)throws Exception{Files.createDirectories(path.getParent());Files.writeString(path,text);}
    private static JsonObject read()throws Exception{return JsonParser.parseString(Files.readString(file)).getAsJsonObject();}
    private static JsonObject language(Path repo,String name)throws Exception{return JsonParser.parseString(Files.readString(repo.resolve("src/main/resources/assets/magicaland_gameplay/lang/"+name+".json"))).getAsJsonObject();}
    private static String translate(Text text,JsonObject language){return String.format(Locale.ROOT,language.get(text.key()).getAsString(),Arrays.stream(text.arguments()).map(value->value instanceof Text nested?translate(nested,language):value).toArray());}
    private static void persisted(float value)throws Exception{close(GameplayClientConfig.earthSenseFilterStrength(),value,"active filter matches saved value");close(read().get(FILTER).getAsFloat(),value,"own JSON stores filter strength");check(!Files.exists(file.resolveSibling("client.json.tmp")),"successful save leaves no temporary file");}
    private static void close(float value,float expected,String message){check(Math.abs(value-expected)<.00001f,message);}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
