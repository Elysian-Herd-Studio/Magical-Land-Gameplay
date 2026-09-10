package net.fabricmc.loader.api;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
public final class FabricLoader {
    private static final FabricLoader INSTANCE=new FabricLoader();
    public static FabricLoader getInstance(){return INSTANCE;}
    public EnvType getEnvironmentType(){return EnvType.valueOf(System.getProperty("magicaland.test.environment","CLIENT"));}
    public Path getConfigDir(){
        String root=System.getProperty("magicaland.test.configDir");
        if(root==null)throw new AssertionError("Isolated test config directory required");
        return Path.of(root);
    }
}
