package pub.godweer.util.render.world;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.module.impl.render.PopEffect;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.render.world.particles.ParticlesWorldRenderer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Create by daun kvass
 */
public final class PopEffectRenderer {

    private final ParticlesWorldRenderer particleRenderer = new ParticlesWorldRenderer();
    private final List<PopParticle> particles = new ArrayList<>();
    private final List<BeamInstance> beams = new ArrayList<>();
    private final List<LightningBolt> lightnings = new ArrayList<>();
    private final List<PopChamsInstance> chams = new ArrayList<>();

    private long lastFrameTime = System.currentTimeMillis();


    public static class PopChamsInstance {
        public final EntityRenderState renderState;
        public Vec3 pos;
        public final Vec3 velocity;
        public final long startTime;
        public final long durationMs = 850;
        public final int color;

        public PopChamsInstance(EntityRenderState renderState, Vec3 startPos, Vec3 velocity, int color) {
            this.renderState = renderState;
            this.pos = startPos;
            this.velocity = velocity;
            this.startTime = System.currentTimeMillis();
            this.color = color;
        }

        public float getProgress() {
            return Math.min(1.0F, (System.currentTimeMillis() - this.startTime) / (float) this.durationMs);
        }

        public boolean isDead() {
            return getProgress() >= 1.0F;
        }

        public void update(float dt) {
            this.pos = this.pos.add(this.velocity.scale(dt * 20.0));
        }
    }


    public static class PopParticle {
        Vec3 pos;
        Vec3 velocity;
        Entity target;
        long startTime;
        long durationMs;
        float baseSize;
        int color;
        boolean isHelix;
        boolean isSelfFirstPerson;
        double helixAngle;
        double initialRadius;
        double initialHeight;
        double helixSpeed;

        public PopParticle(Vec3 pos, Vec3 velocity, Entity target, long durationMs, float baseSize, int color, boolean isSelfFirstPerson) {
            this.pos = pos;
            this.velocity = velocity;
            this.target = target;
            this.startTime = System.currentTimeMillis();
            this.durationMs = durationMs;
            this.baseSize = baseSize;
            this.color = color;
            this.isHelix = false;
            this.isSelfFirstPerson = isSelfFirstPerson;
        }

        public static PopParticle createHelix(Entity target, double startAngle, double radius, double height, double speed, long durationMs, float size, int color, boolean isFirstPerson) {
            PopParticle p = new PopParticle(target.position().add(0, height, 0), Vec3.ZERO, target, durationMs, size, color, isFirstPerson);
            p.isHelix = true;
            p.helixAngle = startAngle;
            p.initialRadius = radius;
            p.initialHeight = height;
            p.helixSpeed = speed;
            return p;
        }

        public float getProgress() {
            return Math.min(1.0F, (System.currentTimeMillis() - this.startTime) / (float) this.durationMs);
        }

        public boolean isDead() {
            return getProgress() >= 1.0F;
        }

        public void update(float dt, float speedScale) {
            Minecraft mc = Minecraft.getInstance();
            float progress = getProgress();

            Vec3 targetCenter;
            if (this.isSelfFirstPerson && mc.player != null) {
                Vec3 look = mc.player.getViewVector(1.0F);
                targetCenter = mc.gameRenderer.mainCamera().position().add(look.scale(1.2));
            } else if (this.target != null && this.target.isAlive()) {
                targetCenter = this.target.position().add(0, this.target.getBbHeight() * 0.5, 0);
            } else {
                targetCenter = this.pos;
            }

            if (this.isHelix) {
                this.helixAngle += this.helixSpeed * dt * 7.5 * speedScale;

                double currentRadius = this.initialRadius;
                double currentHeight = this.initialHeight;

                if (progress > 0.2F) {
                    float pull = (progress - 0.2F) / 0.8F;
                    float ease = pull * pull * (3.0F - 2.0F * pull);
                    currentRadius = this.initialRadius * (1.0 - ease * 0.95);
                    currentHeight = this.initialHeight * (1.0 - ease * 0.9);
                }

                if (this.isSelfFirstPerson && mc.player != null) {
                    Vec3 camPos = mc.gameRenderer.mainCamera().position();
                    Vec3 look = mc.player.getViewVector(1.0F);
                    Vec3 up = new Vec3(0, 1, 0);
                    Vec3 right = look.cross(up).normalize();
                    Vec3 actualUp = right.cross(look).normalize();

                    double xOffset = Math.cos(this.helixAngle) * currentRadius;
                    double yOffset = Math.sin(this.helixAngle) * currentRadius;

                    this.pos = camPos.add(look.scale(1.0 + currentHeight * 0.1))
                            .add(right.scale(xOffset))
                            .add(actualUp.scale(yOffset));
                } else {
                    double curX = targetCenter.x + Math.cos(this.helixAngle) * currentRadius;
                    double curY = (this.target != null ? this.target.getY() : targetCenter.y) + currentHeight;
                    double curZ = targetCenter.z + Math.sin(this.helixAngle) * currentRadius;
                    this.pos = new Vec3(curX, curY, curZ);
                }

            } else if (this.target != null) {
                if (progress > 0.2F) {
                    Vec3 toTarget = targetCenter.subtract(this.pos);
                    if (toTarget.length() > 0.1) {
                        Vec3 desiredVel = toTarget.normalize().scale((4.0 + progress * 8.0) * speedScale);
                        this.velocity = this.velocity.lerp(desiredVel, Math.min(1.0, dt * 7.0 * speedScale));
                    }
                } else {
                    this.velocity = this.velocity.scale(Math.pow(0.35, dt));
                }
                this.pos = this.pos.add(this.velocity.scale(dt * 20.0 * speedScale));
            } else {
                this.velocity = this.velocity.scale(Math.pow(0.5, dt)).add(0, -0.65 * dt, 0);
                this.pos = this.pos.add(this.velocity.scale(dt * 20.0 * speedScale));
            }
        }
    }


