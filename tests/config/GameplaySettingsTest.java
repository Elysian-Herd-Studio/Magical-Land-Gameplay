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
import top.csituka.magicaland.gameplay.config.GameplayClientConfig;
import top.csituka.magicaland.gameplay.MagicalLandGameplay;
import top.csituka.magicaland.gameplay.client.GameplaySettingsScreen;
import top.csituka.magicaland.gameplay.client.ModMenuIntegration;

public final class GameplaySettingsTest {
    private static int checks;
    private static Path root,file,legacy,output;
    private static final String FIELD="automaticAbilityThirdPerson";

    public static void main(String[] args) throws Exception {
        Path repo=Path.of(args[0]);output=Path.of(args[1]);
        scenario("dedicated-server");System.setProperty("magicaland.test.environment","SERVER");
        new MagicalLandGameplay().onInitialize();
        check(!Files.exists(file)&&!Files.exists(file.getParent()),"actual dedicated-server initializer creates no client settings");
        scenario("startup-order");System.setProperty("magicaland.test.environment","CLIENT");
        String startupLegacy="{\"automaticAbilityThirdPerson\":false,\"activeModelName\":\"已选预设\"}";
        write(legacy,startupLegacy);new MagicalLandGameplay().onInitialize();
        persisted(false);
        check(Files.readString(legacy).equals(startupLegacy),"main initialization only reads appearance file");
        write(legacy,"{\"activeModelName\":\"已选预设\"}");
        check(GameplayClientConfig.automaticAbilityThirdPerson()==false,"later appearance client save cannot erase migrated choice");
        persisted(false);
        new MagicalLandGameplay().onInitialize();persisted(false);
        check(read().size()==1,"subsequent startup reads own settings after legacy field vanished");
        scenario("fresh");GameplayClientConfig.load();
        persisted(true);
        check(!Files.exists(legacy),"fresh startup never creates appearance config");
        for(String old:new String[]{"false","true","null","\"false\"","[]","{}"}) {
            scenario("legacy");
            String bytes="{\"automaticAbilityThirdPerson\":"+old+",\"activeModelName\":\"旧预设\"}\n";
            write(legacy,bytes);GameplayClientConfig.load();
            persisted(!old.equals("false"));
            check(Files.readString(legacy).equals(bytes),"migration leaves appearance file byte-for-byte untouched");
            check(read().size()==1,"migration imports no appearance fields");
        }
        scenario("absent-legacy-field");write(legacy,"{\"activeModelName\":\"旧预设\"}");
        GameplayClientConfig.load();persisted(true);
        scenario("malformed-legacy");write(legacy,"invalid-json");GameplayClientConfig.load();persisted(true);
        check(Files.readString(legacy).equals("invalid-json"),"malformed legacy is not rewritten");

        scenario("own-priority");write(legacy,"{\"automaticAbilityThirdPerson\":false}");
        write(file,"{\"futureOption\":{\"name\":\"自有设置\",\"values\":[1,2,3]}}");
        JsonObject other=read();GameplayClientConfig.load();
        check(GameplayClientConfig.automaticAbilityThirdPerson(),"existing own config missing field defaults true, ignores legacy");
        for(boolean enabled:new boolean[]{false,true,false,true}) {
            check(GameplayClientConfig.setAutomaticAbilityThirdPerson(enabled),"atomic setting save succeeds");
            persisted(enabled);
            check(read().get("futureOption").equals(other.get("futureOption")),"unknown own fields preserved");
            GameplayClientConfig.load();persisted(enabled);
        }
        String legacyBefore=Files.readString(legacy);
        var languages=new JsonObject[]{language(repo,"zh_cn"),language(repo,"en_us")};
        var parent=new Screen(Text.translatable("test.parent"));
        var screen=(GameplaySettingsScreen)new ModMenuIntegration().getModConfigScreenFactory().create(parent);
        var client=new MinecraftClient();client.setScreen(screen);screen.testInit(client,400,240);
        check(screen.widgets.size()==5,"gameplay settings has personal view, race, server rules, sense filter and done buttons");
        var view=screen.widgets.get(0);var done=screen.widgets.get(4);
        check(!screen.widgets.get(1).active&&!screen.widgets.get(2).active,"race and server rules are disabled without a server");
        check(view.x==45&&view.width==310&&view.height==20,"bounded centered mode button");
        check(view.tooltip.text().key().endsWith("ability_view.hint"),"mode button explains next-activation behavior");
        label(view,true,languages);
        for(int click=0;click<20;click++) {
            boolean enabled=!GameplayClientConfig.automaticAbilityThirdPerson();
            view.onPress();persisted(enabled);label(view,enabled,languages);
            check(view.messageChanges==click+1,"actual handler updates existing label once");
            check(screen.additions==5&&screen.widgets.get(0)==view&&screen.widgets.get(4)==done,
                    "click saves without rebuilding screen or replacing controls");
            check(client.currentScreen==screen,"mode switch stays in settings");
            check(read().get("futureOption").equals(other.get("futureOption")),"UI save preserves other own fields");
        }
        check(Files.readString(legacy).equals(legacyBefore),"all UI changes leave appearance settings untouched");
        done.onPress();check(client.currentScreen==parent,"done returns to exact parent");
        client.setScreen(screen);screen.close();check(client.currentScreen==parent,"close/Escape returns to exact parent");
        var menuScreen=(GameplaySettingsScreen)new ModMenuIntegration().getModConfigScreenFactory().create(null);
        menuScreen.testInit(client,320,240);client.setScreen(menuScreen);menuScreen.close();
        check(client.currentScreen==null,"null parent closes safely");
        check(menuScreen.widgets.get(0).width==288,"small window keeps mode button in bounds");

        Path saved=file.resolveSibling("saved.json");Files.move(file,saved);Files.createDirectory(file);
        Files.writeString(file.resolve("prevent-replacement"),"test");
        boolean original=GameplayClientConfig.automaticAbilityThirdPerson();view.onPress();
        check(GameplayClientConfig.automaticAbilityThirdPerson()==original,"failed save keeps previous active mode");
        label(view,original,languages);
        check(!Files.exists(file.resolveSibling("client.json.tmp")),"failed save cleans temporary file");
        var draw=new DrawContext();screen.render(draw,0,0,0);
        check(draw.drawn.stream().anyMatch(text->text.key().endsWith("save_failed")),"failed save visibly reported");
        Files.delete(file.resolve("prevent-replacement"));Files.delete(file);Files.move(saved,file);
        view.onPress();persisted(!original);draw=new DrawContext();screen.render(draw,0,0,0);
        check(draw.drawn.stream().noneMatch(text->text.key().endsWith("save_failed")),"successful retry clears error");

        for(String broken:new String[]{"null","[]","invalid-json"}) {
            scenario("invalid-own");write(file,broken);write(legacy,"{\"automaticAbilityThirdPerson\":false}");
            GameplayClientConfig.load();check(GameplayClientConfig.automaticAbilityThirdPerson(),"invalid own configuration safely defaults true");
            check(Files.readString(file).equals(broken),"load does not silently overwrite malformed existing settings");
        }
        System.out.println("PASS GameplaySettingsTest: "+checks+" checks; fixtures: "+output);
    }

