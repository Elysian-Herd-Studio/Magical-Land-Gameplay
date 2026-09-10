package top.csituka.magicaland.gameplay.race;

import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.network.PacketByteBuf;

public final class RaceProtocolTest {
    private static int checks;
    private static final String RACE=RaceDefinitions.UNICORN_ID,FUTURE="addon:future_race";
    public static void main(String[] args) {
        var rules=RaceRules.defaults();byte[] canonical=encode(buf->RaceProtocol.writeRules(buf,rules));
        check(decodeRules(canonical).equals(rules),"default roundtrip");
        for(var mode:RaceRules.ChangeMode.values())for(boolean free:new boolean[]{false,true})for(long revision:new long[]{0,1,Long.MAX_VALUE}) {
            var value=new RaceRules(mode,free,Set.of(RACE,FUTURE),revision);check(decodeRules(encode(b->RaceProtocol.writeRules(b,value))).equals(value),"all rule values roundtrip");
        }
        for(int len=0;len<canonical.length;len++)reject(Arrays.copyOf(canonical,len),false,"every rules truncation");
        reject(Arrays.copyOf(canonical,canonical.length+1),false,"rules trailing byte");reject(new byte[16385],false,"rules byte limit");
        for(int mode:new int[]{3,127,255})reject(encode(b->header(b,1,mode,0,1).writeString(RACE)),false,"invalid mode");
        for(int version:new int[]{0,2,Integer.MAX_VALUE,-1})reject(encode(b->header(b,version,0,0,1).writeString(RACE)),false,"invalid version");
        for(int size:new int[]{-1,0,65,Integer.MAX_VALUE})reject(encode(b->header(b,1,0,0,size)),false,"invalid count");
        reject(encode(b->header(b,1,0,-1,1).writeString(RACE)),false,"negative revision");
        reject(encode(b->header(b,1,0,0,2).writeString(RACE).writeString(RACE)),false,"duplicate enabled");
        for(String invalid:new String[]{"","bad","a:","A:race","a:bad space","a:"+"x".repeat(127)})reject(encode(b->header(b,1,0,0,1).writeString(invalid)),false,"invalid or oversized ID");
        reject(encode(b->header(b,1,0,0,1).writeVarInt(513).writeZero(513)),false,"oversized encoded string");
        var enabled=new HashSet<String>();for(int i=0;i<64;i++)enabled.add("addon:r"+i);var maximumRules=new RaceRules(RaceRules.ChangeMode.FREE,true,enabled,123);check(decodeRules(encode(b->RaceProtocol.writeRules(b,maximumRules))).equals(maximumRules),"64 rules boundary");
        var entries=new LinkedHashMap<UUID,String>();entries.put(new UUID(0,1),RACE);entries.put(new UUID(0,2),FUTURE);
        for(String own:new String[]{"",RACE,FUTURE})for(boolean manage:new boolean[]{false,true}) {
            var view=new RaceProtocol.View(rules,own,manage,entries);byte[] bytes=encode(b->RaceProtocol.writeView(b,view));check(decodeView(bytes).equals(view),"own/unknown/admin view roundtrip");
            for(int len=0;len<bytes.length;len++)reject(Arrays.copyOf(bytes,len),true,"every view truncation");reject(Arrays.copyOf(bytes,bytes.length+1),true,"view trailing byte");
        }
        var view=new RaceProtocol.View(rules,"",false,entries);entries.clear();check(view.players().size()==2,"view copies input map");invalid(()->view.players().clear(),"view map immutable");
        for(int size:new int[]{-1,4097,Integer.MAX_VALUE})reject(encode(b->{RaceProtocol.writeRules(b,rules);b.writeString("").writeBoolean(false);b.writeVarInt(size);}),true,"invalid player count");
        reject(encode(b->{RaceProtocol.writeRules(b,rules);b.writeString("").writeBoolean(false);b.writeVarInt(2).writeUuid(new UUID(0,1)).writeString(RACE).writeUuid(new UUID(0,1)).writeString(FUTURE);}),true,"duplicate player UUID");
        for(String bad:new String[]{"bad","a:"+"x".repeat(127)}) {
            reject(encode(b->{RaceProtocol.writeRules(b,rules);b.writeString(bad).writeBoolean(false);b.writeVarInt(0);}),true,"invalid own race");
            reject(encode(b->{RaceProtocol.writeRules(b,rules);b.writeString("").writeBoolean(false);b.writeVarInt(1).writeUuid(new UUID(0,1)).writeString(bad);}),true,"invalid player race");
        }
        reject(new byte[RaceProtocol.MAX_STATE_BYTES+1],true,"state byte limit");
        var maximum=new HashMap<UUID,String>();for(int i=0;i<4096;i++)maximum.put(new UUID(0,i),RACE);var maxView=new RaceProtocol.View(maximumRules,RACE,true,maximum);check(decodeView(encode(b->RaceProtocol.writeView(b,maxView))).equals(maxView),"4096-player boundary roundtrip");maximum.put(new UUID(1,0),RACE);invalid(()->new RaceProtocol.View(rules,"",false,maximum),"view constructor player limit");
        for(long requestId:new long[]{1,2,Long.MAX_VALUE})for(String denial:new String[]{"","stale","potion_required","_".repeat(128)}) {
            var reply=new RaceProtocol.Result(requestId,denial);byte[] bytes=encode(b->RaceProtocol.writeResult(b,reply));
            check(decodeResult(bytes).equals(reply),"request-specific result roundtrip");
            for(int len=0;len<bytes.length;len++)rejectResult(Arrays.copyOf(bytes,len),"every result truncation");
            rejectResult(Arrays.copyOf(bytes,bytes.length+1),"result trailing byte");
        }
        for(long invalidId:new long[]{0,-1,Long.MIN_VALUE}) {
            invalid(()->new RaceProtocol.Result(invalidId,""),"result constructor request ID");
            rejectResult(encode(b->{b.writeLong(invalidId);b.writeString("");}),"result wire request ID");
        }
        for(String invalidDenial:new String[]{"Stale","stale1","bad reason","addon:failure","失败","a".repeat(129)}) {
            invalid(()->new RaceProtocol.Result(1,invalidDenial),"result constructor denial");
            rejectResult(encode(b->{b.writeLong(1);b.writeString(invalidDenial);}),"result wire denial");
        }
        invalid(()->new RaceProtocol.Result(1,null),"null result denial");
        rejectResult(new byte[525],"result byte bound");
        rejectResult(encode(b->{b.writeLong(1);b.writeVarInt(513).writeZero(513);}),"result encoded string bound");
        check(RaceProtocol.RESULT.toString().equals("magicaland_gameplay:race_result_v1"),"separate acknowledgement channel");
        System.out.println("PASS RaceProtocolTest: "+checks+" real PacketByteBuf roundtrips, truncation, duplicate, length/value, request acknowledgement and immutable snapshot checks");
    }
    private static PacketByteBuf header(PacketByteBuf b,int version,int mode,long revision,int size){b.writeVarInt(version).writeByte(mode).writeBoolean(false).writeLong(revision);b.writeVarInt(size);return b;}
    private static byte[] encode(Consumer<PacketByteBuf> writer){var b=new PacketByteBuf(Unpooled.buffer());try{writer.accept(b);byte[] result=new byte[b.readableBytes()];b.readBytes(result);return result;}finally{b.release();}}
    private static RaceRules decodeRules(byte[] data){var b=new PacketByteBuf(Unpooled.wrappedBuffer(data));try{return RaceProtocol.readRules(b);}finally{b.release();}}
    private static RaceProtocol.View decodeView(byte[] data){var b=new PacketByteBuf(Unpooled.wrappedBuffer(data));try{return RaceProtocol.readView(b);}finally{b.release();}}
    private static RaceProtocol.Result decodeResult(byte[] data){var b=new PacketByteBuf(Unpooled.wrappedBuffer(data));try{return RaceProtocol.readResult(b);}finally{b.release();}}
    private static void rejectResult(byte[] data,String label){try{decodeResult(data);throw new AssertionError(label);}catch(RuntimeException expected){checks++;}}
    private static void reject(byte[] data,boolean view,String label){try{if(view)decodeView(data);else decodeRules(data);throw new AssertionError(label);}catch(RuntimeException expected){checks++;}}
    private static void invalid(Runnable run,String label){try{run.run();throw new AssertionError(label);}catch(RuntimeException expected){checks++;}}
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}
