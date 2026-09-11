import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import top.csituka.magicaland.gameplay.remote.RemoteReturnNavigator;
import top.csituka.magicaland.gameplay.remote.RemoteReturnNavigator.Point;
import top.csituka.magicaland.gameplay.remote.RemoteReturnNavigator.Status;

public final class RemoteReturnNavigatorTest {
    private static int checks;
    private static void check(boolean condition,String message) {
        checks++; if (!condition) throw new AssertionError("return "+checks+": "+message);
    }
    private record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {}
    private static final class World implements RemoteReturnNavigator.Collision {
        final List<Box> walls=new ArrayList<>();
        double minX=-200,maxX=200,minY=-.1,maxY=.1,minZ=-200,maxZ=200;
        int calls;
        World wall(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
            walls.add(new Box(minX,minY,minZ,maxX,maxY,maxZ)); return this;
        }
        @Override public boolean clear(Point from,Point to) {
            calls++;
            if (!inside(from) || !inside(to)) return false;
            for (Box wall:walls) if (intersects(from,to,wall)) return false;
            return true;
        }
        private boolean inside(Point point) {
            return point.x()>=minX && point.x()<=maxX && point.y()>=minY && point.y()<=maxY
                    && point.z()>=minZ && point.z()<=maxZ;
        }
        private static boolean intersects(Point from,Point to,Box wall) {
            double low=0,high=1;
            double[] start={from.x(),from.y(),from.z()},end={to.x(),to.y(),to.z()};
            double[] min={wall.minX-.15,wall.minY-.15,wall.minZ-.15};
            double[] max={wall.maxX+.15,wall.maxY+.15,wall.maxZ+.15};
            for (int axis=0;axis<3;axis++) {
                double delta=end[axis]-start[axis];
                if (Math.abs(delta)<1e-12) { if (start[axis]<min[axis] || start[axis]>max[axis]) return false; }
                else {
                    double a=(min[axis]-start[axis])/delta,b=(max[axis]-start[axis])/delta;
                    low=Math.max(low,Math.min(a,b)); high=Math.min(high,Math.max(a,b));
                    if (low>high) return false;
                }
            }
            return true;
        }
    }
    private record Run(Point end,int ticks,int expanded,double highestY) {}
    private static Point p(double x,double z) { return new Point(x,0,z); }
    private static Point advance(Point from,Point to,double speed) {
        double distance=from.distanceTo(to),scale=distance==0?0:Math.min(speed,distance)/distance;
        return new Point(from.x()+(to.x()-from.x())*scale,from.y()+(to.y()-from.y())*scale,
                from.z()+(to.z()-from.z())*scale);
    }
    private static Run run(RemoteReturnNavigator navigation,World world,Point start,IntFunction<Point> goal,int ticks) {
        Point position=start; int expanded=0; double highestY=Math.abs(position.y());
        for (int tick=0;tick<ticks;tick++) {
            Point target=goal.apply(tick); world.calls=0;
            var step=navigation.next(position,target); int calls=world.calls;
            check(step.expandedNodes()>=0 && step.expandedNodes()<=RemoteReturnNavigator.EXPANSIONS_PER_TICK,"bounded node work per tick");
            check(calls<=3500,"bounded collision queries per tick: "+calls);
            check(navigation.searchNodeCount()<=RemoteReturnNavigator.MAX_SEARCH_NODES,"bounded search storage");
            expanded+=step.expandedNodes();
            if (step.status()==Status.ARRIVED) {
                check(position.distanceTo(target)<=RemoteReturnNavigator.ARRIVAL_DISTANCE,"arrives at current target tolerance");
                check(world.clear(position,target),"arrival has a clear final segment");
                return new Run(position,tick,expanded,highestY);
            }
            if (step.status()==Status.MOVING) {
                check(step.waypoint()!=null && world.clear(position,step.waypoint()),"every emitted waypoint has a clear 0.3-box sweep");
                Point moved=advance(position,step.waypoint(),.45);
                check(position.distanceTo(moved)<=.450000001 && world.clear(position,moved),"bounded actual movement cannot cut corners");
                position=moved; highestY=Math.max(highestY,Math.abs(position.y()));
            } else check(step.waypoint()==null,"search and blockage never yield a teleport destination");
        }
        throw new AssertionError("did not arrive in "+ticks+" ticks from "+start+"; ended "+position);
    }
    private static World wallWorld() { return new World().wall(3,-2,-2,5,2,2); }
    private static void trail(RemoteReturnNavigator navigation) {
        for (int z=0;z<=6;z++) navigation.record(p(0,z*.5));
        for (int x=1;x<=16;x++) navigation.record(p(x*.5,3));
        for (int z=5;z>=0;z--) navigation.record(p(8,z*.5));
    }
    public static void main(String[] args) {
        openSpace(); breadcrumbs(); wallsAndDoors(); changingWorld(); sealedAndBudgets(); sharedBudget(); movingTargetAndCache(); inputs();
        System.out.println("PASS RemoteReturnNavigatorTest: "+checks+" checks");
    }
    private static void openSpace() {
        var world=new World(); var navigation=new RemoteReturnNavigator(world);
        var result=run(navigation,world,p(128,0),tick->p(0,0),400);
        check(result.expanded==0 && result.ticks>=282 && result.ticks<=285,"128-block open return needs no grid search");
        var close=navigation.next(p(.69,0),p(0,0));
        check(close.status()==Status.ARRIVED,"clear 0.69 final segment arrives");
        check(navigation.next(p(.71,0),p(0,0)).status()==Status.MOVING,"0.71 remains moving");
    }
    private static void breadcrumbs() {
        var world=wallWorld(); var navigation=new RemoteReturnNavigator(world); trail(navigation);
        var result=run(navigation,world,p(8,0),tick->p(0,0),300);
        check(result.expanded==0,"a reachable recorded route returns without A star");
        navigation=new RemoteReturnNavigator(world);
        for (int i=0;i<RemoteReturnNavigator.MAX_BREADCRUMBS;i++) navigation.record(p(100+i,100));
        trail(navigation);
        result=run(navigation,world,p(8,0),tick->p(0,0),300);
        check(result.expanded==0,"wrapped breadcrumb storage preserves newest route order");
    }
    private static void wallsAndDoors() {
        var wall=wallWorld();
        var result=run(new RemoteReturnNavigator(wall),wall,p(8,0),tick->p(0,0),500);
        check(result.expanded>0,"unrecorded wall uses local search");
        var cup=new World().wall(1,-2,-3,2,2,3).wall(1,-2,3,5,2,4).wall(1,-2,-4,5,2,-3);
        result=run(new RemoteReturnNavigator(cup),cup,p(3,0),tick->p(0,0),500);
        check(result.expanded>0,"U-shaped obstacle allows an initially outward detour");
        var door=new World().wall(2,-2,-5,3,2,-.5).wall(2,-2,.5,3,2,5);
        door.minZ=-4; door.maxZ=4;
        result=run(new RemoteReturnNavigator(door),door,p(5,2),tick->p(0,0),500);
        check(result.expanded>0,"one-block doorway preserves a safe narrow corridor");
        var corners=new World().wall(.35,-2,-.2,1.2,2,.35).wall(-.2,-2,.35,.35,2,1.2);
        check(!corners.clear(p(0,0),p(1,1)),"diagonal through touching blockers is obstructed");
        run(new RemoteReturnNavigator(corners),corners,p(0,0),tick->p(1,1),500);
        var vertical=new World().wall(1,-1,-100,2,1,100); vertical.minY=-3; vertical.maxY=3; vertical.minZ=-2; vertical.maxZ=2;
        result=run(new RemoteReturnNavigator(vertical),vertical,p(3,0),tick->p(0,0),500);
        check(result.highestY>1.15,"search can route above or below a wall in three dimensions");
    }
    private static void changingWorld() {
        var world=wallWorld(); var navigation=new RemoteReturnNavigator(world); trail(navigation);
        world.wall(3,-2,2,5,2,4);
        var result=run(navigation,world,p(8,0),tick->p(0,0),600);
        check(result.expanded>0,"new wall across breadcrumbs requires an alternative route");
        world=wallWorld(); navigation=new RemoteReturnNavigator(world);
        RemoteReturnNavigator.Step first=null;
        for (int i=0;i<100;i++) {
            first=navigation.next(p(8,0),p(0,0));
            if (first.status()==Status.MOVING) break;
        }
        check(first!=null && first.status()==Status.MOVING,"planned path available for changed-obstacle fixture");
        Point blocked=first.waypoint();
        world.wall(blocked.x()-.04,-2,blocked.z()-.04,blocked.x()+.04,2,blocked.z()+.04);
        result=run(navigation,world,p(8,0),tick->p(0,0),600);
        check(result.expanded>0,"cached segment is invalidated when a new obstacle covers it");
    }
    private static void sealedAndBudgets() {
        var sealed=new World().wall(-2,-2,-2,-1,2,2).wall(1,-2,-2,2,2,2)
                .wall(-2,-2,-2,2,2,-1).wall(-2,-2,1,2,2,2);
        var navigation=new RemoteReturnNavigator(sealed);
        var blocked=navigation.next(p(0,0),p(4,0));
        check(blocked.status()==Status.BLOCKED && blocked.waypoint()==null,"fully enclosed return remains in place");
        for (int i=0;i<20;i++) check(navigation.next(p(0,0),p(4,0)).expandedNodes()==0,"blocked search retry has a cooldown");
        sealed.walls.remove(1);
        check(navigation.next(p(0,0),p(4,0)).status()==Status.MOVING,"opening a direct exit resumes immediately");
        var thin=new World().wall(.2,-2,-100,.25,2,100); thin.minX=0; thin.maxX=.5; thin.minZ=-1; thin.maxZ=1;
        navigation=new RemoteReturnNavigator(thin);
        blocked=navigation.next(p(0,0),p(.5,0));
        check(blocked.status()!=Status.ARRIVED,"a thin wall blocks arrival even within 0.7 blocks");
        var large=new World().wall(.5,-100,-100,1.5,100,100); large.minY=-20; large.maxY=20;
        navigation=new RemoteReturnNavigator(large); int total=0,maxStored=0,ticks=0;
        do {
            large.calls=0; blocked=navigation.next(p(0,0),p(4,0));
            check(blocked.expandedNodes()<=128 && large.calls<=3500,"incremental search obeys node and sweep budgets");
            check(blocked.status()!=Status.MOVING && blocked.status()!=Status.ARRIVED,"unreachable destination never emits movement");
            total+=blocked.expandedNodes(); maxStored=Math.max(maxStored,navigation.searchNodeCount()); ticks++;
        } while (blocked.status()!=Status.BLOCKED && ticks<200);
        check(blocked.status()==Status.BLOCKED && ticks>1 && ticks<200,"bounded search terminates over multiple ticks");
        check(total<=4096 && maxStored<=4096 && maxStored==4096,"search cannot grow past 4096 nodes");
    }
    private static void movingTargetAndCache() {
        var world=wallWorld(); var navigation=new RemoteReturnNavigator(world);
        RemoteReturnNavigator.Step first=null;
        for (int i=0;i<100;i++) {
            first=navigation.next(p(8,0),p(0,0));
            if (first.status()==Status.MOVING) break;
        }
        check(first!=null && first.status()==Status.MOVING,"cached path established");
        Point moved=advance(p(8,0),first.waypoint(),.1);
        var cached=navigation.next(moved,p(0,.02));
        check(cached.status()==Status.MOVING && cached.expandedNodes()==0,"small body motion keeps a safe cached path");
        run(navigation,world,moved,tick->p(0,Math.min(2,tick*.05)),500);
        var open=new World(); navigation=new RemoteReturnNavigator(open);
        var result=run(navigation,open,p(20,0),tick->p(0,Math.min(4,tick*.1)),150);
        check(result.end.distanceTo(p(0,4))<=.7,"clear direct pursuit uses the moving body's latest position");
    }
    private static void sharedBudget() {
        var world=wallWorld(); var navigation=new RemoteReturnNavigator(world);
        for (int tick=0;tick<10;tick++) {
            world.calls=0; var step=navigation.next(p(8,0),p(0,0),0);
            check(step.status()==Status.SEARCHING && step.expandedNodes()==0 && navigation.searchNodeCount()==0,
                    "zero global budget defers A star without creating search state");
            check(world.calls<=40,"zero budget still bounds collision queries");
        }
        RemoteReturnNavigator.Step ready=null;
        for (int tick=0;tick<500;tick++) {
            int budget=tick%3==0?0:tick%3==1?1:5;
            world.calls=0; ready=navigation.next(p(8,0),p(0,0),budget);
            check(ready.expandedNodes()<=budget && world.calls<=27*budget+40,"shared incremental budget bounds expansions and edge checks");
            if (ready.status()==Status.MOVING) break;
        }
        check(ready!=null && ready.status()==Status.MOVING,"small shared budgets can finish the same path across ticks");
        var cached=navigation.next(p(8,0),p(0,0),0);
        check(cached.status()==Status.MOVING && cached.expandedNodes()==0,"zero budget follows an already checked cached path");
        navigation=new RemoteReturnNavigator(world); trail(navigation);
        check(navigation.next(p(8,0),p(0,0),0).status()==Status.MOVING,"zero budget follows safe breadcrumbs");
        var open=new RemoteReturnNavigator((from,to)->true);
        check(open.next(p(128,0),p(0,0),0).status()==Status.MOVING,"zero budget still permits a direct return");
        check(open.next(p(.6,0),p(0,0),0).status()==Status.ARRIVED,"zero budget still checks a clear arrival");
        int expectedWork=-1;
        for (int budget:new int[]{128,15,1}) {
            var distant=wallWorld(); distant.minY=-10; distant.maxY=10;
            var distantNavigation=new RemoteReturnNavigator(distant); int total=0;
            RemoteReturnNavigator.Step step=null;
            for (int tick=0;tick<1000;tick++) {
                step=distantNavigation.next(p(8,0),p(-100,0),budget); total+=step.expandedNodes();
                check(step.expandedNodes()<=budget,"long search respects a small shared budget");
                if (step.status()==Status.MOVING || step.status()==Status.BLOCKED) break;
            }
            check(step!=null && step.status()==Status.MOVING,"small budgets retain distant line-of-sight probes");
            if (expectedWork<0) expectedWork=total;
            check(total==expectedWork,"budget changes search speed without dropping useful distant probes");
        }
        boolean rejected=false;
        try { open.next(p(0,0),p(0,0),-1); } catch (IllegalArgumentException expected) { rejected=true; }
        check(rejected,"negative global budget is rejected");
        var movingWorld=wallWorld(); movingWorld.minY=-10; movingWorld.maxY=10;
        var movingNavigation=new RemoteReturnNavigator(movingWorld); boolean planned=false;
        for (int tick=0;tick<500;tick++) {
            var step=movingNavigation.next(p(8,0),p(-100,Math.min(4,tick*.1)),1);
            if (step.status()==Status.MOVING) { planned=true; check(movingWorld.clear(p(8,0),step.waypoint()),"moving-target planned segment remains clear"); break; }
        }
        check(planned,"small-budget search keeps progress while the body moves moderately");
        movingNavigation=new RemoteReturnNavigator(movingWorld); planned=false;
        for (int tick=0;tick<400;tick++) {
            Point movingGoal=p(-100-tick*.2,0);
            check(!movingWorld.clear(p(8,0),movingGoal),"continuously moving fixture keeps its direct path blocked");
            var step=movingNavigation.next(p(8,0),movingGoal,1);
            if (step.status()==Status.MOVING) { planned=true; check(movingWorld.clear(p(8,0),step.waypoint()),"continuous moving-target plan emits a clear segment"); break; }
        }
        check(planned,"continuous body motion cannot repeatedly erase a budget-one search frontier");
    }
    private static void inputs() {
        for (double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            for (int axis=0;axis<3;axis++) {
                boolean rejected=false;
                try { new Point(axis==0?invalid:0,axis==1?invalid:0,axis==2?invalid:0); }
                catch (IllegalArgumentException expected) { rejected=true; }
                check(rejected,"non-finite coordinate rejected on every axis");
            }
        }
        var navigation=new RemoteReturnNavigator((from,to)->true);
        for (int i=0;i<10000;i++) navigation.record(p(i*.01,0));
        check(navigation.breadcrumbCount()==8192,"recorded return history is capped");
        navigation.record(p(99.99,0)); check(navigation.breadcrumbCount()==8192,"duplicate breadcrumb does not grow history");
        navigation.next(p(100,0),p(0,0)); navigation.record(p(120,0));
        check(navigation.breadcrumbCount()==8192,"return movement is not appended to the outbound route");
        navigation.reset(); check(navigation.breadcrumbCount()==0 && navigation.searchNodeCount()==0,"reset clears all session navigation state");
        navigation.record(p(1,1)); check(navigation.breadcrumbCount()==1,"reset accepts a new outbound route");
        boolean rejected=false;
        try { navigation.next(null,p(0,0)); } catch (NullPointerException expected) { rejected=true; }
        check(rejected,"null navigation input fails immediately");
    }
}
