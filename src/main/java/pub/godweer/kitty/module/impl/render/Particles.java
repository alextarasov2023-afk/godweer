package pub.godweer.kitty.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.AttackEvent;
import pub.godweer.enjoy.event.list.GameTickEvent;
import pub.godweer.enjoy.event.list.PacketEvent;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.kitty.module.settings.impl.MultiModeSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.Render3DUtil;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Create by daun kvass
 */
public final class Particles extends Module {

    private static final Identifier GLOW_TEXTURE = Identifier.fromNamespaceAndPath("godweer", "images/world/pt/glow.png");

    public final ModeSetting texture = mode("Текстура", "Star",
            "Star", "Heart", "Genshin", "Crown", "Dollar", "Sakura", "Glow", "Cross", "CubeBlast", "Lightning",
            "Line", "Rhombus", "Snowflake", "Triangle", "Bloom"
    );

    public final MultiModeSetting triggers = multiMode(
            "Спавнить от",
            List.of("Атака", "Ходьба", "Снаряды", "Тотем"),
            "Атака", "Ходьба", "Снаряды", "Тотем"
    );

    public final CheckBox glow = checkbox("Glow", true);
    public final CheckBox physics = checkbox("Гравитация", false);

    public final SliderSetting count = slider("Количество", 20.0F, 1.0F, 60.0F, 1.0F);
    public final SliderSetting countWalk = slider("Количество при ходьбе", 2.0F, 1.0F, 10.0F, 1.0F).visible(() -> triggers.isEnabled("Ходьба"));
    public final SliderSetting size = slider("Размер", 20.0F, 8.0F, 60.0F, 1.0F);
    public final SliderSetting lifetime = slider("Время жизни (сек)", 1.5F, 0.3F, 4.0F, 0.1F);
    public final SliderSetting spread = slider("Сила разлёта", 1.0F, 0.1F, 3.0F, 0.1F);
    public final SliderSetting speed = slider("Скорость", 1.2F, 0.1F, 3.0F, 0.1F);

    private final List<ImageParticle> particles = new ArrayList<>();
    private Vec3 lastPlayerPos = null;

    public Particles() {
        super("Particles", "Текстурные партиклы со свечением и отскоком от блоков", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        particles.clear();
        lastPlayerPos = null;
    }

    private static class ImageParticle {
        public Vec3 pos;
        public Vec3 motion;
        public float size;
        public float life;
        public float maxLife;
        public float rotation;
        public float rotSpeed;
        public int color;
        public Identifier texture;

        public ImageParticle(Vec3 pos, Vec3 motion, float size, float maxLife, int color, Identifier texture) {
            this.pos = pos;
            this.motion = motion;
            this.size = size;
            this.life = 0.0F;
            this.maxLife = maxLife;
            this.rotation = (float) ThreadLocalRandom.current().nextDouble(0, 360);
            this.rotSpeed = (float) ThreadLocalRandom.current().nextDouble(-180, 180);
            this.color = color;
            this.texture = texture;
        }

        public boolean update(float dt, boolean useGravity) {
            this.life += dt;
            this.rotation += this.rotSpeed * dt;

            Minecraft mc = Minecraft.getInstance();
            double scaleFactor = dt * 20.0;

            if (useGravity) {
                this.motion = this.motion.subtract(0, 0.015 * scaleFactor, 0);
                this.motion = this.motion.scale(Math.pow(0.96, scaleFactor));
            }

            Vec3 nextPos = this.pos.add(this.motion.scale(scaleFactor));

            if (mc.level != null && mc.player != null) {
                BlockHitResult hit = mc.level.clip(new ClipContext(
                        this.pos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player
                ));

                if (hit.getType() != HitResult.Type.MISS) {
                    Direction side = hit.getDirection();
                    Vec3 hitPos = hit.getLocation();

                    switch (side.getAxis()) {
                        case X -> {
                            this.motion = new Vec3(-this.motion.x * 0.7, this.motion.y * 0.8, this.motion.z * 0.8);
                            this.pos = new Vec3(hitPos.x + side.getStepX() * 0.05, hitPos.y, hitPos.z);
                        }
                        case Y -> {
                            this.motion = new Vec3(this.motion.x * 0.8, -this.motion.y * 0.7, this.motion.z * 0.8);
                            this.pos = new Vec3(hitPos.x, hitPos.y + side.getStepY() * 0.05, hitPos.z);
                        }
                        case Z -> {
                            this.motion = new Vec3(this.motion.x * 0.8, this.motion.y * 0.8, -this.motion.z * 0.7);
                            this.pos = new Vec3(hitPos.x, hitPos.y, hitPos.z + side.getStepZ() * 0.05);
                        }
                    }
                } else {
                    this.pos = nextPos;
                }
            } else {
                this.pos = nextPos;
            }

            return this.life >= this.maxLife;
        }
    }

    private Identifier getSelectedTexture() {
        String name = this.texture.getValue().toLowerCase();
        if (name.equals("cubeblast")) name = "cubeblast1";
        return Identifier.fromNamespaceAndPath("godweer", "images/world/pt/" + name + ".png");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || !triggers.isEnabled("Тотем") || mc.level == null) return;

        if (event.getPacket() instanceof ClientboundEntityEventPacket packet) {
            if (packet.getEventId() == 35) {
                Entity entity = packet.getEntity(mc.level);
                if (entity instanceof LivingEntity living) {
                    spawnTotemParticles(living);
                }
            }
        }
    }

