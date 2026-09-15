package top.elysianherd.magicaland.gameplay.client.telekinesis;

final class TelekinesisMarkerGeometry {
    @FunctionalInterface interface Vertex { void add(double x, double y, double z, float alpha); }
    private TelekinesisMarkerGeometry() {}

    static void ring(double x, double y, double z, double radius, Vertex out) {
        if (!Double.isFinite(radius) || radius < .2 || radius > 8) return;
        double[] bands = {radius - .12, radius - .025, radius + .025, radius + .16};
        float[] alpha = {0, .85f, .85f, 0};
        for (int step = 0; step < 64; step++) {
            double a = step * Math.PI / 32, b = (step + 1) * Math.PI / 32;
            for (int band = 0; band < 3; band++) {
                out.add(x + Math.cos(a) * bands[band], y, z + Math.sin(a) * bands[band], alpha[band]);
                out.add(x + Math.cos(a) * bands[band+1], y, z + Math.sin(a) * bands[band+1], alpha[band+1]);
                out.add(x + Math.cos(b) * bands[band+1], y, z + Math.sin(b) * bands[band+1], alpha[band+1]);
                out.add(x + Math.cos(b) * bands[band], y, z + Math.sin(b) * bands[band], alpha[band]);
            }
        }
    }

    static void box(double x0, double y0, double z0, double x1, double y1, double z1, Vertex out) {
        double[] xs = {x0,x1}, ys = {y0,y1}, zs = {z0,z1};
        for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) {
            edge(x0,ys[i],zs[j],x1,ys[i],zs[j],0,1,0,out);
            edge(x0,ys[i],zs[j],x1,ys[i],zs[j],0,0,1,out);
            edge(xs[i],y0,zs[j],xs[i],y1,zs[j],1,0,0,out);
            edge(xs[i],y0,zs[j],xs[i],y1,zs[j],0,0,1,out);
            edge(xs[i],ys[j],z0,xs[i],ys[j],z1,1,0,0,out);
            edge(xs[i],ys[j],z0,xs[i],ys[j],z1,0,1,0,out);
        }
    }

    private static void edge(double x, double y, double z, double ex, double ey, double ez,
                             double dx, double dy, double dz, Vertex out) {
        double[] widths = {-.06,-.009,.009,.06};
        float[] alpha = {0,.7f,.7f,0};
        for (int band = 0; band < 3; band++) {
            double a = widths[band], b = widths[band+1];
            out.add(x+dx*a,y+dy*a,z+dz*a,alpha[band]);
            out.add(x+dx*b,y+dy*b,z+dz*b,alpha[band+1]);
            out.add(ex+dx*b,ey+dy*b,ez+dz*b,alpha[band+1]);
            out.add(ex+dx*a,ey+dy*a,ez+dz*a,alpha[band]);
        }
    }
}
