package top.elysianherd.magicaland.gameplay;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import top.elysianherd.magicaland.gameplay.config.GameplayClientConfig;
import top.elysianherd.magicaland.gameplay.easteregg.CarrotMisunderstanding;

public final class MagicalLandGameplay implements ModInitializer {
    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) GameplayClientConfig.load();
        CarrotMisunderstanding.register();
        top.elysianherd.magicaland.gameplay.remote.TelekinesisToken.register();
        top.elysianherd.magicaland.gameplay.remote.RemoteToolServer.register();
        top.elysianherd.magicaland.gameplay.remote.TelekinesisServer.register();
        top.elysianherd.magicaland.gameplay.race.RaceServer.register();
        top.elysianherd.magicaland.gameplay.sense.EarthSenseServer.register();
        top.elysianherd.magicaland.gameplay.levitation.UnicornLevitationServer.register();
        top.elysianherd.magicaland.gameplay.pegasus.PegasusFlightServer.register();
    }
}
