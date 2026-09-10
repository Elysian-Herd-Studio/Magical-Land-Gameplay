package top.csituka.magicaland.gameplay.sense;

public final class EarthSenseFocusTest {
    private static int checks;
    public static void main(String[] args) {
        var focus=newFocus();check(!focus.ready(10)&&!focus.ready(11)&&focus.ready(12),"two tick server observation");
        for(int tick=0;tick<600;tick++)check(denial(focus,tick*.05,0,0,20,2,0,7,true,.05,-.0784,0)==null,"sustained slow crouch stays active without an anchor");
        for(double[] delta:new double[][]{{.02,0,0},{.061,0,0},{0,.5,0},{0,0,-.061},{.043,0,.043},{10,2,-10}})
            check(denial(newFocus(),delta[0],delta[1],delta[2],20,2,0,7,true,.1,-.0784,.1)==null,"grounded actual movement and external pushes do not cancel");
        check(denial(newFocus(),0,0,0,20,2,0,7,true,.5,0,0)==null,"velocity alone never cancels focus");
        check(denial(newFocus(),0,0,0,20,2,0,7,true,0,.101,0)==null,"grounded step velocity is not an airborne substitute");
        check("hurt".equals(denial(newFocus(),0,0,0,19,2,0,7,true,0,0,0)),"health loss interrupts");
        check("hurt".equals(denial(newFocus(),0,0,0,20,1,0,7,true,0,0,0)),"absorbed damage interrupts");
        check("hurt".equals(denial(newFocus(),0,0,0,20,2,1,7,true,0,0,0)),"hurt animation interrupts even without health loss");
        check("hurt".equals(denial(newFocus(),0,0,0,20,2,0,8,true,0,0,0)),"new attacker timestamp interrupts");
        check(newFocus().denial(0,64,0,20,2,0,108,false,true,0,-.0784,0)==null,"expired attacker cleanup timestamp is not new damage");
        check("airborne".equals(denial(newFocus(),0,0,0,20,2,0,7,false,0,0,0)),"airborne immediately ends focus; no old grace");
        check("invalid".equals(denial(newFocus(),Double.NaN,0,0,20,2,0,7,true,0,0,0)),"nonfinite position hidden");
        check("invalid".equals(denial(newFocus(),0,0,0,20,2,0,7,true,Double.POSITIVE_INFINITY,0,0)),"invalid velocity remains a safety failure");
        focus=newFocus();check(denial(focus,0,0,0,22,3,0,7,true,0,0,0)==null,"healing can remain focused");
        check("hurt".equals(denial(focus,0,0,0,21,3,0,7,true,0,0,0)),"damage after healing compared to last state");
        System.out.println("PASS EarthSenseFocusTest: "+checks+" authoritative warmup, free ground movement, harmless pushes, damage/absorption and immediate airborne checks");
    }
    private static EarthSenseFocus newFocus(){return new EarthSenseFocus(20,2,7,10);}
    private static String denial(EarthSenseFocus focus,double x,double y,double z,float hp,float absorption,int hurt,int attacked,boolean ground,double vx,double vy,double vz){return focus.denial(x,64+y,z,hp,absorption,hurt,attacked,true,ground,vx,vy,vz);}
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}
