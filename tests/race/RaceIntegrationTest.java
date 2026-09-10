package top.csituka.magicaland.gameplay.race;

import java.nio.file.Files;
import java.nio.file.Path;

/** 源码接入守卫，不等同于真实 ServerPlayer/网络事件集成测试。 */
public final class RaceIntegrationTest {
    private static int checks;
    public static void main(String[] args)throws Exception {
        Path repo=Path.of(args[1]);String prefix="src/main/java/top/csituka/magicaland/gameplay/";
        String server=read(repo,prefix+"race/RaceServer.java"),potion=read(repo,prefix+"race/RacePotionItem.java");
        has(read(repo,prefix+"MagicalLandGameplay.java"),"RaceServer.register();","common initialization");
        has(server,"RaceItems.register();","items initialized");
        String request=between(server,"registerGlobalReceiver(RaceProtocol.REQUEST","registerGlobalReceiver(RaceProtocol.CHOOSE");
        has(request,"buf.isReadable() || !allowPacket(player.getUuid())","empty bounded request");has(request,"server.execute","request server thread");has(request,"connected(player)","request connection identity");
        String choose=between(server,"registerGlobalReceiver(RaceProtocol.CHOOSE","registerGlobalReceiver(RaceProtocol.RULES");
        has(choose,"buf.readableBytes() > 536","choose byte bound");has(choose,"buf.readString(128)","choose string bound");has(choose,"if (buf.isReadable()) return;","choose trailing bytes");has(choose,"!allowPacket(player.getUuid())","choose rate limit");
        before(choose,"long requestId = buf.readLong()","buf.readString(128)","choose request ID prefix");before(choose,"if (requestId <= 0) return;","server.execute","positive choose request ID");
        before(choose,"server.execute","revision != RaceState.get(server).rules().revision()","choose checks current revision on server thread");before(choose,"if (!connected(player)) return;","tryChange(player, target, RacePolicy.ChangeCause.SELECT, false)","stale connection cannot select");has(choose,"catch (RuntimeException ignored)","bad choose contained");has(choose,"result(player, requestId, denial)","choice success and refusal acknowledge exact request");
        String editRules=between(server,"registerGlobalReceiver(RaceProtocol.RULES","ServerPlayConnectionEvents.JOIN");
        has(editRules,"RaceProtocol.readRules(buf)","actual strict rules parser");has(editRules,"RacePolicy.rulesDenial(canManage(player), state.rules(), requested)","real permission/revision policy");before(editRules,"server.execute","RacePolicy.rulesDenial","rules authorization on server thread");before(editRules,"if (denial != null) { reject(player, denial); result(player, requestId, denial); return; }","state.rules(requested.nextRevision())","no write before authorization");
        before(editRules,"long requestId = buf.readLong()","RaceProtocol.readRules(buf)","rules request ID prefix");before(editRules,"if (requestId <= 0) return;","server.execute","positive rules request ID");before(editRules,"state.rules(requested.nextRevision())","result(player, requestId, null)","rules success reply after mutation");
        String manage=method(server,"public static boolean canManage");has(manage,"hasPermissionLevel(2)","operator level two");has(manage,"server.isSingleplayer() && server.isHost(player.getGameProfile())","singleplayer host exception scoped");has(server,".requires(source -> source.hasPermissionLevel(2))","admin command permission");
        has(server,"argument(\"race\", IdentifierArgumentType.identifier())","command accepts namespaced race ID");has(server,"IdentifierArgumentType.getIdentifier(context, \"race\").toString()","command uses full parsed ID");
        String safe=method(server,"public static boolean safeToChange");for(String guard:new String[]{"player.isAlive()","!player.isSpectator()","player.isOnGround()","!player.hasVehicle()","!player.isSleeping()","!player.getAbilities().flying","!player.isTouchingWater()","!player.isInLava()","player.hurtTime == 0","!RemoteToolServer.active(player)","drinking || !player.isUsingItem()","player.currentScreenHandler == player.playerScreenHandler"})has(safe,guard,"safe stance "+guard);
        String use=method(potion,"public TypedActionResult<ItemStack> use");has(use,"user instanceof ServerPlayerEntity","potion start server authority");has(use,"RaceServer.denial(player, race.id(), RacePolicy.ChangeCause.POTION, false)","first potion check");before(use,"TypedActionResult.fail","ItemUsage.consumeHeldItem","rejected start does not begin drinking");
        String finish=method(potion,"public ItemStack finishUsing");has(finish,"user instanceof ServerPlayerEntity player && RaceServer.change(player, race.id(), RacePolicy.ChangeCause.POTION, true)","finish rechecks actual server change");before(finish,"RaceServer.change","stack.decrement(1)","successful change required before consumption");has(finish,"if (!player.getAbilities().creativeMode)","creative no consumption");has(finish,"if (stack.isEmpty()) return bottle;","empty stack returns bottle");has(finish,"if (!player.getInventory().insertStack(bottle)) player.dropItem(bottle, false)","overflow bottle preserved");has(finish,"return stack;","failure retains potion");
        String wrapper=method(server,"public static boolean change");has(wrapper,"tryChange(player, target, cause, drinking)","potion wrapper shares actual mutation");has(wrapper,"return denial == null;","potion wrapper reports success");
        String change=method(server,"private static String tryChange");before(change,"String denial = denial(player, target, cause, drinking)","RaceState.get(player.getServer()).race(player.getUuid(), target)","second policy/stance check before persistence");before(change,"if (denial != null) return denial;","RemoteToolServer.stop(player)","rejected change has no session mutation");has(change,"RaceDefinitions.UNICORN_ID.equals(target)","unicorn identity drives grant");has(change,"else player.removeScoreboardTag(RemoteToolServer.GRANT)","leaving unicorn revokes grant");
        String result=method(server,"private static void result");has(result,"ServerPlayNetworking.canSend(player, RaceProtocol.RESULT)","reply channel support check");has(result,"new RaceProtocol.Result(requestId, denial == null ? \"\" : denial)","reply preserves request ID and error");
        has(server,"ServerPlayConnectionEvents.JOIN.register","join sync");has(server,"ServerPlayerEvents.AFTER_RESPAWN.register","respawn sync");has(server,"ServerPlayerEvents.COPY_FROM.register","respawn grant copy");has(server,"REQUESTS.remove(handler.player.getUuid())","disconnect throttle cleanup");has(server,"PERMISSIONS.remove(handler.player.getUuid())","disconnect permission cleanup");has(server,"REQUESTS.clear(); PERMISSIONS.clear();","shutdown cleanup");has(server,"if (old == null || old != allowed) sync(player)","permission changes resync");
        String send=method(server,"private static void send");has(send,"if (!ServerPlayNetworking.canSend(player, RaceProtocol.STATE)) return;","old clients not sent unknown packet");has(send,"canManage(player)","server-derived management flag");
        String remote=read(repo,prefix+"remote/RemoteToolServer.java");has(remote,"RaceServer.isUnicorn(player)","remote ability entry race check");has(remote,"RaceServer.isUnicorn(p)","remote active session race check");
        String clientPrefix="src/client/java/top/csituka/magicaland/gameplay/client/race/";
        String client=read(repo,clientPrefix+"RaceClient.java");has(client,"registerGlobalReceiver(RaceProtocol.RESULT","dedicated client result receiver");has(client,"RaceProtocol.readResult(buffer)","strict result parser used");has(client,"RESULTS.put(incoming.requestId(), incoming)","responses indexed by request ID");has(client,"RESULTS.size() > 64","bounded reply cache");
        String clear=method(client,"private static void clear");has(clear,"RESULTS.clear();","disconnect drops old acknowledgements");
        for(String screen:new String[]{"RaceSelectionScreen.java","RaceRulesScreen.java"}) {
            String source=read(repo,clientPrefix+screen),tick=method(source,"public void tick");
            has(source,"requestId = RaceClient.lastRequestId()","screen records own request ID");has(tick,"RaceClient.result(requestId)","screen reads only matching acknowledgement");has(tick,"reply != null && !reply.denial().isEmpty()","failure requires explicit negative acknowledgement");
            check(!tick.contains("RaceClient.text(\"not_applied\")"),"unrelated state update is not a failure acknowledgement");has(tick,"System.nanoTime() >= deadline","missing acknowledgement eventually times out");
        }
        System.out.println("PASS RaceIntegrationTest: "+checks+" SOURCE-STRUCTURE guards; not a live network, item-use or server integration test");
    }
    private static String read(Path repo,String file)throws Exception{return Files.readString(repo.resolve(file)).replaceAll("\\s+"," ");}
    private static String between(String text,String first,String next){int start=text.indexOf(first),end=text.indexOf(next,start+first.length());check(start>=0&&end>start,"source anchors");return text.substring(start,end);}
    private static String method(String text,String signature){int start=text.indexOf(signature);check(start>=0,"method exists "+signature);int open=text.indexOf('{',start),depth=1,end=open+1;while(depth>0&&end<text.length()){char c=text.charAt(end++);if(c=='{')depth++;if(c=='}')depth--;}check(depth==0,"method balanced");return text.substring(open,end);}
    private static void has(String text,String needle,String label){check(text.contains(needle),label);}
    private static void before(String text,String first,String second,String label){int a=text.indexOf(first),b=text.indexOf(second);check(a>=0&&b>a,label);}
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}
