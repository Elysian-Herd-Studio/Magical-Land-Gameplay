package top.elysianherd.magicaland.gameplay.client;

record AbilityWheelLayout(double x, double y, double radius, int count) {
    static AbilityWheelLayout fit(int width, int height, int count) {
        double radius = Math.max(20, Math.min(100, Math.min((width - 32) / 2.0,
                (height - 64) / 2.0)));
        return new AbilityWheelLayout(width / 2.0, height / 2.0, radius, Math.max(0, Math.min(4, count)));
    }

    double sector() { return Math.PI * 2 / Math.max(1, count); }

    int slotAt(double mouseX, double mouseY) {
        double dx = mouseX - x, dy = mouseY - y, distance = Math.hypot(dx, dy);
        if (count == 0 || !Double.isFinite(distance) || distance < radius * .35 || distance > radius) return -1;
        double angle = Math.atan2(dy, dx) + Math.PI / 2 + sector() / 2;
        double within = angle - Math.floor(angle / sector()) * sector();
        if (count > 1 && (within < .025 || within > sector() - .025)) return -1;
        return Math.floorMod((int) Math.floor(angle / sector()), count);
    }

    double angle(int slot) { return -Math.PI / 2 + slot * sector(); }
    int iconX(int slot) { return (int) Math.round(x + Math.cos(angle(slot)) * radius * .68) - 8; }
    int iconY(int slot) { return (int) Math.round(y + Math.sin(angle(slot)) * radius * .68) - 8; }
}
