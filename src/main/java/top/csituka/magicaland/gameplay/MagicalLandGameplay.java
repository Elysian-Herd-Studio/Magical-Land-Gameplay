package top.csituka.magicaland.gameplay;

import net.fabricmc.api.ModInitializer;
import top.csituka.magicaland.gameplay.easteregg.CarrotMisunderstanding;

public final class MagicalLandGameplay implements ModInitializer {
    @Override
    public void onInitialize() {
        CarrotMisunderstanding.register();
        top.csituka.magicaland.gameplay.remote.RemoteToolServer.register();
    }
}
