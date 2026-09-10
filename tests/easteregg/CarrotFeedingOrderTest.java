package top.csituka.magicaland.gameplay.easteregg;

import java.util.UUID;

public final class CarrotFeedingOrderTest {
    private static int checks;
    private static void check(boolean result) { checks++; if (!result) throw new AssertionError("check "+checks); }
    public static void main(String[] args) {
        UUID player=UUID.randomUUID(),horse=UUID.randomUUID(),other=UUID.randomUUID();
        String world="minecraft:overworld";
        for (boolean playerFirst : new boolean[] {true,false}) for (int gap=0;gap<=302;gap++) {
            var state=new CarrotMisunderstandingState();
            if (playerFirst) state.ate(player,world,10); else state.fed(player,horse,world,10);
            check(state.takeMatch(player,world,10)==null);
            if (playerFirst) state.fed(player,horse,world,10+gap); else state.ate(player,world,10+gap);
            var match=state.takeMatch(player,world,10+gap);
            check((match!=null)==(gap<=300));
            check(state.takeMatch(player,world,10+gap)==null);
            if (match!=null) {
                check(state.claim(player,match,10+gap));
                state.release(player);
                state.ate(player,world,11+gap); state.fed(player,horse,world,11+gap);
                check(state.takeMatch(player,world,11+gap)==null);
            }
        }
        var state=new CarrotMisunderstandingState();
        state.ate(player,world,0); state.fed(other,horse,world,1);
        check(state.takeMatch(other,world,1)==null);
        state.fed(player,horse,"minecraft:the_nether",2);
        check(state.takeMatch(player,"minecraft:the_nether",2)==null);
        state.release(player); state.fed(player,horse,world,3);
        check(state.takeMatch(player,world,3)==null);
        state.ate(player,world,4); state.prune(5,(id,dim)->false);
        state.fed(player,horse,world,6); check(state.takeMatch(player,world,6)==null);
        state.ate(player,world,7); state.clear();
        state.fed(player,horse,world,8); check(state.takeMatch(player,world,8)==null);
        System.out.println("PASS CarrotFeedingOrderTest: "+checks+" checks");
    }
}