    private void spawnTotemParticles(LivingEntity target) {
        Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
        int total = (int) (count.getValue() * 1.5);
        float baseSpread = this.spread.getValue();
        float baseSpeed = this.speed.getValue();
        float pSize = this.size.getValue();
        float pLife = this.lifetime.getValue();
        Identifier tex = getSelectedTexture();

        for (int i = 0; i < total; i++) {
            double u = ThreadLocalRandom.current().nextDouble();
            double v = ThreadLocalRandom.current().nextDouble();
            double theta = u * 2.0 * Math.PI;
            double phi = Math.acos(2.0 * v - 1.0);
            double spd = (0.05 + ThreadLocalRandom.current().nextDouble() * 0.08) * baseSpread * baseSpeed;

            double vx = Math.sin(phi) * Math.cos(theta) * spd;
            double vy = Math.cos(phi) * spd;
            double vz = Math.sin(phi) * Math.sin(theta) * spd;

            boolean isGreen = ThreadLocalRandom.current().nextBoolean();
            int color = isGreen ? ColorUtil.rgba(85, 255, 110, 255) : ColorUtil.rgba(255, 225, 60, 255);

            particles.add(new ImageParticle(
                    center.add((ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3,
                            (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3,
                            (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3),
                    new Vec3(vx, vy, vz),
                    pSize * (0.85F + ThreadLocalRandom.current().nextFloat() * 0.4F),
                    pLife * (0.9F + ThreadLocalRandom.current().nextFloat() * 0.4F),
                    color,
                    tex
            ));
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!isEnabled() || !triggers.isEnabled("Атака") || event.getTarget() == null) return;

        Entity target = event.getTarget();
        Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
        int spawnCount = this.count.getValue().intValue();
        float baseSpread = this.spread.getValue();
        float baseSpeed = this.speed.getValue();
        float pSize = this.size.getValue();
        float pLife = this.lifetime.getValue();
        Identifier tex = getSelectedTexture();
        int baseColor = Theme.getAccentColor();

        for (int i = 0; i < spawnCount; i++) {
            double u = ThreadLocalRandom.current().nextDouble();
            double v = ThreadLocalRandom.current().nextDouble();
            double theta = u * 2.0 * Math.PI;
            double phi = Math.acos(2.0 * v - 1.0);
            double spd = (0.06 + ThreadLocalRandom.current().nextDouble() * 0.08) * baseSpread * baseSpeed;

            double vx = Math.sin(phi) * Math.cos(theta) * spd;
            double vy = Math.cos(phi) * spd;
            double vz = Math.sin(phi) * Math.sin(theta) * spd;

            particles.add(new ImageParticle(
                    center.add((ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3,
                            (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3,
                            (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3),
                    new Vec3(vx, vy, vz),
                    pSize * (0.8F + ThreadLocalRandom.current().nextFloat() * 0.4F),
                    pLife * (0.85F + ThreadLocalRandom.current().nextFloat() * 0.3F),
                    baseColor,
                    tex
            ));
        }
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;

        Identifier tex = getSelectedTexture();
        int baseColor = Theme.getAccentColor();
        float baseSpread = this.spread.getValue();
        float baseSpeed = this.speed.getValue();
        float pSize = this.size.getValue();
        float pLife = this.lifetime.getValue();

        if (triggers.isEnabled("Ходьба")) {
            Vec3 curPos = mc.player.position();
            if (lastPlayerPos != null) {
                double dist = curPos.distanceTo(lastPlayerPos);
                if (dist > 0.05) {
                    int spawnAmount = this.countWalk.getValue().intValue();
                    for (int i = 0; i < spawnAmount; i++) {
                        double spawnY = curPos.y + (ThreadLocalRandom.current().nextDouble() * mc.player.getBbHeight());
                        Vec3 spawnPos = new Vec3(
                                curPos.x + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.45 * baseSpread,
                                spawnY,
                                curPos.z + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.45 * baseSpread
                        );

                        double pSpd = baseSpeed * 0.025;
                        Vec3 motion = new Vec3(
                                (ThreadLocalRandom.current().nextDouble() - 0.5) * pSpd,
                                (ThreadLocalRandom.current().nextDouble() - 0.5) * pSpd,
                                (ThreadLocalRandom.current().nextDouble() - 0.5) * pSpd
                        );

                        particles.add(new ImageParticle(spawnPos, motion, pSize * 0.75F, pLife * 0.75F, baseColor, tex));
                    }
                }
            }
            lastPlayerPos = curPos;
        }

        if (triggers.isEnabled("Снаряды")) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof Projectile projectile && !projectile.onGround() && projectile.isAlive()) {
                    Vec3 projPos = projectile.position();
                    Vec3 delta = projectile.getDeltaMovement();

                    if (delta.lengthSqr() > 0.01) {
                        int projBurst = Math.max(2, (int) (count.getValue() / 10));
                        for (int i = 0; i < projBurst; i++) {
                            double u = ThreadLocalRandom.current().nextDouble();
                            double v = ThreadLocalRandom.current().nextDouble();
                            double theta = u * 2.0 * Math.PI;
                            double phi = Math.acos(2.0 * v - 1.0);
                            double spd = (0.04 + ThreadLocalRandom.current().nextDouble() * 0.06) * baseSpread * baseSpeed;

                            double vx = Math.sin(phi) * Math.cos(theta) * spd - delta.x * 0.05;
                            double vy = Math.cos(phi) * spd - delta.y * 0.05;
                            double vz = Math.sin(phi) * Math.sin(theta) * spd - delta.z * 0.05;

                            float quickLife = (0.4F + ThreadLocalRandom.current().nextFloat() * 0.25F);

                            particles.add(new ImageParticle(
                                    projPos.add((ThreadLocalRandom.current().nextDouble() - 0.5) * 0.2,
                                            (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.2,
                                            (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.2),
                                    new Vec3(vx, vy, vz),
                                    pSize * 0.65F,
                                    quickLife,
                                    baseColor,
                                    tex
                            ));
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        Minecraft mc = event.getClient();
        if (mc == null || mc.level == null || mc.player == null || particles.isEmpty()) return;

        var extractor = event.getGuiGraphicsExtractor();
        if (extractor == null) return;

        float dt = event.getDeltaTracker().getGameTimeDeltaTicks();
        float actualDt = Math.max(0.001F, Math.min(0.05F, dt * 0.05F));
        boolean useGravity = this.physics.getValue();
        boolean useGlow = this.glow.getValue();

        Matrix3x2fStack pose = extractor.pose();

        Iterator<ImageParticle> it = particles.iterator();
        while (it.hasNext()) {
            ImageParticle p = it.next();
            if (p.update(actualDt, useGravity)) {
                it.remove();
                continue;
            }

            Render3DUtil.ScreenPoint screenPos = Render3DUtil.projectToScreen(mc, p.pos);
            if (screenPos == null) continue;

            double dist = mc.player.getEyePosition().distanceTo(p.pos);
            if (dist <= 0.2) continue;

            float distanceFactor = (float) Math.max(0.25, Math.min(2.5, 6.0 / dist));
            float renderSize = p.size * distanceFactor;
            float halfSize = renderSize / 2.0F;

            float progress = p.life / p.maxLife;
            float alpha = (float) Math.sin((1.0F - progress) * Math.PI * 0.5);
            int color = ColorUtil.multiplyAlpha(p.color, alpha);

            pose.pushMatrix();
            pose.translate(screenPos.x(), screenPos.y());
            pose.rotate((float) Math.toRadians(p.rotation));

            if (useGlow) {
                float glowSize = renderSize * 1.8F;
                float halfGlow = glowSize / 2.0F;
                int glowColor = ColorUtil.multiplyAlpha(p.color, alpha * 0.35F);
                Render2D.drawTexture(GLOW_TEXTURE, -halfGlow, -halfGlow, glowSize, glowSize, 0.0F, glowColor);
            }

            Render2D.drawTexture(p.texture, -halfSize, -halfSize, renderSize, renderSize, 0.0F, color);

            pose.popMatrix();
        }
    }
}