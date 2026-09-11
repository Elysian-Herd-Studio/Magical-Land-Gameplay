import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

/** Initializes Fabric's normal named-class access repair, never the game loop. */
public final class LevitationKnotBootstrap {
    public static void main(String[] args) throws Throwable {
        ClassLoader loader = new Knot(EnvType.CLIENT).init(new String[0]);
        Class<?> test = Class.forName("top.csituka.magicaland.gameplay.levitation.UnicornLevitationPhysicsTest", true, loader);
        if (test.getClassLoader() != loader) throw new AssertionError("Test escaped Knot");
        try { test.getMethod("main", String[].class).invoke(null, (Object) args); }
        catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
    }
}
