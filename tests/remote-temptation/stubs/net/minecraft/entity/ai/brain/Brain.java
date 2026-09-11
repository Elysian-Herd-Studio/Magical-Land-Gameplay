package net.minecraft.entity.ai.brain;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
public final class Brain {
    private final Map<MemoryModuleType<?>, Object> memories = new HashMap<>();
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOptionalRegisteredMemory(MemoryModuleType<T> type) {
        return Optional.ofNullable((T) memories.get(type));
    }
    public <T> void remember(MemoryModuleType<T> type, T value) { memories.put(type, value); }
    public void forget(MemoryModuleType<?> type) { memories.remove(type); }
}
