package top.csituka.magicaland.gameplay.sense;

import java.util.concurrent.atomic.AtomicInteger;
import static top.csituka.magicaland.gameplay.sense.EarthSenseGround.*;

public final class EarthSenseGroundTest {
    private static int checks;
    private static final Cell ORIGIN=new Cell(0,0,0);
    public static void main(String[] args) {
        var flat=search(ORIGIN,(x,y,z)->y<=0,2048,4096);
        for(int x=-14;x<=14;x++)for(int z=-14;z<=14;z++)if(x*x+z*z<=196)check(flat.contains(new Cell(x,0,z)),"all radius14 dry plane reachable");
        for(var cell:flat.cells())check(Math.abs(cell.y())<=2&&cell.x()*cell.x()+cell.z()*cell.z()<=196,"local volume hard bound");
        var wall=search(ORIGIN,(x,y,z)->y<=0||x==4,2048,4096);check(wall.contains(new Cell(10,0,0)),"wall does not block connected foundation");
        var trench=search(ORIGIN,(x,y,z)->y<=0&&x!=4,2048,4096);check(!trench.contains(new Cell(8,0,0)),"deep trench has no under-range bridge");
        var shallow=search(ORIGIN,(x,y,z)->y<=0&&(x!=4||y<0),2048,4096);check(shallow.contains(new Cell(8,0,0)),"shallow connected ground can carry around dip");
        var floating=search(ORIGIN,(x,y,z)->y==0||y==2&&x>=4,2048,4096);check(!floating.contains(new Cell(8,2,0)),"floating platform lacks material connection");
        var bridged=search(ORIGIN,(x,y,z)->y==0||y==2&&x>=4||x==4&&z==0&&y==1,2048,4096);check(bridged.contains(new Cell(8,2,0)),"supported platform connected via pillar");
        var unloaded=search(ORIGIN,(x,y,z)->x<4&&y==0,2048,4096);check(!unloaded.contains(new Cell(4,0,0)),"unavailable column not guessed");
        for(int nodes:new int[]{0,1,2,17,2048})for(int probes:new int[]{0,1,2,31,4096}) {
            var calls=new AtomicInteger();var limited=search(ORIGIN,(x,y,z)->{calls.incrementAndGet();return true;},nodes,probes);
            check(calls.get()<=probes&&limited.probes()==calls.get()&&limited.cells().size()<=nodes,"probe and node budgets exact");
        }
        Shape full=new Shape(0,0,0,1,1,1),bottom=new Shape(0,0,0,1,.5,1),top=new Shape(0,.5,0,1,1,1);
        check(touches(full,ORIGIN,full,new Cell(1,0,0)),"full block shared face");
        check(touches(bottom,ORIGIN,bottom,new Cell(1,0,0)),"wood/slab bridge horizontal contact");
        check(!touches(bottom,ORIGIN,top,new Cell(1,0,0)),"slabs touching only edge do not conduct");
        check(!touches(bottom,ORIGIN,bottom,new Cell(0,1,0)),"stacked bottom slabs with air gap do not conduct");
        check(touches(top,ORIGIN,bottom,new Cell(0,1,0)),"vertically touching slabs conduct");
        var partial=search(ORIGIN,new Conductor(){public boolean test(int x,int y,int z){return y==0;}
            public boolean connects(Cell from,Cell to){return !(from.x()==3&&to.x()==4||from.x()==4&&to.x()==3);}},2048,4096);
        check(!partial.contains(new Cell(8,0,0)),"real shape edge check gates propagation");
        System.out.println("PASS EarthSenseGroundTest: "+checks+" flat/wall/pit/platform, partial-shape contact and finite-search checks");
    }
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}
