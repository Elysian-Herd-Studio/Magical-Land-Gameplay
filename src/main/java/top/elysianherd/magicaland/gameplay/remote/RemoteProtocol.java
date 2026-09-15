package top.elysianherd.magicaland.gameplay.remote;

import net.minecraft.network.PacketByteBuf;

public final class RemoteProtocol {
    public static final int NORMAL=0, MANUAL=1, LOST=2, INVALID=3, TIMEOUT=4, REJECTED=5;
    public static final int START_BYTES=9, STOP_BYTES=21, INPUT_BYTES=27, DROP_BYTES=19;
    private RemoteProtocol() {}

    public record Control(int operation,long request,long session,int entity,int sequence,
                          float yaw,float pitch,int keys,int selected,int flags) {}

    public static Control readControl(PacketByteBuf buffer) {
        int size=buffer.readableBytes();
        if (size!=START_BYTES && size!=STOP_BYTES && size!=INPUT_BYTES && size!=DROP_BYTES) throw new IllegalArgumentException("packet size");
        int op=buffer.readUnsignedByte();
        Control result=switch (op) {
            case 0 -> new Control(op,buffer.readLong(),0,-1,-1,0,0,0,0,0);
            case 1 -> new Control(op,buffer.readLong(),buffer.readLong(),buffer.readInt(),-1,0,0,0,0,0);
            case 2 -> new Control(op,0,buffer.readLong(),buffer.readInt(),buffer.readInt(),
                    buffer.readFloat(),buffer.readFloat(),buffer.readUnsignedByte(),buffer.readUnsignedByte(),0);
            case 3 -> new Control(op,0,buffer.readLong(),buffer.readInt(),buffer.readInt(),
                    0,0,0,buffer.readUnsignedByte(),buffer.readUnsignedByte());
            default -> throw new IllegalArgumentException("operation");
        };
        if (buffer.isReadable() || !valid(result)) throw new IllegalArgumentException("control");
        return result;
    }

    public static boolean valid(Control c) {
        if (!Float.isFinite(c.yaw) || !Float.isFinite(c.pitch)) return false;
        return switch(c.operation) {
            case 0 -> c.request>0;
            case 1 -> c.request>0 && (c.session==0 && c.entity==-1 || c.session>0 && c.entity>=0);
            case 2 -> c.session>0 && c.entity>=0 && c.sequence>=0 && c.keys>=0 && c.keys<=255
                    && c.selected>=0 && c.selected<RemoteCargoInventory.MAX_SLOTS;
            case 3 -> c.session>0 && c.entity>=0 && c.sequence>=0 && c.selected>=0
                    && c.selected<RemoteCargoInventory.MAX_SLOTS && c.flags>=0 && c.flags<=1;
            default -> false;
        };
    }
}
