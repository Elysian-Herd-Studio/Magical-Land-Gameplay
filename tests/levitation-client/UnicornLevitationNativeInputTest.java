package top.csituka.magicaland.gameplay.client.levitation;

import java.io.IOException;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.player.PlayerAbilities;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath.Mode;
import top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath.Motion;

public final class UnicornLevitationNativeInputTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        yes(KeyboardInput.class.getProtectionDomain().getCodeSource().getLocation().toString().contains("minecraft-client"), "execute real cached Minecraft KeyboardInput");
        yes(Input.class.getProtectionDomain().getCodeSource().getLocation().toString().contains("minecraft-client"), "real Input fields, not a copied implementation");
        keyboard(); speeds(); permission(); bytecode();
        System.out.println("UnicornLevitationNativeInputTest: " + checks + " checks PASS (real KeyboardInput execution and real hook bytecode; option-key fixtures only)");
    }
    private static void keyboard() {
        var options = new GameOptions();
        var input = new KeyboardInput(options);
        options.forwardKey.setPressed(true); options.sneakKey.setPressed(true); options.jumpKey.setPressed(true);
        var raw = new UnicornLevitationInput(true,true,true,false,false,false,0);
        input.tick(true,.3f);
        near(input.movementForward,.3,"vanilla crouch factor applied before hook");
        UnicornLevitationNativeInput.apply(input,raw,false,false);
        near(input.movementForward,.3,"prepared without casting retains ordinary crouch");
        yes(input.sneaking && input.jumping,"no blanket crouch/jump suppression outside charging/control");
        input.tick(true,.3f);
        UnicornLevitationNativeInput.apply(input,raw,true,false);
        yes(!input.sneaking && input.jumping,"charging Space+Shift uncrouches but preserves short jump");
        near(input.movementForward,1,"charging does not inherit previous crouch factor");
        for (boolean slowed : new boolean[]{false,true}) for (float factor : new float[]{.3f,.6f,1f}) {
            input.tick(slowed,factor);
            UnicornLevitationNativeInput.apply(input,raw,true,true);
            near(input.movementForward,1,"active raw axis restored regardless old pose/swift-sneak");
            yes(!input.sneaking && !input.jumping,"active magic suppresses native crouch and jump");
            yes(raw.sneak() && raw.space(),"wire/control still sees original held keys");
            input.movementForward *= .2f;
            near(input.movementForward,raw.forwardAxis(true),"item use slowdown exactly once after input hook");
        }
        options.backKey.setPressed(true); options.leftKey.setPressed(true);
        raw = new UnicornLevitationInput(true,true,true,true,true,false,0);
        input.tick(true,.3f); UnicornLevitationNativeInput.apply(input,raw,true,true);
        near(input.movementForward,0,"opposing keys cancel"); near(input.movementSideways,1,"side axis restored too");
        input.tick(true,.3f); UnicornLevitationNativeInput.apply(input,raw,false,false);
        yes(input.sneaking && input.jumping,"release/V exit restores original key behavior next tick");
        near(input.movementSideways,.3,"normal crouch speed restored");
    }
    private static void speeds() {
        for (Mode mode : new Mode[]{Mode.ASCEND,Mode.HOVER,Mode.SURFACE}) for (boolean shift : new boolean[]{false,true}) {
            var raw = new UnicornLevitationInput(true,shift,true,false,false,false,0);
            var velocity = new Motion(0,0,0);
            for (int tick=0;tick<20;tick++) velocity=UnicornLevitationMath.step(velocity,0,raw.forwardAxis(false),0,mode,1,1,shift);
            near(velocity.z(),mode==Mode.ASCEND?.065:.216,"mode-defined horizontal speed, no extra crouch factor: "+mode+" shift="+shift);
            if(mode==Mode.ASCEND)near(velocity.y(),.16,"vertical ascent unchanged");
        }
        var hover=UnicornLevitationMath.step(new Motion(0,.16,0),0,0,0,Mode.HOVER,1,1,true);
        near(hover.y(),.14,"Shift eases vertical speed over multiple frames");
        for(int frame=0;frame<7;frame++) hover=UnicornLevitationMath.step(hover,0,0,0,Mode.HOVER,1,1,true);
        near(hover.y(),0,"Shift holds level after braking");
        var rise=UnicornLevitationMath.step(hover,0,0,0,Mode.ASCEND,1,1,false);
        near(rise.y(),.08,"release Shift resumes rise continuously");
        var use=new Motion(0,0,0);
        for(int tick=0;tick<20;tick++)use=UnicornLevitationMath.step(use,0,.2f,0,Mode.HOVER,1,1,true);
        near(use.z(),.216*.2,"item use slows boosted horizontal speed once");
    }
    private static void permission() {
        var abilities = new PlayerAbilities(); abilities.allowFlying=true; abilities.flying=true;
        float original=abilities.getFlySpeed();
        abilities.flying=false;
        yes(abilities.allowFlying,"leaving native flight preserves Creative permission");
        yes(!UnicornLevitationNativeInput.allowNativeFlight(abilities.allowFlying,true),"prepared/controlled masks only native double-Space read");
        yes(abilities.allowFlying && !abilities.flying,"mask never writes abilities");
        yes(UnicornLevitationNativeInput.allowNativeFlight(abilities.allowFlying,false),"off restores vanilla flight controls without auto-flying");
        yes(!UnicornLevitationNativeInput.allowNativeFlight(false,false),"Survival never gains flight permission");
        near(abilities.getFlySpeed(),original,"fly speed unchanged");
    }
    private static ClassNode read(String name) throws IOException {
        try(var stream=UnicornLevitationNativeInputTest.class.getClassLoader().getResourceAsStream(name+".class")) {
            if(stream==null)throw new AssertionError("Missing actual class "+name);
            var node=new ClassNode(); new ClassReader(stream).accept(node,0); return node;
        }
    }
    private static void bytecode() throws IOException {
        var player=read("net/minecraft/client/network/ClientPlayerEntity");
        yes(player.fields.stream().anyMatch(f->f.name.equals("inSneakingPose")&&f.desc.equals("Z")&&(f.access&Opcodes.ACC_PRIVATE)!=0),"private camera-pose shadow target exists");
        var common=read("net/minecraft/entity/player/PlayerEntity");
        yes(common.fields.stream().anyMatch(f->f.name.equals("abilityResyncCountdown")&&f.desc.equals("I")&&(f.access&Opcodes.ACC_PROTECTED)!=0),"inherited double-Space countdown shadow exists");
        yes(common.methods.stream().anyMatch(m->m.name.equals("travel")&&m.desc.equals("(Lnet/minecraft/util/math/Vec3d;)V")),"actual PlayerEntity.travel target");
        var method=player.methods.stream().filter(m->m.name.equals("tickMovement")&&m.desc.equals("()V")).findFirst().orElseThrow();
        int allow=0,jumpReads=0,base=0,inputTick=-1,pose=-1,autoJump=-1,index=0;
        for(var insn:method.instructions) {
            if(insn instanceof FieldInsnNode field) {
                if(field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals("net/minecraft/entity/player/PlayerAbilities")&&field.name.equals("allowFlying")&&field.desc.equals("Z"))allow++;
                if(field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals("net/minecraft/client/input/Input")&&field.name.equals("jumping"))jumpReads++;
                if(field.getOpcode()==Opcodes.PUTFIELD&&field.name.equals("inSneakingPose"))pose=index;
                if(field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals("net/minecraft/client/input/Input")&&field.name.equals("jumping"))autoJump=index;
            }
            if(insn instanceof MethodInsnNode call) {
                if(call.owner.equals("net/minecraft/client/input/Input")&&call.name.equals("tick")&&call.desc.equals("(ZF)V"))inputTick=index;
                if(call.owner.equals("net/minecraft/client/network/AbstractClientPlayerEntity")&&call.name.equals("tickMovement")&&call.desc.equals("()V")) {
                    yes(call.getOpcode()==Opcodes.INVOKESPECIAL,"pre-travel hook is actual superclass movement");base++;
                }
            }
            index++;
        }
        yes(allow==1,"exactly one native permission read redirect target"); yes(jumpReads>=4,"native jump reads covered including autojump/elytra");
        yes(base==1,"exactly one inherited movement call hook");
        yes(pose>=0&&pose<inputTick,"HEAD uncrouch precedes camera-pose calculation");
        yes(autoJump>inputTick,"post-keyboard autojump needs final pre-travel clear");
        var keyboard=read("net/minecraft/client/input/KeyboardInput");
        yes(keyboard.methods.stream().anyMatch(m->m.name.equals("tick")&&m.desc.equals("(ZF)V")),"real KeyboardInput HEAD/TAIL descriptor");
    }
    private static void near(double actual,double expected,String name){yes(Math.abs(actual-expected)<1e-6,name+" actual="+actual+" expected="+expected);}
    private static void yes(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
}
