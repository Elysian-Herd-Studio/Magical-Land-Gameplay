package top.csituka.magicaland.gameplay.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class TelekinesisProtocol {
    public static final Identifier CONTROL = new Identifier("magicaland_gameplay", "telekinesis_control_v2");
    public static final Identifier STATE = new Identifier("magicaland_gameplay", "telekinesis_state_v2");
    public enum Task { GUARD, GATHER }
    public enum Mode { AUTO, DIRECT }
    public enum Preference { VALUE, NEAREST, PREFERRED }
    public enum Operation { START, STOP, CONFIG, TARGET }
    public enum Phase { IDLE, TRAVELLING, WORKING, RETURNING, DELIVERING }
    public record Control(long sequence, long taskId, int sourceSlot, Operation operation, Task task, Mode mode,
                          Preference preference, String preferredId, BlockPos block, int entityId) {
        public Control {
            if (sequence < 0 || taskId < 0 || sourceSlot < -1 || sourceSlot > 8 || operation == null || task == null || mode == null || preference == null
                    || !validId(preferredId) || entityId < -1 || block != null && entityId >= 0)
                throw new IllegalArgumentException("Invalid telekinesis control");
            if (block != null) block = block.toImmutable();
        }
    }
    public record TaskView(long id, int sourceSlot, Task task, Mode mode, Preference preference, String preferredId,
                           Phase phase, int toolEntityId, BlockPos targetBlock, int targetEntityId, ItemStack renderStack) {
        public TaskView {
            if (id <= 0 || sourceSlot < 0 || sourceSlot > 8 || task == null || mode == null || preference == null || !validId(preferredId)
                    || phase == null || toolEntityId < 0 || targetEntityId < -1 || renderStack == null)
                throw new IllegalArgumentException("Invalid telekinesis task");
            if (targetBlock != null) targetBlock = targetBlock.toImmutable();
            renderStack = displayStack(renderStack);
        }
        @Override public ItemStack renderStack() { return renderStack.copy(); }
        @Override public boolean equals(Object other) {
            return other instanceof TaskView value && id == value.id && sourceSlot == value.sourceSlot && task == value.task
                    && mode == value.mode && preference == value.preference && preferredId.equals(value.preferredId) && phase == value.phase
                    && toolEntityId == value.toolEntityId && Objects.equals(targetBlock, value.targetBlock) && targetEntityId == value.targetEntityId
                    && ItemStack.areEqual(renderStack, value.renderStack);
        }
        @Override public int hashCode() {
            return Objects.hash(id, sourceSlot, task, mode, preference, preferredId, phase, toolEntityId, targetBlock, targetEntityId,
                    renderStack.getItem(), renderStack.getCount(), renderStack.getNbt());
        }
    }
    public record State(long sequence, long acknowledgedSequence, List<TaskView> tasks, String reason) {
        public State {
            if (sequence < 0 || acknowledgedSequence < -1 || tasks == null || tasks.size() > 16 || reason == null
                    || reason.length() > 32 || !reason.matches("[a-z_]*")) throw new IllegalArgumentException("Invalid telekinesis state");
            tasks = List.copyOf(tasks);
        }
    }
    private TelekinesisProtocol() {}
    private static ItemStack displayStack(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy(); copy.setCount(1);
        var bounded = new PacketByteBuf(Unpooled.buffer(128, 8192));
        try { bounded.writeItemStack(copy); return copy; }
        catch (RuntimeException oversized) {
            var basic = new ItemStack(stack.getItem());
            if (stack.isDamageable()) basic.setDamage(stack.getDamage());
            return basic;
        } finally { bounded.release(); }
    }
    private static boolean validId(String id) { return id != null && id.length() <= 128 && (id.isEmpty() || Identifier.tryParse(id) != null); }
    public static void writeControl(PacketByteBuf buf, Control input) {
        buf.writeVarLong(input.sequence()); buf.writeVarLong(input.taskId()); buf.writeVarInt(input.sourceSlot()); buf.writeEnumConstant(input.operation());
        buf.writeEnumConstant(input.task()); buf.writeEnumConstant(input.mode()); buf.writeEnumConstant(input.preference());
        buf.writeString(input.preferredId(), 128); buf.writeBoolean(input.block() != null);
        if (input.block() != null) buf.writeBlockPos(input.block());
        buf.writeVarInt(input.entityId());
    }
    public static Control readControl(PacketByteBuf buf) {
        if (buf.readableBytes() > 256) throw new IllegalArgumentException("Oversized telekinesis control");
        var value = new Control(buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readEnumConstant(Operation.class),
                buf.readEnumConstant(Task.class), buf.readEnumConstant(Mode.class), buf.readEnumConstant(Preference.class),
                buf.readString(128), buf.readBoolean() ? buf.readBlockPos() : null, buf.readVarInt());
        if (buf.isReadable()) throw new IllegalArgumentException("Trailing telekinesis control");
        return value;
    }
    public static void writeState(PacketByteBuf buf, State state) {
        buf.writeVarLong(state.sequence()); buf.writeVarLong(state.acknowledgedSequence()); buf.writeVarInt(state.tasks().size());
        for (var task : state.tasks()) {
            buf.writeVarLong(task.id()); buf.writeVarInt(task.sourceSlot()); buf.writeEnumConstant(task.task()); buf.writeEnumConstant(task.mode());
            buf.writeEnumConstant(task.preference()); buf.writeString(task.preferredId(), 128); buf.writeEnumConstant(task.phase());
            buf.writeVarInt(task.toolEntityId()); buf.writeBoolean(task.targetBlock() != null);
            if (task.targetBlock() != null) buf.writeBlockPos(task.targetBlock());
            buf.writeVarInt(task.targetEntityId()); buf.writeItemStack(task.renderStack());
        }
        buf.writeString(state.reason(), 32);
    }
    public static State readState(PacketByteBuf buf) {
        if (buf.readableBytes() > 262144) throw new IllegalArgumentException("Oversized telekinesis state");
        long sequence = buf.readVarLong(), acknowledgedSequence = buf.readVarLong(); int count = buf.readVarInt();
        if (count < 0 || count > 16) throw new IllegalArgumentException("Invalid telekinesis count");
        var tasks = new ArrayList<TaskView>(count);
        for (int i = 0; i < count; i++) tasks.add(new TaskView(buf.readVarLong(), buf.readVarInt(), buf.readEnumConstant(Task.class),
                buf.readEnumConstant(Mode.class), buf.readEnumConstant(Preference.class), buf.readString(128),
                buf.readEnumConstant(Phase.class), buf.readVarInt(), buf.readBoolean() ? buf.readBlockPos() : null, buf.readVarInt(), buf.readItemStack()));
        var state = new State(sequence, acknowledgedSequence, tasks, buf.readString(32));
        if (buf.isReadable()) throw new IllegalArgumentException("Trailing telekinesis state");
        return state;
    }
}
