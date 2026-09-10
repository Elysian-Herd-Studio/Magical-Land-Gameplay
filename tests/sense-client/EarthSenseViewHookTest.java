package top.csituka.magicaland.gameplay.client.sense;

import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class EarthSenseViewHookTest {
    private static int checks;
    public static void main(String[] args)throws Exception {
        var renderer=load("net/minecraft/client/render/GameRenderer");
        var fov=method(renderer,"getFov","(Lnet/minecraft/client/render/Camera;FZ)D");
        check(fov!=null,"real 1.20.1 getFov exact descriptor");
        int returns=0;for(var node:fov.instructions)if(node.getOpcode()==Opcodes.DRETURN)returns++;
        check(returns>=2,"return injection covers vanilla panorama and normal return (scope skips panorama)");
        boolean worldTrue=false,handFalse=false;
        for(var method:renderer.methods)for(var node:method.instructions)if(node instanceof MethodInsnNode call&&call.name.equals("getFov")&&call.desc.equals(fov.desc)) {
            var previous=previous(node);
            if(method.name.equals("renderWorld")&&previous!=null&&previous.getOpcode()==Opcodes.ICONST_1)worldTrue=true;
            if(method.name.equals("renderHand")&&previous!=null&&previous.getOpcode()==Opcodes.ICONST_0)handFalse=true;
        }
        check(worldTrue&&handFalse,"changingFov distinguishes actual world from hand projection");
        var mouse=method(load("net/minecraft/client/Mouse"),"updateMouse","()V");
        int targetCount=0,target=-1,smooth=-1,spyglass=-1,invert=-1,index=0;
        for(var node:mouse.instructions) {
            if(node instanceof MethodInsnNode call) {
                if(call.owner.equals("net/minecraft/client/network/ClientPlayerEntity")&&call.name.equals("changeLookDirection")&&call.desc.equals("(DD)V")){targetCount++;target=index;}
                if(call.owner.equals("net/minecraft/client/util/SmoothUtil")&&call.name.equals("smooth"))smooth=index;
                if(call.name.equals("isUsingSpyglass"))spyglass=index;
                if(call.name.equals("getInvertYMouse"))invert=index;
            }
            index++;
        }
        check(targetCount==1,"single precise final player mouse call");
        check(smooth>=0&&smooth<target&&spyglass>=0&&spyglass<target&&invert>=0&&invert<target,"hook is after vanilla smoothing, spyglass and inversion");
        Path repo=Path.of(args[0]);String base="src/client/java/top/csituka/magicaland/gameplay/mixin/client/";
        String fovSource=Files.readString(repo.resolve(base+"EarthSenseFovMixin.java"));
        String mouseSource=Files.readString(repo.resolve(base+"EarthSenseMouseMixin.java"));
        for(String guard:new String[]{"!changingFov","isRenderingPanorama()","camera.getFocusedEntity() == client.player","client.getCameraEntity() == client.player","client.currentScreen != null","RemoteToolClient.active()","client.player.isUsingSpyglass()","EarthSenseClient.opacity(delta)"})check(fovSource.contains(guard),"FOV scope "+guard);
        for(String guard:new String[]{"@ModifyArgs","updateMouse()V","ClientPlayerEntity;changeLookDirection(DD)V","client.getCameraEntity() == client.player","client.currentScreen != null","RemoteToolClient.active()","client.mouse.isCursorLocked()","client.isWindowFocused()","client.isPaused()","EarthSenseClient.opacity(client.getTickDelta())"})check(mouseSource.contains(guard),"mouse scope "+guard);
        for(String source:new String[]{fovSource,mouseSource})check(!source.contains(".setValue(")&&!source.contains("options.write(")&&!source.contains("cursorDelta")&&!source.contains("@Redirect"),"no option writes, raw delta mutation or replacing other hooks");
        System.out.println("PASS EarthSenseViewHookTest: "+checks+" actual Minecraft bytecode plus source-scope guards; not a running mixin/game test");
    }
    private static AbstractInsnNode previous(AbstractInsnNode node){var result=node.getPrevious();while(result!=null&&result.getOpcode()<0)result=result.getPrevious();return result;}
    private static MethodNode method(ClassNode type,String name,String descriptor){return type.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(descriptor)).findFirst().orElseThrow();}
    private static ClassNode load(String name)throws Exception{var result=new ClassNode();try(var input=EarthSenseViewHookTest.class.getClassLoader().getResourceAsStream(name+".class")){if(input==null)throw new AssertionError("missing actual class");new ClassReader(input).accept(result,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);}return result;}
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}