    public static class BeamInstance {
        Vec3 pos;
        long startTime;
        long durationMs;
        int color;
        float width;

        public BeamInstance(Vec3 pos, long durationMs, int color, float width) {
            this.pos = pos;
            this.startTime = System.currentTimeMillis();
            this.durationMs = durationMs;
            this.color = color;
            this.width = width;
        }

        public float getProgress() {
            return Math.min(1.0F, (System.currentTimeMillis() - this.startTime) / (float) this.durationMs);
        }

        public boolean isDead() {
            return getProgress() >= 1.0F;
        }
    }

    public static class LightningBolt {
        List<Vec3> segments = new ArrayList<>();
        List<Vec3> branchSegments = new ArrayList<>();
        long startTime;
        long durationMs = 450;
        int color;

        public LightningBolt(Vec3 targetPos, int color) {
            this.startTime = System.currentTimeMillis();
            this.color = color;

            Vec3 top = targetPos.add(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 3.0,
                    28.0,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 3.0
            );

            int count = 28;
            Vec3 current = top;
            this.segments.add(current);

            for (int i = 1; i <= count; i++) {
                float frac = (float) i / count;
                Vec3 ideal = top.lerp(targetPos, frac);
                if (i < count) {
                    double spread = (1.0F - frac) * 1.3 + 0.3;
                    ideal = ideal.add(
                            (ThreadLocalRandom.current().nextDouble() - 0.5) * spread,
                            (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.4,
                            (ThreadLocalRandom.current().nextDouble() - 0.5) * spread
                    );

                    if (i == 9 || i == 18) {
                        Vec3 branchEnd = ideal.add(
                                (ThreadLocalRandom.current().nextDouble() - 0.5) * 3.8,
                                -3.5 - ThreadLocalRandom.current().nextDouble(3.0),
                                (ThreadLocalRandom.current().nextDouble() - 0.5) * 3.8
                        );
                        this.branchSegments.add(ideal);
                        this.branchSegments.add(ideal.lerp(branchEnd, 0.5).add(0.2, 0.2, -0.2));
                        this.branchSegments.add(branchEnd);
                    }
                }
                this.segments.add(ideal);
            }
        }

        public float getProgress() {
            return Math.min(1.0F, (System.currentTimeMillis() - this.startTime) / (float) this.durationMs);
        }

        public boolean isDead() {
            return getProgress() >= 1.0F;
        }
    }

    public synchronized boolean hasActive() {
        return !this.particles.isEmpty() || !this.beams.isEmpty() || !this.lightnings.isEmpty() || !this.chams.isEmpty();
    }


    public synchronized void spawnLightning(Vec3 targetPos, PopEffect module) {
        int color = module.getEffectColor();
        this.lightnings.add(new LightningBolt(targetPos, color));

        for (int i = 0; i < 25; i++) {
            Vec3 vel = randomSphereVector().scale(0.4);
            this.particles.add(new PopParticle(targetPos, vel, null, 400, module.size.getValue() * 0.75F, color, false));
        }
    }

    public synchronized void spawnGhost(LivingEntity entity, PopEffect module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        EntityRenderState state = mc.getEntityRenderDispatcher().extractEntity(entity, 0.0F);

        Vec3 move = entity.getDeltaMovement();
        Vec3 vel = move.lengthSqr() > 0.001
                ? move.normalize().scale(0.14).add(0, 0.06, 0)
                : new Vec3(0, 0.08, 0);

        this.chams.add(new PopChamsInstance(state, entity.position(), vel, module.getEffectColor()));
    }