    private static void scenario(String name) throws Exception {
        root=Files.createTempDirectory(output,name+"-");
        System.setProperty("magicaland.test.configDir",root.toString());
        file=root.resolve("magicaland-gameplay/client.json");legacy=root.resolve("magicaland/config.json");
    }
    private static void write(Path target,String text) throws Exception {Files.createDirectories(target.getParent());Files.writeString(target,text);}
    private static JsonObject read() throws Exception {return JsonParser.parseString(Files.readString(file)).getAsJsonObject();}
    private static void persisted(boolean value) throws Exception {
        check(GameplayClientConfig.automaticAbilityThirdPerson()==value,"runtime mode matches selected value");
        check(read().get(FIELD).getAsBoolean()==value,"own JSON stores selected mode immediately");
        check(!Files.exists(file.resolveSibling("client.json.tmp")),"atomic-save temporary cleared");
    }
    private static JsonObject language(Path repo,String name) throws Exception {
        return JsonParser.parseString(Files.readString(repo.resolve("src/main/resources/assets/magicaland_gameplay/lang/"+name+".json"))).getAsJsonObject();
    }
    private static void label(ButtonWidget button,boolean value,JsonObject... languages) {
        Text text=button.getMessage();String key="text.magicaland_gameplay.config.ability_view."+(value?"third_person":"body");
        check(text.key().endsWith("ability_view.name")&&text.arguments().length==1,"mode label uses translated template");
        check(text.arguments()[0] instanceof Text mode&&mode.key().equals(key),"mode label chooses correct mode");
        for(var language:languages) {
            String translated=translate(text,language);
            check(translated.contains(language.get(key).getAsString()),"localized mode visible");
            check(!translated.contains("%s")&&!translated.contains("text.magicaland"),"no unresolved translation token");
        }
    }
    private static String translate(Text text,JsonObject language) {
        return String.format(Locale.ROOT,language.get(text.key()).getAsString(),Arrays.stream(text.arguments())
                .map(value->value instanceof Text nested?translate(nested,language):value).toArray());
    }
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
}
