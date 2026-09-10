import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import top.csituka.magicaland.gameplay.client.RemoteAimMath;

public final class RemoteAimMathTest {
    private static final float RAD=(float)(Math.PI/180);
    private static int checks;
    public static void main(String[] args) {
        projectionAndParallax();
        actualCameraAxes();
        clipAndPrecision();
        System.out.println("PASS RemoteAimMathTest: "+checks+" checks");
    }

    private static void projectionAndParallax() {
        var view=new Matrix4f();
        var projection=new Matrix4f().perspective(90*RAD,1,.05f,100);
        near(RemoteAimMath.project(view,projection,0,0,-3,0,0,0),.5f,.5f,"first-person forward aim");
        near(RemoteAimMath.project(view,projection,1,0,-3,0,0,0),2f/3,.5f,"off-axis near hit");
        near(RemoteAimMath.project(view,projection,1,0,-3,0,0,4),4f/7,.5f,"rear camera distance changes actual parallax");
        near(RemoteAimMath.project(view,projection,1,0,-1,0,0,4),.6f,.5f,"near hit differs from miss endpoint");
        near(RemoteAimMath.project(view,projection,1,0,-3,.3,0,4),.55f,.5f,"camera lateral offset is included");
        near(RemoteAimMath.project(view,projection,0,1,-3,0,0,0),.5f,1f/3,"HUD Y points downward");
        var bobbed=new Matrix4f(projection).translate(.12f,.08f,0).rotateZ(.07f);
        var actual=RemoteAimMath.project(view,bobbed,0,0,-3,0,0,0);
        check(actual.x()>.5f && actual.y()<.5f,"actual projection retains camera bob/hurt transform");
        var beforeView=new Matrix4f(view);var beforeProjection=new Matrix4f(projection);
        RemoteAimMath.project(view,projection,0,0,-3,0,0,0);
        check(view.equals(beforeView)&&projection.equals(beforeProjection),"shared render matrices are not mutated");
    }

    private static void actualCameraAxes() {
        var projection=new Matrix4f().perspective(70*RAD,16f/9,.05f,100);
        for(int yaw=-360;yaw<=360;yaw+=10) for(int pitch=-89;pitch<=89;pitch+=10) {
            // Camera#setRotation and GameRenderer#renderWorld use these exact conventions.
            var camera=new Quaternionf().rotationYXZ(-yaw*RAD,pitch*RAD,0);
            var before=new Quaternionf(camera);
            var view=new Matrix4f().rotateX(pitch*RAD).rotateY((yaw+180)*RAD);
            var forward=camera.transform(new Vector3f(0,0,1)).mul(3);
            near(RemoteAimMath.project(view,projection,forward.x,forward.y,forward.z,0,0,0),.5f,.5f,"Minecraft yaw/pitch aim");
            for(boolean left:new boolean[]{false,true}) {
                var offset=RemoteAimMath.itemSideOffset(camera,left);
                check(Math.abs(offset.length()-.30f)<.00001f,"visual offset stays 0.30 blocks");
                var screen=view.transformDirection(new Vector3f(offset));
                check(left?screen.x<-.299f:screen.x>.299f,"handed offset is on correct screen side");
                check(Math.abs(screen.y)<.00001f && Math.abs(screen.z)<.00001f,"side offset never changes visual depth or height");
                near(RemoteAimMath.project(view,projection,forward.x,forward.y,forward.z,0,0,0),.5f,.5f,"visual offset does not move aim origin");
            }
            check(camera.equals(before),"shared camera quaternion is unchanged");
        }
        var frontView=new Matrix4f().rotateY((float)Math.PI);
        check(RemoteAimMath.project(frontView,projection,0,0,-3,0,0,-2)==null,"front-view target behind camera is not forced to center");
        near(RemoteAimMath.project(frontView,projection,0,0,-3,0,0,-4),.5f,.5f,"front-view target genuinely in front still projects");
    }

    private static void clipAndPrecision() {
        var view=new Matrix4f();var projection=new Matrix4f().perspective(90*RAD,1,.05f,100);
        for(double[] xyz:new double[][]{{0,0,1},{0,0,0},{0,0,-.001},{0,0,-1000},{99,0,-3},{0,99,-3},
                {Double.NaN,0,-3},{0,Double.POSITIVE_INFINITY,-3}})
            check(RemoteAimMath.project(view,projection,xyz[0],xyz[1],xyz[2],0,0,0)==null,"clipped/nonfinite target is hidden");
        var near=RemoteAimMath.project(view,projection,.125,0,-3,0,0,0);
        var far=RemoteAimMath.project(view,projection,29_999_999.125,256,29_999_996,29_999_999,256,29_999_999);
        near(far,near.x(),near.y(),"subtract camera in double precision at world border");
        check(RemoteAimMath.project(new Matrix4f().zero(),projection,0,0,-3,0,0,0)==null,"degenerate view hidden");
        check(RemoteAimMath.project(view,new Matrix4f().zero(),0,0,-3,0,0,0)==null,"degenerate projection hidden");
        check(RemoteAimMath.itemSideOffset(new Quaternionf(Float.NaN,0,0,1),false).length()==0,"invalid camera offset fails closed");
    }

    private static void near(RemoteAimMath.Point point,float x,float y,String reason) {
        check(point!=null && Math.abs(point.x()-x)<.00001f && Math.abs(point.y()-y)<.00001f,reason+": "+point);
    }
    private static void check(boolean passed,String reason) {
        checks++;if(!passed)throw new AssertionError(reason);
    }
}