    public synchronized void spawnTotemBeam(Entity target, PopEffect module) {
        Minecraft mc = Minecraft.getInstance();
        boolean isSelf1stPerson = (target == mc.player && mc.options.getCameraType().isFirstPerson());

        Vec3 pos = target.position();
        int gold = ColorUtil.rgba(255, 220, 35, 255);
        int green = ColorUtil.rgba(40, 245, 120, 255);
        long dur = (long) (module.lifetime.getValue() * 1000.0F);

        this.beams.add(new BeamInstance(pos, dur, gold, 1.8F));

        int totalParticles = (int) module.count.getValue().floatValue();
        for (int i = 0; i < totalParticles; i++) {
            int arm = i % 3;
            double baseAngle = (arm * (Math.PI * 2.0 / 3.0)) + ThreadLocalRandom.current().nextDouble(0.4);
            double height = isSelf1stPerson ? ThreadLocalRandom.current().nextDouble() * 2.0 : ThreadLocalRandom.current().nextDouble() * 14.0;
            double radius = isSelf1stPerson ? 0.6 + ThreadLocalRandom.current().nextDouble(0.8) : 1.0 + (height / 14.0) * 1.8;
            double rotSpeed = 0.8 + ThreadLocalRandom.current().nextDouble(0.4);
            int col = (i % 2 == 0) ? gold : green;

            this.particles.add(PopParticle.createHelix(target, baseAngle, radius, height, rotSpeed, dur, module.size.getValue() * 0.85F, col, isSelf1stPerson));
        }
    }

    public synchronized void spawnTotemExplosion(Entity target, PopEffect module) {
        Minecraft mc = Minecraft.getInstance();
        boolean isSelf1stPerson = (target == mc.player && mc.options.getCameraType().isFirstPerson());

        int particleCount = (int) module.count.getValue().floatValue();
        float baseSize = module.size.getValue();

        Vec3 startPos;
        if (isSelf1stPerson && mc.player != null) {
            Vec3 look = mc.player.getViewVector(1.0F);
            startPos = mc.gameRenderer.mainCamera().position().add(look.scale(0.8));
        } else {
            startPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        }

        long dur = (long) (module.lifetime.getValue() * 1000.0F);
        int gold = ColorUtil.rgba(255, 215, 30, 255);
        int green = ColorUtil.rgba(40, 245, 120, 255);

        for (int i = 0; i < particleCount; i++) {
            Vec3 vel = randomSphereVector().scale(0.5 + ThreadLocalRandom.current().nextDouble(0.65));
            int col = ThreadLocalRandom.current().nextBoolean() ? gold : green;
            this.particles.add(new PopParticle(startPos, vel, target, dur, baseSize, col, isSelf1stPerson));
        }
    }

