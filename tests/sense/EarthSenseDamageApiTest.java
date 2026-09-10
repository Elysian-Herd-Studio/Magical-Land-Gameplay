package top.csituka.magicaland.gameplay.sense;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class EarthSenseDamageApiTest {
    private static int checks;
    public static void main(String[] args)throws Exception {
        var type=new ClassNode();
        try(var stream=EarthSenseDamageApiTest.class.getClassLoader().getResourceAsStream("net/minecraft/entity/LivingEntity.class")) {
            check(stream!=null,"actual mapped Minecraft class exists");new ClassReader(stream).accept(type,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        check(field(method(type,"getLastAttackedTime"),Opcodes.GETFIELD,"lastAttackedTime"),"victim timestamp getter");
        check(field(method(type,"setAttacker"),Opcodes.PUTFIELD,"lastAttackedTime"),"victim attacker setter updates victim timestamp");
        check(field(method(type,"onAttacking"),Opcodes.PUTFIELD,"lastAttackTime"),"active attacker uses separate timestamp");
        check(!field(method(type,"onAttacking"),Opcodes.PUTFIELD,"lastAttackedTime"),"own attack does not update victim timestamp");
        boolean nullCleanup=false;
        for(var method:type.methods)for(var insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.name.equals("setAttacker")) {
            var previous=insn.getPrevious();while(previous!=null&&previous.getOpcode()<0)previous=previous.getPrevious();
            if(previous!=null&&previous.getOpcode()==Opcodes.ACONST_NULL)nullCleanup=true;
        }
        check(nullCleanup,"vanilla clears attacker with setter; focus must guard null attacker");
        System.out.println("PASS EarthSenseDamageApiTest: "+checks+" actual Minecraft victim/attacker bytecode and null-cleanup checks; no game bootstrap");
    }
    private static MethodNode method(ClassNode type,String name){return type.methods.stream().filter(method->method.name.equals(name)).findFirst().orElseThrow();}
    private static boolean field(MethodNode method,int opcode,String name){for(var insn:method.instructions)if(insn instanceof FieldInsnNode field&&field.getOpcode()==opcode&&field.name.equals(name))return true;return false;}
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}
