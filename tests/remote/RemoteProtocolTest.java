import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import top.csituka.magicaland.gameplay.remote.RemoteProtocol;
import top.csituka.magicaland.gameplay.remote.RemoteAction;
import top.csituka.magicaland.gameplay.remote.RemoteSessionRules;

public final class RemoteProtocolTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("protocol "+checks); }
    private static PacketByteBuf buffer() { return new PacketByteBuf(Unpooled.buffer()); }
    private static void invalid(PacketByteBuf data) {
        try { RemoteProtocol.readControl(data); throw new AssertionError("invalid accepted"); }
        catch (RuntimeException expected) { checks++; }
        finally { data.release(); }
    }
    public static void main(String[] args) {
        var start=buffer(); start.writeByte(0).writeLong(17);
        var request=RemoteProtocol.readControl(start); check(request.operation()==0 && request.request()==17); start.release();
        var stop=buffer(); stop.writeByte(1).writeLong(17).writeLong(0).writeInt(-1);
        check(RemoteProtocol.readControl(stop).session()==0); stop.release();
        var input=buffer(); input.writeByte(2).writeLong(19).writeInt(5).writeInt(8).writeFloat(900).writeFloat(-100).writeByte(255).writeByte(8);
        var decoded=RemoteProtocol.readControl(input);
        check(decoded.session()==19 && decoded.entity()==5 && decoded.sequence()==8 && decoded.selected()==8 && decoded.keys()==255); input.release();
        check(RemoteProtocol.INPUT_BYTES==27 && RemoteProtocol.DROP_BYTES==19);
        for (int flags=0;flags<=1;flags++) for (int slot=0;slot<9;slot++) {
            var drop=buffer(); drop.writeByte(3).writeLong(19).writeInt(5).writeInt(9).writeByte(slot).writeByte(flags);
            check(drop.readableBytes()==RemoteProtocol.DROP_BYTES);
            var command=RemoteProtocol.readControl(drop);
            check(command.operation()==3 && command.session()==19 && command.entity()==5 && command.sequence()==9);
            check(command.selected()==slot && command.flags()==flags && command.keys()==0 && command.yaw()==0 && command.pitch()==0);
            drop.release();
        }
        for (int flags:new int[]{2,128,255}) {
            var bad=buffer(); bad.writeByte(3).writeLong(19).writeInt(5).writeInt(9).writeByte(0).writeByte(flags); invalid(bad);
        }
        for (int slot:new int[]{9,255}) {
            var bad=buffer(); bad.writeByte(3).writeLong(19).writeInt(5).writeInt(9).writeByte(slot).writeByte(0); invalid(bad);
        }
        for (int invalidField=0;invalidField<3;invalidField++) {
            var bad=buffer(); bad.writeByte(3).writeLong(invalidField==0?0:19).writeInt(invalidField==1?-1:5)
                    .writeInt(invalidField==2?-1:9).writeByte(0).writeByte(1); invalid(bad);
        }
        var truncatedDrop=buffer(); truncatedDrop.writeByte(3).writeLong(19).writeInt(5).writeInt(9).writeByte(0); invalid(truncatedDrop);
        var trailingDrop=buffer(); trailingDrop.writeByte(3).writeLong(19).writeInt(5).writeInt(9).writeByte(0).writeByte(0).writeByte(0); invalid(trailingDrop);
        var ordering=new RemoteSessionRules();
        for (int sequence:new int[]{0,1,2,3}) {
            var message=buffer();
            if (sequence%2==0) message.writeByte(2).writeLong(19).writeInt(5).writeInt(sequence).writeFloat(0).writeFloat(0).writeByte(1).writeByte(0);
            else message.writeByte(3).writeLong(19).writeInt(5).writeInt(sequence).writeByte(0).writeByte(1);
            var control=RemoteProtocol.readControl(message); message.release();
            check(ordering.acceptInput(control.sequence()));
            check(!ordering.acceptInput(control.sequence()) && !ordering.acceptInput(control.sequence()-1));
        }
        for (int length=0;length<80;length++) {
            var bad=buffer(); for (int i=0;i<length;i++) bad.writeByte(255); invalid(bad);
        }
        for (float value:new float[]{Float.NaN,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY}) {
            var bad=buffer(); bad.writeByte(2).writeLong(1).writeInt(2).writeInt(0).writeFloat(value).writeFloat(0).writeByte(0).writeByte(0); invalid(bad);
        }
        for (int selected:new int[]{9,255}) {
            var bad=buffer(); bad.writeByte(2).writeLong(1).writeInt(2).writeInt(0).writeFloat(0).writeFloat(0).writeByte(0).writeByte(selected); invalid(bad);
        }
        var trailing=buffer(); trailing.writeByte(0).writeLong(1).writeByte(1); invalid(trailing);
        var wrong=buffer(); wrong.writeByte(2).writeLong(0).writeInt(2).writeInt(-1).writeFloat(0).writeFloat(0).writeByte(0).writeByte(0); invalid(wrong);
        check(RemoteAction.fromId(-1)==RemoteAction.NONE && RemoteAction.fromId(100)==RemoteAction.NONE);
        for (var action:RemoteAction.values()) check(RemoteAction.fromId(action.ordinal())==action && (action==RemoteAction.NONE || action.durationTicks()>0));
        System.out.println("PASS RemoteProtocolTest: "+checks+" checks");
    }
}
