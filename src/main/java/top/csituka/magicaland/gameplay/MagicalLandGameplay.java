package top.csituka.magicaland.gameplay;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import top.csituka.magicaland.gameplay.config.GameplayClientConfig;
import top.csituka.magicaland.gameplay.easteregg.CarrotMisunderstanding;

public final class MagicalLandGameplay implements ModInitializer {
    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) GameplayClientConfig.load();
        CarrotMisunderstanding.register();
        top.csituka.magicaland.gameplay.remote.RemoteToolServer.register();
    }
}
