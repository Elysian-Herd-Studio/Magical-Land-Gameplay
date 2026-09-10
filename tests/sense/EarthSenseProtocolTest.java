package top.csituka.magicaland.gameplay.sense;

import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.network.PacketByteBuf;

public final class EarthSenseProtocolTest {
    private static int checks;
    public static void main(String[] args) {
        check(EarthSenseProtocol.CONTROL.getPath().endsWith("_v2")&&EarthSenseProtocol.STATE.getPath().endsWith("_v2"),"new independent channels");
        for(long token:new long[]{1,2,Long.MAX_VALUE})for(boolean enabled:new boolean[]{false,true}) {
            var value=new EarthSenseProtocol.Control(token,enabled);byte[] bytes=encode(b->EarthSenseProtocol.writeControl(b,value));
            check(bytes.length==10&&readControl(bytes).equals(value),"canonical exact-length control");
            for(int len=0;len<bytes.length;len++)invalidControl(Arrays.copyOf(bytes,len));invalidControl(Arrays.copyOf(bytes,bytes.length+1));
            bytes[9]=2;invalidControl(bytes);
        }
        for(long token:new long[]{0,-1,Long.MIN_VALUE})invalidControl(encode(b->b.writeByte(2).writeLong(token).writeBoolean(true)));
        for(int version:new int[]{0,1,3,255})invalidControl(encode(b->b.writeByte(version).writeLong(1).writeBoolean(true)));
        var maximum=new ArrayList<EarthSenseProtocol.Signal>();for(int id=1;id<=32;id++)maximum.add(signal(id));
        for(long token:new long[]{1,Long.MAX_VALUE})for(int sequence:new int[]{0,1,Integer.MAX_VALUE})for(var signals:List.of(List.<EarthSenseProtocol.Signal>of(),maximum)) {
            var value=new EarthSenseProtocol.State(token,sequence,"minecraft:overworld",true,true,"",signals);
            byte[] bytes=encode(b->EarthSenseProtocol.writeState(b,value));check(bytes.length<=2048&&readState(bytes).equals(value),"32 individual target roundtrip");
            for(int len=0;len<bytes.length;len++)invalidState(Arrays.copyOf(bytes,len));invalidState(Arrays.copyOf(bytes,bytes.length+1));
        }
        for(boolean active:new boolean[]{false,true}) {
            var state=new EarthSenseProtocol.State(1,1,"addon:world/path",active,false,"airborne",List.of());
            check(readState(encode(b->EarthSenseProtocol.writeState(b,state))).equals(state),"offground/closed empty state");
        }
        var last=new EarthSenseProtocol.State(1,1,"a:b",true,true,"",List.of(signal(65535)));
        check(readState(encode(b->EarthSenseProtocol.writeState(b,last))).equals(last),"maximum session short ID");
        var source=new ArrayList<>(maximum);var snapshot=new EarthSenseProtocol.State(1,1,"a:b",true,true,"",source);source.clear();check(snapshot.signals().size()==32,"immutable state copy");invalid(()->snapshot.signals().clear());
        invalid(()->new EarthSenseProtocol.State(1,1,"a:b",true,true,"",List.of(signal(1),signal(1))));
        var extra=new ArrayList<>(maximum);extra.add(signal(33));invalid(()->new EarthSenseProtocol.State(1,1,"a:b",true,true,"",extra));
        for(boolean active:new boolean[]{false,true})invalid(()->new EarthSenseProtocol.State(1,1,"a:b",active,false,"",List.of(signal(1))));
        invalid(()->new EarthSenseProtocol.State(1,1,"a:b",false,true,"",List.of()));
        for(String dimension:new String[]{"","unqualified","UPPER:a","a:","a:"+"x".repeat(127)})invalid(()->new EarthSenseProtocol.State(1,1,dimension,false,false,"",List.of()));
        for(String reason:new String[]{"bad error","Bad","error1","x".repeat(33)})invalid(()->new EarthSenseProtocol.State(1,1,"a:b",false,false,reason,List.of()));
        invalid(()->new EarthSenseProtocol.State(0,1,"a:b",false,false,"",List.of()));invalid(()->new EarthSenseProtocol.State(1,-1,"a:b",false,false,"",List.of()));
        for(int id:new int[]{0,-1,65536,Integer.MAX_VALUE}) {
            invalid(()->signal(id));invalidState(packet(b->wireSignal(b,id,0,0,64,0,1,2,.5f,1,0)));
        }
        for(int kind:new int[]{-1,4,255}) {
            invalid(()->new EarthSenseProtocol.Signal(1,kind,0,64,0,1,2,.5f,1,0));
            invalidState(packet(b->wireSignal(b,1,kind,0,64,0,1,2,.5f,1,0)));
        }
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,32000000.25,-32000000.25}) {
            for(int axis=0;axis<3;axis++) {
                double x=axis==0?value:0,y=axis==1?value:64,z=axis==2?value:0;
                invalid(()->new EarthSenseProtocol.Signal(1,0,x,y,z,1,2,.5f,1,0));
                invalidState(packet(b->wireSignal(b,1,0,x,y,z,1,2,.5f,1,0)));
            }
        }
        for(float width:new float[]{0,.249f,8.001f,Float.NaN,Float.POSITIVE_INFINITY}) {
            invalid(()->new EarthSenseProtocol.Signal(1,0,0,64,0,width,2,.5f,1,0));
            invalidState(packet(b->wireSignal(b,1,0,0,64,0,width,2,.5f,1,0)));
        }
        for(float height:new float[]{0,.249f,12.001f,Float.NaN,Float.POSITIVE_INFINITY}) {
            invalid(()->new EarthSenseProtocol.Signal(1,0,0,64,0,1,height,.5f,1,0));
            invalidState(packet(b->wireSignal(b,1,0,0,64,0,1,height,.5f,1,0)));
        }
        for(float activity:new float[]{-.001f,1.001f,Float.NaN,Float.POSITIVE_INFINITY}) {
            invalid(()->new EarthSenseProtocol.Signal(1,0,0,64,0,1,2,activity,1,0));
            invalidState(packet(b->wireSignal(b,1,0,0,64,0,1,2,activity,1,0)));
        }
        for(int strength:new int[]{0,5,255})invalidState(packet(b->wireSignal(b,1,0,0,64,0,1,2,.5f,strength,0)));
        for(int pulse:new int[]{-1,256})invalid(()->new EarthSenseProtocol.Signal(1,0,0,64,0,1,2,.5f,1,pulse));
        invalidState(new byte[2049]);
        for(int size:new int[]{-1,33,Integer.MAX_VALUE})invalidState(encode(b->{header(b,2,1,1,1,1,"a:b","");b.writeVarInt(size);}));
        invalidState(encode(b->{header(b,2,1,1,1,1,"a:b","");b.writeVarInt(2);wireSignal(b,1,0,0,64,0,1,2,.5f,1,0);wireSignal(b,1,1,2,64,0,1,2,.5f,1,0);}));
        for(int[] bad:new int[][]{{0,1,1,1,1},{1,1,1,1,1},{3,1,1,1,1},{2,0,1,1,1},{2,1,-1,1,1},{2,1,1,2,1},{2,1,1,1,2},{2,1,1,0,1}})invalidState(encode(b->{header(b,bad[0],bad[1],bad[2],bad[3],bad[4],"a:b","");b.writeVarInt(0);}));
        check(Arrays.stream(EarthSenseProtocol.Signal.class.getRecordComponents()).map(c->c.getName()).toList().equals(List.of("id","kind","x","y","z","width","height","activity","strength","pulse")),"wire only anonymous short ID and coarse blob fields");
        check(Arrays.stream(EarthSenseProtocol.Signal.class.getRecordComponents()).noneMatch(c->c.getType()==UUID.class),"no UUID on wire");
        var unquantized=new EarthSenseProtocol.State(1,1,"a:b",true,true,"",List.of(new EarthSenseProtocol.Signal(1,0,.123,64.444,.888,.51f,1.66f,.123f,1,0)));
        check(readState(encode(b->EarthSenseProtocol.writeState(b,unquantized))).equals(unquantized),"wire finite/range checks do not silently change values; server builder separately quantizes");
        System.out.println("PASS EarthSenseProtocolTest: "+checks+" v2 real buffers, bounds, truncations, finite coordinates/sizes, anonymous IDs and duplicate target checks");
    }
    private static EarthSenseProtocol.Signal signal(int id){return new EarthSenseProtocol.Signal(id,0,-.25,64.5,1.75,.75f,1.75f,.65f,3,255);}
    private static void header(PacketByteBuf b,int version,long token,int sequence,int active,int grounded,String dimension,String reason){b.writeByte(version).writeLong(token);b.writeVarInt(sequence).writeString(dimension).writeByte(active).writeByte(grounded);b.writeString(reason);}
    private static void wireSignal(PacketByteBuf b,int id,int kind,double x,double y,double z,float width,float height,float activity,int strength,int pulse){b.writeVarInt(id).writeByte(kind).writeDouble(x).writeDouble(y).writeDouble(z).writeFloat(width).writeFloat(height).writeFloat(activity).writeByte(strength).writeByte(pulse);}
    private static byte[] packet(Consumer<PacketByteBuf> writer){return encode(b->{header(b,2,1,1,1,1,"a:b","");b.writeVarInt(1);writer.accept(b);});}
    private static byte[] encode(Consumer<PacketByteBuf> writer){var b=new PacketByteBuf(Unpooled.buffer());try{writer.accept(b);byte[] out=new byte[b.readableBytes()];b.readBytes(out);return out;}finally{b.release();}}
    private static EarthSenseProtocol.Control readControl(byte[] bytes){var b=new PacketByteBuf(Unpooled.wrappedBuffer(bytes));try{return EarthSenseProtocol.readControl(b);}finally{b.release();}}
    private static EarthSenseProtocol.State readState(byte[] bytes){var b=new PacketByteBuf(Unpooled.wrappedBuffer(bytes));try{return EarthSenseProtocol.readState(b);}finally{b.release();}}
    private static void invalidControl(byte[] bytes){invalid(()->readControl(bytes));}
    private static void invalidState(byte[] bytes){invalid(()->readState(bytes));}
    private static void invalid(Runnable task){try{task.run();throw new AssertionError("invalid input accepted");}catch(RuntimeException expected){checks++;}}
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}
