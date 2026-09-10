import top.csituka.magicaland.gameplay.remote.RemoteSessionRules;

public final class RemoteSessionRulesTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("rule "+checks); }
    private static boolean see(RemoteSessionRules rules,float coverage,double x,double y,double z,
                               double dx,double dy,double dz,double ix,double iy,double iz) {
        return rules.visibility(coverage,x,y,z,dx,dy,dz,ix,iy,iz,1,0,0);
    }
    private static RemoteSessionRules blind() {
        var rules=new RemoteSessionRules();
        check(!see(rules,.8f,0,0,0,.1,0,0,.3,0,0));
        check(!see(rules,1,.1,0,0,.1,0,0,.3,0,0)); return rules;
    }
    public static void main(String[] args) {
        var rules=blind();
        for (int i=0;i<10000;i++) check(!see(rules,1,.1,0,0,0,0,0,0,0,0));
        check(rules.phase()==RemoteSessionRules.Phase.BLIND);
        check(!see(rules,1,.9,0,0,.8,0,0,0,0,0));
        check(!see(rules,1,1,0,0,.1,0,0,-.3,0,0));
        check(!see(rules,1,.95,0,0,-.05,0,0,.3,0,0));
        check(!see(rules,1,.95,0,0,0,0,0,.3,0,0));
        check(see(rules,1,1.05,0,0,.1,0,0,.3,0,0));
        rules=blind();
        for (int i=1;i<100;i++) check(!see(rules,1,.1,i*.1,i*.1,0,.1,.1,0,.3,.3));
        check(!see(rules,1,.5,10,10,.4,.1,0,.2,.2,0));
        check(see(rules,1,.9,10.1,10,.4,.1,0,.2,.2,0));
        rules=blind();
        check(!see(rules,1,-2,0,0,-2.1,0,0,-.3,0,0));
        check(!see(rules,1,-1,0,0,1,0,0,.3,0,0));
        check(!see(rules,1,.2,0,0,1.2,0,0,.3,0,0));
        check(see(rules,1,.9,0,0,.7,0,0,.3,0,0));
        check(!see(rules,.96f,.9,0,0,0,0,0,0,0,0));
        check(rules.phase()==RemoteSessionRules.Phase.FADING);
        check(!see(rules,0,.9,0,0,0,0,0,0,0,0) && rules.phase()==RemoteSessionRules.Phase.ACTIVE);
        var still=new RemoteSessionRules();
        check(!see(still,0,3,0,0,0,0,0,0,0,0)); check(!see(still,1,3,0,0,0,0,0,0,0,0));
        check(!see(still,1,3,2,0,0,2,0,0,.3,0));
        check(!see(still,1,2,2,0,-1,0,0,-.3,0,0));
        check(see(still,1,4,2,0,2,0,0,.3,0,0));
        var up=new RemoteSessionRules();
        check(!up.visibility(.9f,0,0,0,0,.1,0,0,.3,0,0,1,0));
        check(!up.visibility(1,0,.1,0,0,.1,0,0,.3,0,0,1,0));
        check(!up.visibility(1,2,.1,0,2,0,0,.3,0,0,0,1,0));
        check(up.visibility(1,2,1,0,0,.9,0,0,.3,0,0,1,0));
        check(up.acceptInput(0) && !up.acceptInput(0) && !up.acceptInput(-1));
        check(up.acceptInput(2) && !up.acceptInput(1) && up.sequence()==2);
        check(up.close() && !up.close() && !up.acceptInput(3));
        check(!up.visibility(0,0,0,0,0,0,0,0,0,0,1,0,0));
        check(up.phase()==RemoteSessionRules.Phase.CLOSED);
        System.out.println("PASS RemoteSessionRulesTest: "+checks+" checks");
    }
}
