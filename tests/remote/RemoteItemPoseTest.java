import org.joml.Quaternionf;
import org.joml.Vector3f;
import top.csituka.magicaland.gameplay.client.RemoteItemPose;

public final class RemoteItemPoseTest {
    private static int checks;
    private static final float RAD=(float)(Math.PI/180);
    private static final Vector3f BLADE=new Vector3f(1,1,0).normalize();
    private static final Vector3f UP=new Vector3f(0,1,0);
    private static final Vector3f FACE=new Vector3f(0,0,1);

    public static void main(String[] args) {
        upwardTools();
        forwardBillboards();
        displayCompensation();
        continuousSwing();
        uprightBlocks();
        System.out.println("PASS RemoteItemPoseTest: "+checks+" checks");
    }

    private static void upwardTools() {
        for (int yaw=-360;yaw<=360;yaw+=15) for (int pitch=-90;pitch<=90;pitch+=10) {
            var forward=new Vector3f(-(float)Math.sin(yaw*RAD),0,(float)Math.cos(yaw*RAD));
            for (boolean left:new boolean[]{false,true}) for (boolean flat:new boolean[]{false,true}) {
                var pose=RemoteItemPose.tool(yaw,pitch,0,left,flat);
                var blade=pose.transform(new Vector3f(flat?BLADE:UP));
                check(blade.y>.4f,"idle tip stays upward even looking down");
                check(blade.dot(forward)>.4f,"idle tip stays forward even looking up");
                if (pitch==0) near(blade.y,(float)Math.sqrt(.5),"45 degree resting elevation");
                check(Math.abs(pose.transform(new Vector3f(FACE)).dot(forward))>.4f,
                        "tool exposes broad face, not only edge");
                near(RemoteItemPose.tool(yaw+360,pitch,0,left,flat).transform(new Vector3f(flat?BLADE:UP)),
                        blade,"wrapped heading");
            }
        }
    }

    private static void forwardBillboards() {
        for (int yaw=-360;yaw<=360;yaw+=15) for (int pitch=-90;pitch<=90;pitch+=10) {
            var camera=new Quaternionf().rotateY(-yaw*RAD).rotateX(pitch*RAD);
            var before=new Quaternionf(camera);
            var view=new Quaternionf().rotateX(pitch*RAD).rotateY((yaw+180)*RAD);
            for (boolean left:new boolean[]{false,true}) {
                var rendered=view.mul(RemoteItemPose.billboard(camera,0,left),new Quaternionf());
                for (var axis:new Vector3f[]{new Vector3f(1,0,0),UP,FACE})
                    near(rendered.transform(new Vector3f(axis)),axis,"upright pixel face, not mirrored");
            }
            check(camera.equals(before),"shared camera quaternion never mutated");
        }
    }

    private static void displayCompensation() {
        float[][] displays={{0,-90,55},{0,90,-55},{0,0,0},{75,45,0},{13,-37,24}};
        for (float[] display:displays) for (boolean left:new boolean[]{false,true}) {
            float side=left?-1:1;
            var vanilla=new Quaternionf().rotationXYZ(display[0]*RAD,side*display[1]*RAD,side*display[2]*RAD);
            for (int frame=0;frame<=100;frame++) {
                var target=RemoteItemPose.tool(137,-29,frame/100f,left,true);
                var before=new Quaternionf(target);
                var actual=RemoteItemPose.withoutDisplayRotation(target,display[0],display[1],display[2],left).mul(vanilla);
                for (var axis:new Vector3f[]{BLADE,UP,FACE})
                    near(actual.transform(new Vector3f(axis)),target.transform(new Vector3f(axis)),"resource display rotation canceled");
                check(target.equals(before),"target quaternion never mutated");
            }
        }
    }

    private static void continuousSwing() {
        for (boolean left:new boolean[]{false,true}) for (boolean flat:new boolean[]{false,true}) {
            var axis=flat?BLADE:UP;
            var idle=RemoteItemPose.tool(42,-16,0,left,flat).transform(new Vector3f(axis));
            near(RemoteItemPose.tool(42,-16,1,left,flat).transform(new Vector3f(axis)),idle,"swing ends at rest");
            near(RemoteItemPose.tool(42,-16,-1,left,flat).transform(new Vector3f(axis)),idle,"negative swing clamp");
            near(RemoteItemPose.tool(42,-16,2,left,flat).transform(new Vector3f(axis)),idle,"overshoot swing clamp");
            check(RemoteItemPose.tool(42,-16,.000001f,left,flat).transform(new Vector3f(axis)).distance(idle)<.004f,"smooth entrance");
            check(RemoteItemPose.tool(42,-16,.999999f,left,flat).transform(new Vector3f(axis)).distance(idle)<.00002f,"smooth exit");
            for (int frame=0;frame<=1000;frame++) {
                var pose=RemoteItemPose.tool(137,-29,frame/1000f,left,flat);
                check(Float.isFinite(pose.lengthSquared()) && Math.abs(pose.lengthSquared()-1)<.00001f,"finite unit pose");
            }
        }
        var camera=new Quaternionf().rotateY(.8f).rotateX(.3f);
        for (boolean left:new boolean[]{false,true}) for (var axis:new Vector3f[]{UP,FACE})
            near(RemoteItemPose.billboard(camera,0,left).transform(new Vector3f(axis)),
                    RemoteItemPose.billboard(camera,1,left).transform(new Vector3f(axis)),"billboard action rejoins rest");
    }

    private static void uprightBlocks() {
        for (int yaw=-720;yaw<=720;yaw++) {
            var pose=RemoteItemPose.upright(yaw);
            near(pose.transform(new Vector3f(UP)),UP,"3D item remains upright");
            var x=pose.transform(new Vector3f(1,0,0));
            var z=pose.transform(new Vector3f(FACE));
            near(x.length(),1,"3D width preserved");
            near(z.length(),1,"3D depth preserved");
            near(x.dot(z),0,"3D axes stay orthogonal");
        }
    }

    private static void near(Vector3f actual,Vector3f expected,String reason) {
        check(actual.isFinite() && actual.distance(expected)<.00002f,reason+": "+actual+" != "+expected);
    }
    private static void near(float actual,float expected,String reason) {
        check(Float.isFinite(actual) && Math.abs(actual-expected)<.00002f,reason);
    }
    private static void check(boolean value,String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
}
