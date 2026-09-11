import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Source boundaries and installed vanilla bytecode, not an in-game animal simulation. */
public final class RemoteAnimalInteractionTest {
    private static final String MAIN="src/main/java/top/csituka/magicaland/gameplay/";
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length!=2) throw new IllegalArgumentException("Minecraft common named JAR, repository directory");
        sourceBoundaries(Path.of(args[1]));
        try (var minecraft=new ZipFile(args[0])) {
            vanillaFeeding(minecraft);
            vanillaTemptation(minecraft);
        }
        System.out.println("PASS RemoteAnimalInteractionTest: "+checks+" source/bytecode checks (not in-game)");
    }

    private static void sourceBoundaries(Path repo) throws Exception {
        String server=source(repo,"remote/RemoteToolServer.java");
        String interact=body(server,"private static void interact(");
        String feed=body(server,"private static boolean canFeed(");
        String tick=body(server,"private static void tick(");
        String stop=body(server,"private static void stop(ServerPlayerEntity player,int reason)");
        check(interact.contains("ItemStackstack=s.cargo.selectedStack()"),"attack reads selected cargo");
        check(interact.contains("booleanmelee=RemoteCombat.canAttack(stack)"),"ordinary nonempty items reach melee gate");
        check(interact.contains("p.getAttackCooldownProgress(0)>=1"),"melee retains attack cooldown");
        ordered(interact,"canAttack(p,tool,target)","AttackEntityCallback.EVENT.invoker().interact(","canStrike(s,target)","p.attack(target)");
        String strike=body(server,"private static boolean canStrike(");
        for (String guard:new String[]{"ACTIVE.get(p.getUuid())!=s","!s.rules.canInteract()","!canReturn(s)",
                "!RemoteCombat.canAttack(s.cargo.selectedStack())","p.getAttackCooldownProgress(0)<1","!canAttack(p,tool,target)",
                "tool.getRotationVec(1).multiply(3)","box.raycast(from,to)"})
            check(strike.contains(guard),"attack callback revalidates "+guard);
        check(interact.contains("from=tool.getEyePos(),to=from.add(tool.getRotationVec(1).multiply(3))"),"tool-origin interaction limited to three blocks");
        check(interact.contains("!loaded(world,newBox(from,to))"),"interaction does not force unloaded chunks");
        check(interact.contains("RaycastContext.ShapeType.OUTLINE") && interact.contains("ProjectileUtil.raycast(tool,from,to"),"block and entity raycasts retained");
        check(interact.contains("instanceofAnimalEntityanimal") && interact.contains("usePressed"),"feeding requires explicit entity use");
        ordered(interact,"if(canFeed(s,animal))","UseEntityCallback.EVENT.invoker().interact(p,world,Hand.MAIN_HAND,animal,victim)",
                "if(result==ActionResult.PASS&&canFeed(s,animal))result=p.interact(animal,Hand.MAIN_HAND)");
        check(count(interact,"canFeed(s,animal)")==2,"eligibility rechecked after event mutation or cancellation");
        check(interact.contains("elseif(usePressed&&victim==null&&hit.getType()==HitResult.Type.BLOCK"),"animal use cannot fall through into a block interaction");
        check(!interact.contains("getInventory()") && !interact.contains("bodyStack") && !interact.contains("equipStack("),"interaction does not swap body inventory");
        check(feed.contains("varfood=s.cargo.selectedStack()"),"callback revalidation rereads current cargo instead of stale stack");
        for (String guard:new String[]{"ACTIVE.get(p.getUuid())!=s","!s.rules.canInteract()","!canReturn(s)",
                "animal.getWorld()!=world","!animal.isAlive()","animal.hasPassengers()","p.shouldCancelInteraction()",
                "food.isEmpty()","!animal.isBreedingItem(food)","!world.canPlayerModifyAt(p,animal.getBlockPos())"})
            check(feed.contains(guard),"feeding rejects "+guard);
        check(feed.contains("from=tool.getEyePos()") && feed.contains("box.raycast(from,from.add(tool.getRotationVec(1).multiply(3)))"),"callback movement must still intersect current three-block gaze");
        check(feed.contains("point!=null&&clearRay(tool,from,point)"),"feeding final segment must remain unobstructed");
        ordered(tick,"try(varcontext=RemoteActionContext.open(s))","if(s.rules.canInteract())","interact(s,now)");
        ordered(stop,"s.rules.close()","ACTIVE.remove(player.getUuid())","s.tool.beginReturn()");

        String context=source(repo,"remote/RemoteActionContext.java");
        check(body(context,"public ItemStack stack()").equals("returnsession.cargo.selectedStack();"),"vanilla receives the actual cargo stack, not a copy");
        check(body(context,"public void setStack(").equals("session.cargo.setStack(session.cargo.selectedSlot(),stack);"),"vanilla replacement goes back to the selected cargo slot");
        check(context.contains("action!=null&&action.player==player?action:null"),"context cannot project another player's inventory");
        check(context.contains("if(previous==null)CURRENT.remove();elseCURRENT.set(previous)"),"scoped context restores nesting and clears thread state");
        check(context.contains("attribute==EntityAttributes.GENERIC_ATTACK_DAMAGE?value*RemoteCombat.damageScale(stack()):value"),"only attack damage receives ordinary-item multiplier");
        String player=source(repo,"mixin/RemotePlayerContextMixin.java");
        check(player.contains("method=\"getEquippedStack\"") && player.contains("slot==EquipmentSlot.MAINHAND)ci.setReturnValue(action.stack())"),"player hand getter redirects only main hand");
        check(player.contains("method=\"equipStack\"") && player.contains("slot==EquipmentSlot.MAINHAND){action.setStack(stack);ci.cancel()"),"main-hand replacement cancels body write");
        String inventory=source(repo,"mixin/RemoteInventoryContextMixin.java");
        check(inventory.contains("method=\"getMainHandStack\"") && inventory.contains("RemoteActionContext.forPlayer(player)")
                && inventory.contains("ci.setReturnValue(action.stack())"),"inventory main-hand accessor also uses owner-scoped cargo");

        String tempting=body(server,"public static RemoteToolEntity temptingTool(");
        String valid=body(server,"public static boolean isTempting(");
        check(tempting.contains("ACTIVE.values()") && !tempting.contains("RETURNS"),"only active projections are considered as food targets");
        check(tempting.contains("next<distance&&isTempting(") && tempting.contains("distance=next"),"nearest eligible projection wins");
        for (String guard:new String[]{"ACTIVE.get(tool.owner())","session.tool==tool","session.rules.canInteract()","canReturn(session)",
                "mob.getWorld()==tool.getWorld()","mob.isAlive()","!mob.hasVehicle()","!tool.returning()",
                "mob.squaredDistanceTo(tool)<100","food.test(session.cargo.selectedStack())","clearRay(tool,mob.getEyePos(),tool.getEyePos())"})
            check(valid.contains(guard),"temptation validates "+guard);
        String goal=source(repo,"mixin/RemoteTemptGoalMixin.java");
        check(goal.contains("@Shadow@FinalprivateIngredientfood") && goal.contains("@Shadow@Finalprivatedoublespeed"),"goal keeps vanilla Ingredient and configured speed");
        check(goal.contains("magicaland$coolingDown=cooldown>0") && goal.contains("if(ci.getReturnValueZ()||magicaland$coolingDown)return"),"vanilla player target and existing cooldown take priority");
        check(goal.contains("RemoteToolServer.temptingTool(mob,food)") && goal.contains("RemoteToolServer.isTempting(magicaland$food,mob,food)"),"start and continuation share the vanilla food predicate");
        String continuation=body(goal,"private void remoteContinue(");
        check(continuation.contains("canBeScared()&&mob.squaredDistanceTo(magicaland$food)<36"),"only vanilla-scared animals check movement within six blocks");
        check(continuation.contains("squaredDistanceTo(magicaland$lastPosition)<=.01") && continuation.contains("magicaland$lastYaw))<=5")
                && continuation.contains("magicaland$lastPitch)<=5"),"vanilla movement and angle scare thresholds preserved");
        check(continuation.contains("if(mob.squaredDistanceTo(magicaland$food)>=36)magicaland$lastPosition=magicaland$food.getPos()")
                && count(continuation,"magicaland$lastPosition=magicaland$food.getPos()")==1 && !continuation.contains("magicaland$remember()"),"nearby gradual movement accumulates against a fixed position, not per-tick deltas");
        ordered(continuation,"getClosestPlayer(predicate,mob)","magicaland$food=null","closestPlayer=player","start()");
        String follow=body(goal,"private void remoteFollow(");
        check(follow.contains("lookAt(magicaland$food,mob.getMaxHeadRotation()+20,mob.getMaxLookPitchChange())"),"vanilla look limits preserved");
        check(follow.contains("squaredDistanceTo(magicaland$food)<6.25") && follow.contains("getNavigation().stop()")
                && follow.contains("getNavigation().startMovingTo(magicaland$food,speed)"),"vanilla follow speed and 2.5-block stopping radius preserved");
        check(goal.contains("@Inject(method=\"stop\",at=@At(\"TAIL\"))") && !body(goal,"private void remoteStop(").contains("cancel"),"vanilla stop and cooldown are not suppressed");
        check(Files.readString(repo.resolve("src/main/resources/magicaland.gameplay.mixins.json")).contains("\"RemoteTemptGoalMixin\""),"temptation hook registered");
    }

    private static void vanillaFeeding(ZipFile minecraft) throws Exception {
        var player=method(minecraft,"entity/player/PlayerEntity","interact","(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;");
        check(call(player,"getStackInHand")>=0 && call(player,"interact")>=0 && call(player,"useOnEntity")>=0,"vanilla player entry routes held item through entity then item use");
        check(call(player,"setStackInHand")>=0,"vanilla depleted-item replacement uses the hand setter");
        noInventoryWrite(player);
        var getHand=method(minecraft,"entity/LivingEntity","getStackInHand",null);
        var setHand=method(minecraft,"entity/LivingEntity","setStackInHand",null);
        check(call(getHand,"getEquippedStack")>=0 && field(getHand,"MAINHAND",Opcodes.GETSTATIC)>=0,"vanilla hand read reaches projected equipment getter");
        check(call(setHand,"equipStack")>=0 && field(setHand,"MAINHAND",Opcodes.GETSTATIC)>=0,"vanilla hand write reaches projected equipment setter");
        var animal=method(minecraft,"entity/passive/AnimalEntity","interactMob",null);
        check(call(animal,"getStackInHand")>=0 && call(animal,"isBreedingItem")>=0,"vanilla animal reevaluates the actual hand food");
        check(call(animal,"eat")>=0 && call(animal,"lovePlayer")>=0 && call(animal,"growUp")>=0,"vanilla adult breeding and baby growth remain the interaction implementation");
        consumeParameter(method(minecraft,"entity/passive/AnimalEntity","eat",null),3);
        consumeParameter(method(minecraft,"entity/passive/AbstractHorseEntity","interactHorse",null),2);
        for (String type:new String[]{"HorseEntity","AbstractDonkeyEntity","CamelEntity"}) {
            var method=method(minecraft,"entity/passive/"+type,"interactMob",null);
            int food=call(method,"isBreedingItem"), feed=call(method,"interactHorse");
            check(food>=0 && feed>food,"vanilla "+type+" recognizes food before horse feeding");
            check(nextReal(method.instructions.get(feed)).getOpcode()==Opcodes.ARETURN,"vanilla "+type+" food branch returns before mounting or storage fallthrough");
        }
        for (String type:new String[]{"PigEntity","StriderEntity"}) {
            var method=method(minecraft,"entity/passive/"+type,"interactMob",null);
            int food=call(method,"isBreedingItem"),ride=call(method,"startRiding");
            check(food>=0 && ride>food,"vanilla "+type+" checks food before its riding branch");
            var saved=nextReal(method.instructions.get(food));
            check(saved instanceof VarInsnNode local && local.getOpcode()==Opcodes.ISTORE,"vanilla "+type+" stores recognized-food boolean");
            int slot=((VarInsnNode)saved).var;
            boolean skipsRide=false;
            for (int i=food+1;i<ride;i++) if (method.instructions.get(i) instanceof VarInsnNode load
                    && load.getOpcode()==Opcodes.ILOAD && load.var==slot
                    && nextReal(load) instanceof JumpInsnNode jump && jump.getOpcode()==Opcodes.IFNE
                    && method.instructions.indexOf(jump.label)>ride) skipsRide=true;
            check(skipsRide,"vanilla "+type+" recognized-food branch skips riding rather than merely preceding it");
        }
    }

    private static void vanillaTemptation(ZipFile minecraft) throws Exception {
        var start=method(minecraft,"entity/ai/goal/TemptGoal","canStart",null);
        check(field(start,"cooldown",Opcodes.GETFIELD)>=0 && field(start,"cooldown",Opcodes.PUTFIELD)>=0,"vanilla start owns cooldown decrement");
        var predicate=method(minecraft,"entity/ai/goal/TemptGoal","isTemptedBy",null);
        check(call(predicate,"test")>=0 && call(predicate,"getMainHandStack")>=0 && call(predicate,"getOffHandStack")>=0,"vanilla Ingredient predicate evaluates held food");
        var continuation=method(minecraft,"entity/ai/goal/TemptGoal","shouldContinue",null);
        check(call(continuation,"canBeScared")>=0 && constant(continuation,36) && constant(continuation,5),"installed vanilla goal uses scared flag, six-block and five-degree thresholds");
        for (String position:new String[]{"lastPlayerX","lastPlayerY","lastPlayerZ"})
            check(field(continuation,position,Opcodes.PUTFIELD)>=0,"installed vanilla goal maintains "+position+" anchor");
        var stop=method(minecraft,"entity/ai/goal/TemptGoal","stop",null);
        check(constant(stop,100) && call(stop,"toGoalTicks")>=0 && field(stop,"cooldown",Opcodes.PUTFIELD)>=0,"installed vanilla stop assigns its 100-tick cooldown");
        check(call(stop,"stop")>=0 && field(stop,"active",Opcodes.PUTFIELD)>=0,"installed vanilla stop halts navigation and clears active flag");
        var tick=method(minecraft,"entity/ai/goal/TemptGoal","tick",null);
        check(constant(tick,6.25) && field(tick,"speed",Opcodes.GETFIELD)>=0 && call(tick,"startMovingTo")>=0,"installed vanilla follow speed and stop distance match projection hook");
    }

    private static void consumeParameter(MethodNode method,int slot) {
        int consume=call(method,"decrement");
        check(consume>=0 && field(method,"creativeMode",Opcodes.GETFIELD)>=0,"vanilla "+method.name+" keeps creative consumption exemption");
        AbstractInsnNode one=previousReal(method.instructions.get(consume)), stack=previousReal(one);
        check(one.getOpcode()==Opcodes.ICONST_1 && stack instanceof VarInsnNode load && load.getOpcode()==Opcodes.ALOAD && load.var==slot,
                "vanilla "+method.name+" decrements exactly one from the supplied live ItemStack");
        noInventoryWrite(method);
    }
    private static void noInventoryWrite(MethodNode method) {
        for (var instruction:method.instructions) if (instruction instanceof MethodInsnNode call)
            check(!call.owner.equals("net/minecraft/entity/player/PlayerInventory") || !call.name.equals("setStack"),"vanilla "+method.name+" does not directly replace body inventory");
    }
    private static String source(Path repo,String file) throws Exception { return Files.readString(repo.resolve(MAIN+file)).replaceAll("\\s+",""); }
    private static String body(String source,String signature) {
        int start=source.indexOf(signature.replaceAll("\\s+",""));
        if (start<0) throw new AssertionError("Missing source method "+signature);
        start=source.indexOf('{',start); int depth=1,end=start+1;
        while (depth>0 && end<source.length()) { char next=source.charAt(end++); if (next=='{') depth++; else if (next=='}') depth--; }
        if (depth!=0) throw new AssertionError("Unclosed source method "+signature);
        return source.substring(start+1,end-1).replaceAll("\\s+","");
    }
    private static int count(String text,String part) { return (text.length()-text.replace(part,"").length())/part.length(); }
    private static void ordered(String text,String... parts) {
        int previous=-1;
        for (String part:parts) { int next=text.indexOf(part,previous+1); check(next>previous,"ordered boundary "+part); previous=next; }
    }
    private static MethodNode method(ZipFile minecraft,String type,String name,String descriptor) throws Exception {
        var entry=minecraft.getEntry("net/minecraft/"+type+".class");
        if (entry==null) throw new AssertionError("Missing vanilla class "+type);
        var node=new ClassNode();
        try (var stream=minecraft.getInputStream(entry)) { new ClassReader(stream).accept(node,0); }
        return node.methods.stream().filter(m->m.name.equals(name) && (descriptor==null || m.desc.equals(descriptor))).findFirst().orElseThrow();
    }
    private static int call(MethodNode method,String name) {
        for (int i=0;i<method.instructions.size();i++) if (method.instructions.get(i) instanceof MethodInsnNode call && call.name.equals(name)) return i;
        return -1;
    }
    private static int field(MethodNode method,String name,int opcode) {
        for (int i=0;i<method.instructions.size();i++) if (method.instructions.get(i) instanceof FieldInsnNode field
                && field.name.equals(name) && field.getOpcode()==opcode) return i;
        return -1;
    }
    private static boolean constant(MethodNode method,double value) {
        for (var instruction:method.instructions) {
            if (instruction instanceof LdcInsnNode constant && constant.cst instanceof Number number && number.doubleValue()==value) return true;
            if (instruction instanceof IntInsnNode constant && constant.operand==value) return true;
        }
        return false;
    }
    private static AbstractInsnNode previousReal(AbstractInsnNode node) { do { node=node.getPrevious(); } while (node!=null && node.getOpcode()<0); return node; }
    private static AbstractInsnNode nextReal(AbstractInsnNode node) { do { node=node.getNext(); } while (node!=null && node.getOpcode()<0); return node; }
    private static void check(boolean value,String label) { checks++; if (!value) throw new AssertionError(label); }
}