    public synchronized void spawnKillBeam(Vec3 pos, PopEffect module) {
        int col = module.getEffectColor();
        long dur = (long) (module.lifetime.getValue() * 1000.0F);
        this.beams.add(new BeamInstance(pos, dur, col, 1.6F));

        for (int i = 0; i < 30; i++) {
            Vec3 vel = new Vec3((ThreadLocalRandom.current().nextDouble() - 0.5) * 0.45, 0.15 + ThreadLocalRandom.current().nextDouble(0.35), (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.45);
            this.particles.add(new PopParticle(pos, vel, null, dur / 2, module.size.getValue() * 0.8F, col, false));
        }
    }

    public synchronized void spawnKillExplosion(Vec3 pos, PopEffect module) {
        int count = (int) module.count.getValue().floatValue();
        int col = module.getEffectColor();
        long dur = (long) (module.lifetime.getValue() * 1000.0F);

        for (int i = 0; i < count; i++) {
            Vec3 vel = randomSphereVector().scale(0.45 + ThreadLocalRandom.current().nextDouble(0.6));
            this.particles.add(new PopParticle(pos, vel, null, dur, module.size.getValue(), col, false));
        }
    }


    public synchronized void submitChams(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (this.chams.isEmpty() || camera == null) return;
        Minecraft mc = Minecraft.getInstance();

        for (PopChamsInstance ghost : this.chams) {
            float progress = ghost.getProgress();
            float scale = 1.0F + progress * 0.25F;

            double x = ghost.pos.x - camera.pos.x();
            double y = ghost.pos.y - camera.pos.y();
            double z = ghost.pos.z - camera.pos.z();

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.scale(scale, scale, scale);

            mc.getEntityRenderDispatcher().submit(
                    ghost.renderState,
                    camera,
                    0.0, 0.0, 0.0,
                    poseStack,
                    collector
            );

            poseStack.popPose();
        }
    }


    public synchronized void render(PopEffect module, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long now = System.currentTimeMillis();
        float dt = Math.min(0.1F, (now - this.lastFrameTime) / 1000.0F);
        this.lastFrameTime = now;

        float speedScale = module.speed.getValue();
        float brightness = module.brightness.getValue();
        boolean throughWalls = module.throughWalls.getValue();

        this.beams.removeIf(BeamInstance::isDead);
        this.lightnings.removeIf(LightningBolt::isDead);

        Iterator<PopChamsInstance> cIt = this.chams.iterator();
        while (cIt.hasNext()) {
            PopChamsInstance c = cIt.next();
            if (c.isDead()) {
                cIt.remove();
                continue;
            }
            c.update(dt);
        }

        Iterator<PopParticle> pIt = this.particles.iterator();
        while (pIt.hasNext()) {
            PopParticle p = pIt.next();
            if (p.isDead()) {
                pIt.remove();
                continue;
            }
            p.update(dt, speedScale);
        }

        List<ParticlesWorldRenderer.Sprite> sprites = new ArrayList<>();

        for (BeamInstance beam : this.beams) {
            float progress = beam.getProgress();
            float alphaFactor = (float) Math.sin(progress * Math.PI);

            int outerAlpha = (int) (ColorUtil.alpha(beam.color) * alphaFactor * 0.8F);
            int outerCol = ColorUtil.withAlpha(beam.color, outerAlpha);
            int coreCol = ColorUtil.rgba(255, 255, 255, (int) (245 * alphaFactor));

            float height = 35.0F;
            for (float y = 0; y < height; y += 0.45F) {
                Vec3 pPos = beam.pos.add(0, y, 0);
                sprites.add(new ParticlesWorldRenderer.Sprite(pPos, beam.width * 1.5F * alphaFactor, outerCol));
                sprites.add(new ParticlesWorldRenderer.Sprite(pPos, beam.width * 0.45F * alphaFactor, coreCol));
            }
        }

        for (LightningBolt bolt : this.lightnings) {
            float progress = bolt.getProgress();
            float flash = (1.0F - progress) * (ThreadLocalRandom.current().nextFloat() > 0.12F ? 1.0F : 0.4F);
            int boltCol = ColorUtil.withAlpha(bolt.color, (int) (255 * flash));
            int whiteCore = ColorUtil.rgba(255, 255, 255, (int) (255 * flash));

            renderDenseLaserLine(bolt.segments, sprites, boltCol, whiteCore, 0.55F, 0.16F);

            if (!bolt.branchSegments.isEmpty()) {
                renderDenseLaserLine(bolt.branchSegments, sprites, boltCol, whiteCore, 0.35F, 0.1F);
            }
        }

        for (PopParticle p : this.particles) {
            float progress = p.getProgress();
            float alphaFactor = (float) Math.sin((1.0F - progress) * (Math.PI * 0.5));
            int pAlpha = (int) (ColorUtil.alpha(p.color) * alphaFactor);
            int drawColor = ColorUtil.withAlpha(p.color, pAlpha);

            float currentSize = p.baseSize * (0.5F + 0.5F * alphaFactor);
            sprites.add(new ParticlesWorldRenderer.Sprite(p.pos, currentSize, drawColor));
        }

        if (!sprites.isEmpty()) {
            this.particleRenderer.render(sprites, brightness, throughWalls, true);
        }
    }

    private static void renderDenseLaserLine(List<Vec3> points, List<ParticlesWorldRenderer.Sprite> out, int outerColor, int coreColor, float outerR, float coreR) {
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 p1 = points.get(i);
            Vec3 p2 = points.get(i + 1);
            double dist = p1.distanceTo(p2);
            double step = 0.035;

            for (double d = 0; d < dist; d += step) {
                Vec3 pos = p1.lerp(p2, (float) (d / dist));
                out.add(new ParticlesWorldRenderer.Sprite(pos, outerR, outerColor));
                out.add(new ParticlesWorldRenderer.Sprite(pos, coreR, coreColor));
            }
        }
    }

    private static Vec3 randomSphereVector() {
        double theta = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
        double phi = Math.acos(ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0);
        double sinPhi = Math.sin(phi);
        return new Vec3(sinPhi * Math.cos(theta), Math.cos(phi), sinPhi * Math.sin(theta));
    }

    public synchronized void clear() {
        this.particles.clear();
        this.beams.clear();
        this.lightnings.clear();
        this.chams.clear();
    }
}