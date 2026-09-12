package top.csituka.magicaland.gameplay.levitation;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.SlabType;
import net.minecraft.fluid.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
import static top.csituka.magicaland.gameplay.levitation.UnicornLevitationGround.*;
import static top.csituka.magicaland.gameplay.levitation.UnicornLevitationMath.*;

public final class UnicornLevitationPhysicsTest {
    private static int checks;
    public static void main(String[] args) {
        math(); hover(); recover(); naturalFalls(); SharedConstants.createGameVersion(); Bootstrap.initialize(); terrain(); movingSurface(); liquidFalls();
        System.out.println("PASS UnicornLevitationPhysicsTest: "+checks+" finite motion, landing trajectories and real Minecraft collision/fluid checks");
    }
    private static void math() {
        for(double v:new double[]{-10,-4,-1,-.1,0,.16,3}) {
            var m=new Motion(3,v,-4); check(step(m,0,1,1,Mode.OFF,0,0)==m,"release preserves velocity exactly");
            var n=step(m,0,1,1,Mode.ASCEND,0,0);check(Math.abs(n.y()-m.y())<=.08000001&&n.horizontalSpeed()>4.9,"no initial fall/knockback truncation");
        }
        double last=-1;
        for(double down:new double[]{.1,.5,1,2,3,4,10}) {
            var m=new Motion(0,-down,0);double fallen=0;int ticks=0;
            while(m.y()<0&&ticks++<200){var n=step(m,0,0,0,Mode.ASCEND,0,0);check(n.y()>=m.y()&&n.y()-m.y()<=.08000001,"monotonic finite fall braking");fallen+=Math.max(0,-n.y());m=n;}
            check(fallen>last&&ticks<200,"faster falls need more braking distance");last=fallen;
            for(int i=0;i<30;i++)m=step(m,0,0,0,Mode.ASCEND,0,0);near(m.y(),.16,"eventual upward cruise");
        }
        for(float yaw:new float[]{0,45,90,180,270,Float.NaN})for(float f:new float[]{-1,0,1,Float.NaN})for(float s:new float[]{-1,0,1}) {
            var m=Motion.ZERO;
            for(int i=0;i<30;i++){var n=step(m,yaw,f,s,Mode.ASCEND,0,0);check(Math.hypot(n.x()-m.x(),n.z()-m.z())<=.02500001,"vector bounded horizontal impulse");check(n.horizontalSpeed()<=HORIZONTAL_SPEED+.00000001,"normalized slow ascent diagonals");m=n;}
            for(int i=0;i<30;i++)m=step(m,yaw,0,0,Mode.ASCEND,0,0);near(m.horizontalSpeed(),0,"inertial steering stop");
        }
        last=0;for(int i=0;i<1000;i++){double depth=probeDepth(-i/50d);check(depth>=last&&depth<=96,"monotonic bounded probe distance");last=depth;}
        check(chooseMode(false,true,false,Mode.OFF,-1,1,true)==Mode.OFF,"eligibility first");
        check(chooseMode(true,false,false,Mode.ASCEND,.16,Double.NaN,false)==Mode.OFF,"release stops active lift");
        check(chooseMode(true,false,false,Mode.SURFACE,-.01,-.5,true)==Mode.OFF,"no out-of-range fluid promotion");
        check(chooseMode(true,false,true,Mode.LANDING,0,0,false)==Mode.OFF,"solid contact ends landing");
        for(double down:new double[]{.1,.3,.5,1,2,3.9}){trajectory(down,false);trajectory(down,true);}
        for(double start:new double[]{.12,.15,.25,.35}){double y=start;var m=Motion.ZERO;for(int i=0;i<100;i++){var n=step(m,0,1,0,Mode.SURFACE,y,0);check(Math.abs(n.y()-m.y())<=.10000001,"bounded surface correction");y+=n.y();m=n;check(y>.1,"fluid clearance never lost in steady walking");}near(y,SURFACE_CLEARANCE,"surface convergence");}
    }
    private static void trajectory(double down,boolean fluid) {
        double y=stoppingDistance(-down)+.5;var m=new Motion(0,-down,0);Mode previous=Mode.OFF;boolean started=false,done=false;
        for(int i=0;i<1000;i++){
            Mode mode=chooseMode(true,false,false,false,previous,m.y(),y<=probeDepth(m.y())?y:Double.NaN,fluid,6);
            if(started)check(mode!=Mode.OFF,"no LANDING/OFF oscillation: "+down+" y="+y+" vy="+m.y());
            if(mode!=Mode.OFF)started=true;
            var n=mode==Mode.OFF?new Motion(0,(m.y()-.08)*.98,0):step(m,0,0,0,mode,y,0);
            if(mode==Mode.LANDING&&y>.08)check(n.y()>=m.y()-1e-10,"landing cannot accelerate descent");
            if(mode!=Mode.OFF)check(Math.abs(n.y()-m.y())<=.10000001,"bounded passive acceleration");
            y+=n.y();m=n;previous=mode;
            if(fluid){check(y>0,"normal detected fall enters fluid: "+down+" y="+y+" vy="+m.y());if(Math.abs(y-.15)<.001&&Math.abs(m.y())<.001){done=true;break;}}
            else if(y<=0){check(Math.abs(m.y())<=.03,"slow physical touchdown: "+down+" vy="+m.y());done=true;break;}
        }
        check(started&&done,"landing eventually settles");
    }
    private static void hover() {
        check(Mode.OFF.ordinal()==0&&Mode.ASCEND.ordinal()==1&&Mode.LANDING.ordinal()==2&&Mode.SURFACE.ordinal()==3&&Mode.HOVER.ordinal()==4,"wire mode ordinals preserved");
        check(chooseMode(true,true,true,false,Mode.ASCEND,.16,Double.NaN,false,0)==Mode.HOVER,"Space plus Shift requests hover");
        check(chooseMode(true,true,false,false,Mode.HOVER,0,Double.NaN,false,0)==Mode.ASCEND,"release Shift resumes held Space ascent");
        check(chooseMode(true,false,true,false,Mode.HOVER,0,Double.NaN,false,0)==Mode.OFF,"Shift alone cannot keep active lift");
        check(chooseMode(true,false,true,false,Mode.HOVER,-.5,1,false,4)==Mode.LANDING,"Space release keeps dangerous fall protection");
        check(chooseMode(true,false,true,false,Mode.HOVER,0,.15,true,0)==Mode.SURFACE,"Space release keeps water support");
        near(HORIZONTAL_SPEED,.065,"ascent uses sneaking horizontal speed");near(BOOST_HORIZONTAL_SPEED,.216,"hover uses normal walking horizontal speed");
        for(Mode mode:new Mode[]{Mode.ASCEND,Mode.HOVER,Mode.SURFACE,Mode.LANDING}){var m=Motion.ZERO;for(int i=0;i<40;i++)m=step(m,0,1,1,mode,.15,0);near(m.horizontalSpeed(),mode==Mode.HOVER||mode==Mode.SURFACE?.216:.065,"mode determines speed; no Shift sprint");}
        for(double vy:new double[]{-4,-1,-.16,0,.16,1,4}) {
            var motion=new Motion(0,vy,0);double y=30;
            for(int i=0;i<220;i++) {var next=step(motion,0,1,1,Mode.HOVER,y,Double.NaN);check(Math.abs(next.y()-motion.y())<=.08000001,"hover never cancels vertical momentum instantly");check(Math.abs(next.y())<=Math.abs(motion.y())+1e-9,"hover brakes monotonically");check(next.horizontalSpeed()<=BOOST_HORIZONTAL_SPEED+1e-9,"boosted diagonal cap");y+=next.y();motion=next;}
            near(motion.y(),0,"hover reaches stable altitude");near(motion.horizontalSpeed(),BOOST_HORIZONTAL_SPEED,"hover boost speed");
            double heldY=y;for(int i=0;i<100;i++){motion=step(motion,0,0,0,Mode.HOVER,y,Double.NaN);y+=motion.y();}near(y,heldY,"hover holds final braking altitude");
            var resumed=step(motion,0,1,0,Mode.ASCEND,y,Double.NaN);check(resumed.y()>0&&resumed.y()<=.08000001,"ascent resumes without speed jump");
        }
        var boosted=new Motion(BOOST_HORIZONTAL_SPEED,0,0);var normal=step(boosted,0,0,0,Mode.SURFACE,1,1-SURFACE_CLEARANCE);check(normal.horizontalSpeed()>HORIZONTAL_SPEED,"boost release preserves momentum");
    }
    private static void recover() {
        for(double down:new double[]{.1,.5,1,2,4}) {
            var m=new Motion(0,-down,0); Mode mode=Mode.OFF;
            boolean lifted=false, held=false; int ticks=0;
            while(ticks++<150) {
                mode=chooseMode(true,true,true,false,mode,m.y(),Double.NaN,false,5);
                var next=step(m,0,0,0,mode,100,Double.NaN);
                check(Math.abs(next.y()-m.y())<=LIFT_ACCELERATION+1e-9,"Shift-start retains bounded falling inertia");
                if(next.y()>.1)lifted=true;
                if(mode==Mode.HOVER&&Math.abs(next.y())<1e-8){held=true;break;}
                m=next;
            }
            check(lifted&&held,"falling Shift+Space lifts then settles");
        }
        var m=new Motion(0,ASCEND_SPEED,0);double rise=0;
        for(int tick=0;tick<8;tick++){m=step(m,0,0,0,Mode.HOVER,0,Double.NaN);rise+=m.y();if(tick<7)check(m.y()>0,"no hard stop before eight hover frames");}
        near(m.y(),0,"smooth hover stop");check(rise>.5,"visible inertial upward travel");
    }
    private static void naturalFalls() {
        for(double height:new double[]{.25,.5,1,1.5,2,2.9,3.1,4,8,16,32,64,128}) {
            double y=height,fall=0;var motion=Motion.ZERO;Mode previous=Mode.OFF;boolean protectedFall=false,landed=false;
            for(int tick=0;tick<2000;tick++) {
                Mode mode=chooseMode(true,false,false,false,previous,motion.y(),y<=probeDepth(motion.y())?y:Double.NaN,false,fall);
                if(height<=3)check(mode==Mode.OFF,"safe short fall stays vanilla: "+height);
                if(mode==Mode.LANDING)protectedFall=true;
                if(protectedFall)check(mode==Mode.LANDING,"dangerous landing stays engaged to contact");
                var next=mode==Mode.OFF?new Motion(0,(motion.y()-.08)*.98,0):step(motion,0,0,0,mode,y,0);
                if(mode!=Mode.OFF)check(next.y()-motion.y()<=BRAKE_ACCELERATION+1e-9,"natural fall finite braking");
                y+=next.y();fall+=Math.max(0,-next.y());motion=next;previous=mode;
                if(y<=0){landed=true;if(height>3)check(Math.abs(motion.y())<=.03,"dangerous natural fall lands slowly: "+height+" vy="+motion.y());break;}
            }
            check(landed&&protectedFall==(height>3),"protection only for unsafe drop: "+height);
        }
        var extreme=step(new Motion(0,-20,0),0,0,0,Mode.LANDING,2,0);check(extreme.y()<-19.8,"unavoidable extreme impact is not magically erased");
    }
    private static void terrain(){
        var w=new View();w.put(0,0,0,Blocks.STONE.getDefaultState());support(w,body(.5,2,.5),Motion.ZERO,Kind.SOLID,1,false,"full block");
        w.put(0,0,0,Blocks.STONE_SLAB.getDefaultState().with(Properties.SLAB_TYPE,SlabType.BOTTOM));support(w,body(.5,1.4,.5),Motion.ZERO,Kind.SOLID,.5,false,"bottom slab");
        w.put(0,0,0,Blocks.STONE_SLAB.getDefaultState().with(Properties.SLAB_TYPE,SlabType.TOP));support(w,body(.5,1.4,.5),Motion.ZERO,Kind.SOLID,1,false,"top slab");
        w.put(0,0,0,Blocks.OAK_FENCE.getDefaultState());support(w,body(.5,2,.5),Motion.ZERO,Kind.SOLID,1.5,false,"tall fence shape");
        check(scan(w,new Box(.02,2,.02,.2,3.8,.2),Motion.ZERO,768).support()==null,"empty fence corner");
        w.put(0,0,0,Blocks.WATER.getDefaultState());double water=w.height();support(w,body(.5,water+.15,.5),Motion.ZERO,Kind.WATER,water,false,"source water height");
        w.put(0,0,0,Blocks.LAVA.getDefaultState());double lava=w.height();support(w,body(.5,lava+.15,.5),Motion.ZERO,Kind.LAVA,lava,false,"lava height");
        w.put(0,0,0,Blocks.WATER.getDefaultState().with(Properties.LEVEL_15,5));double flowing=w.height();check(flowing<water,"actual flowing level");support(w,body(.5,flowing+.15,.5),Motion.ZERO,Kind.WATER,flowing,false,"flowing water");
        w.put(0,0,0,Blocks.WATER.getDefaultState());w.put(0,1,0,Blocks.WATER.getDefaultState());check(scan(w,body(.5,.9,.5),Motion.ZERO,768).support()==null,"deep water cannot find false surface below");check(scan(w,body(.5,.9,.5),new Motion(.18,0,0),768).support()==null,"moving underwater cannot use forward surface to escape");
        w.clear();w.put(0,0,0,Blocks.WATER.getDefaultState());w.put(0,1,0,Blocks.STONE.getDefaultState());support(w,body(.5,2.2,.5),Motion.ZERO,Kind.SOLID,2,false,"solid occludes fluid below");
        w.clear();w.put(1,0,0,Blocks.WATER.getDefaultState());var move=new Motion(.18,0,0);support(w,body(.5,water+.15,.5),move,Kind.WATER,water,true,"short clear forward surface");
        check(scan(w,body(.5,4,.5),new Motion(.18,-1,0),768).support()==null,"no far forward deep surface");
        w.put(1,1,0,Blocks.GLASS.getDefaultState());check(scan(w,body(.5,water+.15,.5),move,768).support()==null,"glass collision blocks forward liquid");
        w.clear();w.put(0,0,0,Blocks.STONE.getDefaultState());w.put(1,0,0,Blocks.WATER.getDefaultState());support(w,body(.5,1.02,.5),move,Kind.WATER,water,true,"land to slightly lower water preactivation");support(w,body(.5,1.02,.5),Motion.ZERO,Kind.SOLID,1,false,"stationary land unchanged");
        w.put(1,1,0,Blocks.GLASS.getDefaultState());support(w,body(.5,1.02,.5),move,Kind.SOLID,1,false,"wall preserves actual ground");
        w.clear();w.put(0,0,0,Blocks.WATER.getDefaultState().with(Properties.LEVEL_15,4));w.put(1,0,0,Blocks.WATER.getDefaultState());double lower=w.height();support(w,body(.5,lower+.15,.5),new Motion(.025,0,0),Kind.WATER,water,true,"slow start still detects higher fluid early");
        w.put(1,2,0,Blocks.STONE_SLAB.getDefaultState().with(Properties.SLAB_TYPE,SlabType.TOP));support(w,body(.5,lower+.15,.5),move,Kind.WATER,lower,false,"ceiling blocks projected rise but keeps present support");
        w.clear();w.put(0,0,0,Blocks.WATER.getDefaultState());w.put(1,0,0,Blocks.WATER.getDefaultState());check(scan(w,body(.5,water-.3,.5),move,768).support()==null,"forward fluid cannot promote already-submerged feet");
        w.clear();w.put(3,0,0,Blocks.WATER.getDefaultState());check(scan(w,body(.5,water+.15,.5),new Motion(100,0,0),768).support()==null,"fixed forward reach despite knockback");
        w.clear();w.put(0,-70,0,Blocks.STONE.getDefaultState());support(w,body(.5,10,.5),new Motion(0,-4,0),Kind.SOLID,-69,false,"high fall bounded long probe");check(scan(w,body(.5,10,.5),new Motion(0,-.2,0),768).support()==null,"slow fall short probe");
        for(int budget:new int[]{0,1,2,5,25,100,768,100000}){var calls=new AtomicInteger();var result=scan((x,y,z)->{calls.incrementAndGet();return Cell.AIR;},body(1,200,1),new Motion(.18,-100,.18),budget);check(calls.get()==result.probes()&&result.probes()<=Math.min(768,budget),"hard query budget");if(budget<100)check(!result.complete()&&result.support()==null,"no guessed support after budget");}
        var calls=new AtomicInteger();var missing=scan((x,y,z)->{calls.incrementAndGet();return Cell.UNKNOWN;},body(.5,2,.5),Motion.ZERO,768);check(!missing.complete()&&missing.support()==null&&calls.get()==1,"unloaded query aborts");
        calls.set(0);check(!scan((x,y,z)->{calls.incrementAndGet();return Cell.AIR;},new Box(0,0,0,100,100,100),Motion.ZERO,768).complete()&&calls.get()==0,"oversized body rejected before loops");
        var boxes=Collections.nCopies(33,new Box(0,0,0,1,1,1));check(!scan((x,y,z)->new Cell(true,boxes,Kind.WATER,1),body(.5,2,.5),Motion.ZERO,768).complete(),"complex shape fails closed");
    }
    private static void movingSurface() {
        for(boolean lava:new boolean[]{false,true})for(boolean boost:new boolean[]{false,true})for(int slope:new int[]{0,1,2}) {
            var w=new View();var block=lava?Blocks.LAVA:Blocks.WATER;
            for(int x=-3;x<70;x++)for(int z=-2;z<=2;z++) {int level=slope==1?Math.max(0,3-Math.floorMod(x,18)/3):slope==2&&x<1?4:0;w.put(x,0,z,block.getDefaultState().with(Properties.LEVEL_15,level));}
            double x=.5,y=w.getFluidState(new BlockPos(0,0,0)).getHeight(w,new BlockPos(0,0,0))+SURFACE_CLEARANCE;var motion=Motion.ZERO;Mode previous=Mode.SURFACE;
            for(int tick=0;tick<200;tick++) {
                var result=scan(w,body(x,y,.5),motion,768);check(result.complete()&&result.support()!=null,"moving surface support remains loaded and found");var support=result.support();
                Mode mode=chooseMode(true,false,boost,false,previous,motion.y(),support.distance(),true,0);check(mode==Mode.SURFACE,"ordinary WASD keeps surface mode: x="+x+" y="+y+" vy="+motion.y()+" support="+support);
                motion=step(motion,0,0,1,mode,y,support.y());x+=motion.x();y+=motion.y();previous=mode;
                for(int col=(int)Math.floor(x-.3+.00001);col<=(int)Math.floor(x+.3-.00001);col++){var pos=new BlockPos(col,0,0);double fluidY=w.getFluidState(pos).getHeight(w,pos);check(y>fluidY+.001,"cross-cell walking avoids fluid contact: "+lava+" boost="+boost+" slope="+slope+" x="+x+" y="+y+" fluid="+fluidY);}
            }
            check(x>25,"test walks far outside original collision box");
        }
    }
    private static void liquidFalls() {
        check(chooseMode(true,false,false,false,Mode.OFF,-.5,1.5,true,0)==Mode.SURFACE,"fluid cushions even a harmless short fall before close capture");
        check(chooseMode(false,false,false,false,Mode.SURFACE,-.5,1.5,true,8)==Mode.OFF,"disabled skill cannot buffer a fluid fall");
        check(chooseMode(true,false,false,false,Mode.OFF,-.5,8,true,8)==Mode.OFF,"fluid does not pull from outside finite braking reach");
        for(boolean lava:new boolean[]{false,true})for(int level:new int[]{0,5})
                for(double height:new double[]{.2,.25,.5,1,2,3,4,8,16,32,64,128}) {
            var w=new View();w.put(0,0,0,(lava?Blocks.LAVA:Blocks.WATER).getDefaultState().with(Properties.LEVEL_15,level));
            double surface=w.height(),y=surface+height,fall=0;var motion=Motion.ZERO;Mode previous=Mode.OFF;
            boolean started=false,settled=false;int brakingTicks=0;double firstClearance=0;
            for(int tick=0;tick<1000;tick++) {
                var result=scan(w,body(.5,y,.5),motion,MAX_PROBES);check(result.complete(),"falling fluid scan remains bounded and complete");
                var support=result.support();
                Mode mode=chooseMode(true,false,false,false,previous,motion.y(),support==null?Double.NaN:support.distance(),support!=null&&support.kind()!=Kind.SOLID,fall);
                if(mode!=Mode.OFF&&!started){started=true;firstClearance=y-surface;}
                if(started)check(mode==Mode.SURFACE,"fluid braking connects directly to surface mode without dropout");
                var next=mode==Mode.OFF?new Motion(0,(motion.y()-.08)*.98,0):step(motion,0,0,0,mode,y,support.y());
                if(mode!=Mode.OFF) {
                    check(Math.abs(next.y()-motion.y())<=BRAKE_ACCELERATION+1e-9,"fluid buffer keeps finite acceleration");
                    if(motion.y()<-.08)check(next.y()>=motion.y()-1e-9,"fluid capture never adds to an existing fast fall");
                    if(motion.y()<-.08&&next.y()>motion.y())brakingTicks++;
                }
                y+=next.y();fall+=Math.max(0,-next.y());motion=next;previous=mode;
                check(y>=surface+SURFACE_CLEARANCE-1e-8,"normal fall never crosses liquid or target hover plane: "+lava+" height="+height+" y="+y);
                if(started&&Math.abs(y-surface-SURFACE_CLEARANCE)<1e-7&&Math.abs(motion.y())<1e-7){settled=true;break;}
            }
            check(started&&settled,"natural liquid fall settles at hover clearance: "+lava+" height="+height);
            if(height>=2)check(firstClearance>.4&&brakingTicks>=2,"visible multi-tick cushion begins before old capture range: "+height);
        }
        var late=step(new Motion(0,-4,0),0,0,0,Mode.SURFACE,.2,0);
        check(late.y()<-3.8,"activation too late cannot erase fall inertia or grant lava immunity");
    }
    private static Box body(double x,double y,double z){return new Box(x-.3,y,z-.3,x+.3,y+1.8,z+.3);}
    private static void support(View w,Box box,Motion motion,Kind kind,double y,boolean ahead,String message){var result=scan(w,box,motion,768);check(result.complete()&&result.support()!=null&&result.support().kind()==kind&&Math.abs(result.support().y()-y)<1e-6&&result.support().ahead()==ahead,message+": "+result);}
    private static final class View implements BlockView,Cells{
        final Map<BlockPos,BlockState> blocks=new HashMap<>();void put(int x,int y,int z,BlockState state){blocks.put(new BlockPos(x,y,z),state);}void clear(){blocks.clear();}
        double height(){var pos=BlockPos.ORIGIN;return getFluidState(pos).getHeight(this,pos);}
        public BlockEntity getBlockEntity(BlockPos pos){return null;}public BlockState getBlockState(BlockPos pos){return blocks.getOrDefault(pos,Blocks.AIR.getDefaultState());}public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}public int getHeight(){return 512;}public int getBottomY(){return -256;}
        public Cell at(int x,int y,int z){var pos=new BlockPos(x,y,z);var state=getBlockState(pos);var fluid=getFluidState(pos);Kind kind=fluid.isOf(Fluids.WATER)||fluid.isOf(Fluids.FLOWING_WATER)?Kind.WATER:fluid.isOf(Fluids.LAVA)||fluid.isOf(Fluids.FLOWING_LAVA)?Kind.LAVA:null;return new Cell(true,state.getCollisionShape(this,pos).getBoundingBoxes(),kind,kind==null?0:fluid.getHeight(this,pos));}
    }
    private static void near(double a,double b,String message){check(Double.isFinite(a)&&Math.abs(a-b)<1e-8,message+": "+a);}
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
}
