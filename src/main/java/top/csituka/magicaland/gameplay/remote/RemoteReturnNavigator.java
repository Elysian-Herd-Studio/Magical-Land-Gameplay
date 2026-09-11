package top.csituka.magicaland.gameplay.remote;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

public final class RemoteReturnNavigator {
    public static final double GRID_STEP=.5, ARRIVAL_DISTANCE=.7;
    public static final int MAX_BREADCRUMBS=8192, EXPANSIONS_PER_TICK=128, MAX_SEARCH_NODES=4096;
    private static final int TRAIL_LOOKAHEAD=32, PATH_LOOKAHEAD=8, RETRY_TICKS=20, MAX_QUEUE_ENTRIES=MAX_SEARCH_NODES*2;
    private static final double WAYPOINT_DISTANCE=.2, GOAL_REPLAN_DISTANCE=1, DETOUR_MARGIN=12;

    public record Point(double x,double y,double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Return position must be finite");
        }
        public double distanceTo(Point other) { return Math.hypot(Math.hypot(x-other.x,y-other.y),z-other.z); }
    }
    @FunctionalInterface public interface Collision { boolean clear(Point from,Point to); }
    public enum Status { MOVING, ARRIVED, SEARCHING, BLOCKED }
    public record Step(Status status,Point waypoint,int expandedNodes) {}

    private final Collision collision;
    private final Point[] breadcrumbs=new Point[MAX_BREADCRUMBS];
    private int breadcrumbStart,breadcrumbSize;
    private boolean returning;
    private int trailCursor=-1, pathCursor, retryTicks;
    private List<Point> path;
    private Point pathGoal;
    private Search search;

    public RemoteReturnNavigator(Collision collision) { this.collision=Objects.requireNonNull(collision); }
    public int breadcrumbCount() { return breadcrumbSize; }
    public int searchNodeCount() { return search==null?0:search.best.size(); }
    public void record(Point position) {
        Objects.requireNonNull(position);
        if (returning || breadcrumbSize>0 && breadcrumb(breadcrumbSize-1).equals(position)) return;
        if (breadcrumbSize==MAX_BREADCRUMBS) {
            breadcrumbs[breadcrumbStart]=position; breadcrumbStart=(breadcrumbStart+1)%MAX_BREADCRUMBS;
        } else {
            breadcrumbs[(breadcrumbStart+breadcrumbSize)%MAX_BREADCRUMBS]=position; breadcrumbSize++;
        }
    }
    public void reset() {
        Arrays.fill(breadcrumbs,null); breadcrumbStart=0; breadcrumbSize=0;
        returning=false; trailCursor=-1; path=null; pathGoal=null;
        pathCursor=0; search=null; retryTicks=0;
    }
    public Step next(Point from,Point goal) {
        return next(from,goal,EXPANSIONS_PER_TICK);
    }
    public Step next(Point from,Point goal,int nodeBudget) {
        Objects.requireNonNull(from); Objects.requireNonNull(goal);
        if (nodeBudget<0) throw new IllegalArgumentException("Return search budget must be nonnegative");
        if (!returning) { returning=true; trailCursor=breadcrumbSize-1; }
        if (!collision.clear(from,from)) return new Step(Status.BLOCKED,null,0);
        if (collision.clear(from,goal)) {
            path=null; pathGoal=null; search=null; retryTicks=0;
            return new Step(from.distanceTo(goal)<=ARRIVAL_DISTANCE?Status.ARRIVED:Status.MOVING,goal,0);
        }
        if (pathGoal!=null && pathGoal.distanceTo(goal)>=GOAL_REPLAN_DISTANCE) { path=null; pathGoal=null; }
        // 搜索保留短期目标快照，终段始终检查当前本体，避免移动本体不断清空低预算搜索。
        if (search!=null && search.start.distanceTo(from)>GOAL_REPLAN_DISTANCE) { search=null; retryTicks=0; }
        Step cached=followPath(from,0);
        if (cached!=null) return cached;
        Point trail=trailWaypoint(from);
        if (trail!=null) {
            search=null; retryTicks=0;
            return new Step(Status.MOVING,trail,0);
        }
        if (retryTicks>0) { retryTicks--; return new Step(Status.BLOCKED,null,0); }
        if (nodeBudget==0) return new Step(Status.SEARCHING,null,0);
        if (search==null) search=new Search(from,goal);
        int expanded=0;
        for (int work=0;work<Math.min(nodeBudget,EXPANSIONS_PER_TICK) && !search.open.isEmpty();work++) {
            Node node=search.open.poll();
            if (search.best.get(node.cell)!=node || node.closed) continue;
            node.closed=true; expanded++; search.expanded++;
            if ((node.point.distanceTo(goal)<=4 || (search.expanded&15)==0) && collision.clear(node.point,goal)) {
                List<Point> found=new ArrayList<>();
                for (Node step=node;step!=null;step=step.parent) found.add(step.point);
                Collections.reverse(found); found.add(goal);
                path=found; pathCursor=1; pathGoal=goal; search=null;
                Step result=followPath(from,expanded);
                return result!=null?result:new Step(Status.SEARCHING,null,expanded);
            }
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0 && dy==0 && dz==0) continue;
                Cell cell=new Cell(node.cell.x+dx,node.cell.y+dy,node.cell.z+dz);
                Point point=search.point(cell);
                if (!search.inside(point)) continue;
                Node previous=search.best.get(cell);
                double cost=node.g+GRID_STEP*Math.sqrt(dx*dx+dy*dy+dz*dz);
                if (previous!=null && (previous.closed || cost>=previous.g-1e-9)) continue;
                if (previous==null && search.best.size()>=MAX_SEARCH_NODES
                        || search.open.size()>=MAX_QUEUE_ENTRIES || !collision.clear(node.point,point)) continue;
                Node next=new Node(cell,point,cost,point.distanceTo(search.goal),node,search.order++);
                search.best.put(cell,next); search.open.add(next);
            }
        }
        if (search.open.isEmpty()) {
            search=null; retryTicks=RETRY_TICKS;
            return new Step(Status.BLOCKED,null,expanded);
        }
        return new Step(Status.SEARCHING,null,expanded);
    }
    private Point trailWaypoint(Point from) {
        Point chosen=null;
        int limit=Math.max(0,trailCursor-TRAIL_LOOKAHEAD+1),selected=trailCursor;
        for (int index=trailCursor;index>=limit;index--) {
            Point candidate=breadcrumb(index);
            double distance=from.distanceTo(candidate);
            if (distance>16 || !collision.clear(from,candidate)) continue;
            selected=index;
            chosen=distance>WAYPOINT_DISTANCE?candidate:null;
        }
        if (selected!=trailCursor || chosen==null && trailCursor>=0
                && from.distanceTo(breadcrumb(trailCursor))<=WAYPOINT_DISTANCE
                && collision.clear(from,breadcrumb(trailCursor))) trailCursor=selected-(chosen==null?1:0);
        return chosen;
    }
    private Point breadcrumb(int index) { return breadcrumbs[(breadcrumbStart+index)%MAX_BREADCRUMBS]; }
    private Step followPath(Point from,int expanded) {
        if (path==null) return null;
        for (int skipped=0;skipped<PATH_LOOKAHEAD && pathCursor<path.size()
                && from.distanceTo(path.get(pathCursor))<=WAYPOINT_DISTANCE
                && collision.clear(from,path.get(pathCursor));skipped++) pathCursor++;
        if (pathCursor<path.size() && collision.clear(from,path.get(pathCursor)))
            return new Step(Status.MOVING,path.get(pathCursor),expanded);
        path=null; pathGoal=null;
        return null;
    }
    private record Cell(int x,int y,int z) {}
    private static final class Node {
        final Cell cell;
        final Point point;
        final double g,h;
        final Node parent;
        final long order;
        boolean closed;
        Node(Cell cell,Point point,double g,double h,Node parent,long order) {
            this.cell=cell; this.point=point; this.g=g; this.h=h; this.parent=parent; this.order=order;
        }
    }
    private static final class Search {
        final Point start,goal;
        final Map<Cell,Node> best=new HashMap<>();
        final PriorityQueue<Node> open=new PriorityQueue<>(Comparator
                .comparingDouble((Node node)->node.g+node.h).thenComparingDouble(node->node.h).thenComparingLong(node->node.order));
        long order;
        int expanded;
        Search(Point start,Point goal) {
            this.start=start; this.goal=goal;
            Cell cell=new Cell(0,0,0);
            Node first=new Node(cell,start,0,start.distanceTo(goal),null,order++);
            best.put(cell,first); open.add(first);
        }
        Point point(Cell cell) {
            return new Point(start.x+cell.x*GRID_STEP,start.y+cell.y*GRID_STEP,start.z+cell.z*GRID_STEP);
        }
        boolean inside(Point point) {
            return point.x>=Math.min(start.x,goal.x)-DETOUR_MARGIN && point.x<=Math.max(start.x,goal.x)+DETOUR_MARGIN
                    && point.y>=Math.min(start.y,goal.y)-DETOUR_MARGIN && point.y<=Math.max(start.y,goal.y)+DETOUR_MARGIN
                    && point.z>=Math.min(start.z,goal.z)-DETOUR_MARGIN && point.z<=Math.max(start.z,goal.z)+DETOUR_MARGIN;
        }
    }
}
