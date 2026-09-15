package top.elysianherd.magicaland.gameplay.remote;

public final class RemoteSessionRules {
    public enum Phase { ACTIVE, FADING, BLIND, CLOSED }
    private int sequence=-1;
    private Phase phase=Phase.ACTIVE;
    private double visibleX,visibleY,visibleZ,blindX,blindY,blindZ,forwardX,forwardY,forwardZ;
    private boolean anchored;
    private final boolean spiritualEcho;
    public static final double RECALL_DEPTH=.75;

    public RemoteSessionRules() { this(RemoteCapabilities.SPIRITUAL_ECHO); }
    public RemoteSessionRules(boolean spiritualEcho) { this.spiritualEcho=spiritualEcho; }
    public int sequence() { return sequence; }
    public Phase phase() { return phase; }
    public boolean canInteract() { return phase!=Phase.CLOSED && (spiritualEcho || phase!=Phase.BLIND); }
    public boolean acceptInput(int next) {
        if (phase==Phase.CLOSED || next<=sequence) return false;
        sequence=next; return true;
    }
    public boolean close() {
        if (phase==Phase.CLOSED) return false;
        phase=Phase.CLOSED; return true;
    }
    public boolean visibility(float occlusion,double x,double y,double z,
                              double dx,double dy,double dz,double inputX,double inputY,double inputZ,
                              double fallbackX,double fallbackY,double fallbackZ) {
        if (phase==Phase.CLOSED) return false;
        if (occlusion<1) {
            visibleX=x; visibleY=y; visibleZ=z; anchored=true;
            phase=occlusion>0?Phase.FADING:Phase.ACTIVE;
            return false;
        }
        if (spiritualEcho) { phase=Phase.BLIND; return false; }
        if (phase!=Phase.BLIND) {
            double ax=anchored?x-visibleX:dx,ay=anchored?y-visibleY:dy,az=anchored?z-visibleZ:dz;
            double length=Math.sqrt(ax*ax+ay*ay+az*az);
            if (length<1e-6) {
                ax=fallbackX; ay=fallbackY; az=fallbackZ; length=Math.sqrt(ax*ax+ay*ay+az*az);
            }
            forwardX=length>1e-6?ax/length:0; forwardY=length>1e-6?ay/length:0; forwardZ=length>1e-6?az/length:0;
            blindX=x; blindY=y; blindZ=z; phase=Phase.BLIND;
            return false;
        }
        double net=(x-blindX)*forwardX+(y-blindY)*forwardY+(z-blindZ)*forwardZ;
        double active=inputX*forwardX+inputY*forwardY+inputZ*forwardZ;
        double actual=dx*forwardX+dy*forwardY+dz*forwardZ;
        return net>=RECALL_DEPTH && active>1e-5 && actual>1e-5
                && dx*inputX+dy*inputY+dz*inputZ>1e-6;
    }
}
