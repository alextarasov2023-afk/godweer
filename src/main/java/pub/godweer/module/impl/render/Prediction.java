package pub.godweer.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.Render3DUtil;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.render.font.MsdfFont;
import pub.godweer.util.render.world.particles.ParticlesWorldRenderer;
import pub.godweer.util.render.world.particles.ParticlesWorldRenderer.Sprite;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.Render2DEvent;
import pub.godweer.event.list.Render3DEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.ColorSetting;
import pub.godweer.module.settings.impl.MultiModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Предсказание полёта собственного снаряда: траектория и точка падения для того,
 * что реально лежит в руке — лук (с учётом текущего заряда), арбалет, трезубец,
 * жемчуг, снежок/яйцо, зелья и бутылки опыта.
 *
 * Считаем той же физикой, что и сам снаряд в 26.3: за тик скорость сначала
 * получает гравитацию, затем инерцию 0.99, и только потом позиция сдвигается.
 * Гравитация разная: у стрелы и трезубца 0.05, у жемчуга и снежка 0.03, у
 * бутылки опыта 0.07. Стартовая скорость тоже берётся из самих предметов:
 * лук 3.0 × заряд, арбалет 3.15 (ракета 1.6), трезубец 2.5, метаемое 1.5.
 *
 * Стартуем от глаз строго по взгляду: снаряд не наследует скорость игрока, а
 * внутренним смещением в четверть блока можно пренебречь — на дистанции в
 * десятки блоков оно ничего не меняет.
 *
 * Столкновение с блоками проверяем подшагами внутри тика: за один тик стрела
 * пролетает до трёх блоков, и цельный луч нашёл бы стену позже, чем она есть.
 *
 * Create by daun kvass
 */
public final class Prediction extends Module {

    private static final double AIR_INERTIA = 0.99D;
    private static final int MAX_DOTS = 30;
    private static final int RING_SPRITES = 24;

    public final MultiModeSetting kinds = multiMode("Считаем",
            List.of("Лук", "Арбалет", "Трезубец", "Жемчуг", "Снежок/яйцо", "Зелья"),
            "Лук", "Арбалет", "Трезубец", "Жемчуг", "Снежок/яйцо", "Зелья");
    public final SliderSetting range = slider("Дальность, тики", 70.0F, 20.0F, 140.0F, 1.0F);
    public final SliderSetting dotSize = slider("Размер точек", 0.16F, 0.04F, 0.5F, 0.01F);
    public final SliderSetting subSteps = slider("Подшаги", 3.0F, 1.0F, 6.0F, 1.0F);
    public final CheckBox impact = checkbox("Точка падения", true);
    public final CheckBox label = checkbox("Дистанция и время", true);
    public final CheckBox entityStop = checkbox("Стоп по сущностям", false);
    public final CheckBox throughWalls = checkbox("Сквозь блоки", true);
    public final CheckBox idlePreview = checkbox("Превью полным зарядом", true);
    public final CheckBox themeColor = checkbox("Color From Theme", true);
    public final ColorSetting color = color("Color", ColorUtil.rgba(255, 170, 45, 255))
            .visible(() -> !themeColor.get());

    private final ParticlesWorldRenderer renderer = new ParticlesWorldRenderer();
    private final List<Sprite> sprites = new ArrayList<>(MAX_DOTS + RING_SPRITES + 4);

    private Vec3 cachedImpact;
    private double cachedTicks;
    private double cachedDistance;
    private String cachedKind = "";

    public Prediction() {
        super("Prediction", "Траектория и точка падения снаряда", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        this.cachedImpact = null;
        this.sprites.clear();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        this.cachedImpact = null;
        if (event.getDeltaTracker() == null || mc.player == null || mc.level == null) return;

        Shot shot = resolve(mc);
        if (shot == null) return;

        int ticks = Math.max(4, (int) this.range.get());
        int steps = Math.max(1, (int) this.subSteps.get());
        boolean stops = this.entityStop.get();
        float size = this.dotSize.get();
        List<LivingEntity> nearby = stops
                ? entitiesAround(mc, shot.origin(), (double) ticks * shot.velocity())
                : List.of();

        Vec3 position = shot.origin();
        Vec3 velocity = shot.direction().scale(shot.velocity());
        Vec3 impactPoint = null;
        int impactTick = 0;
        int dotEvery = Math.max(1, Math.round(ticks / (float) MAX_DOTS));

        this.sprites.clear();
        for (int tick = 0; tick < ticks && impactPoint == null; tick++) {
            velocity = velocity.add(0.0D, -shot.gravity(), 0.0D).scale(AIR_INERTIA);
            for (int step = 0; step < steps; step++) {
                Vec3 next = position.add(velocity.x / steps, velocity.y / steps, velocity.z / steps);
                BlockHitResult block = mc.level.clip(new ClipContext(position, next,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                if (block.getType() != HitResult.Type.MISS) {
                    impactPoint = block.getLocation();
                    break;
                }
                if (stops) {
                    LivingEntity victim = pickEntity(nearby, position, next);
                    if (victim != null) {
                        impactPoint = next;
                        break;
                    }
                }
                position = next;
            }
            if (tick % dotEvery == 0) {
                float progress = (float) tick / ticks;
                this.sprites.add(new Sprite(position, size * (1.0F - progress * 0.35F),
                        ColorUtil.multiplyAlpha(ColorUtil.interpolateColor(first(), second(), progress),
                                1.0F - progress * 0.45F)));
            }
            impactTick = tick;
        }

        if (impactPoint == null) impactPoint = position;

        this.cachedImpact = impactPoint;
        this.cachedTicks = impactTick;
        this.cachedDistance = shot.origin().distanceTo(impactPoint);
        this.cachedKind = shot.kind();

        if (this.impact.get()) {
            int tint = second();
            double radius = 0.42D + size;
            this.sprites.add(new Sprite(impactPoint, size * 1.9F, ColorUtil.multiplyAlpha(tint, 0.55F)));
            for (int i = 0; i < RING_SPRITES; i++) {
                double angle = (Math.PI * 2.0D) * i / RING_SPRITES;
                this.sprites.add(new Sprite(
                        impactPoint.add(Math.cos(angle) * radius, 0.02D, Math.sin(angle) * radius),
                        size * 0.55F,
                        ColorUtil.multiplyAlpha(tint, 0.45F)));
            }
        }

        this.renderer.render(this.sprites, 1.45F, this.throughWalls.get());
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.label.get() || event.getDeltaTracker() == null) return;
        Vec3 impactPoint = this.cachedImpact;
        if (impactPoint == null || mc.player == null) return;

        Render3DUtil.ScreenPoint screen = Render3DUtil.projectToScreen(mc, impactPoint);
        if (screen == null) return;

        MsdfFont font = Fonts.SF_MEDIUM;
        float size = 9.0F;
        String line = String.format(Locale.ROOT, "%s · %.1f бл · %.1f с",
                this.cachedKind, this.cachedDistance, this.cachedTicks / 20.0D);
        float width = font.getWidth(line, size);
        float height = font.textHeight(size);
        float x = screen.x() - (width + 6.0F) * 0.5F;
        float y = screen.y() + 6.0F;

        Render2D.drawRoundedRect(x - 3.0F, y - 2.0F, width + 6.0F, height + 4.0F, 3.0F,
                ColorUtil.rgba(8, 8, 12, 150));
        Fonts.drawString(font, line, x, y, size, first());
    }

    /**
     * Что именно сейчас полетит: сначала то, что реально тянем (лук, арбалет),
     * потом любая рука для метательных предметов.
     */
    private Shot resolve(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return null;

        boolean using = player.isUsingItem();
        ItemStack use = using ? player.getUseItem() : ItemStack.EMPTY;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        Vec3 origin = player.getEyePosition();
        Vec3 direction = direction(player);

        if (use.is(Items.BOW) || (!using && main.is(Items.BOW))) {
            if (!this.kinds.isEnabled("Лук")) return null;
            // Формула ванильного лука: (t² + 2t) / 3 при t = тики заряда / 20.
            float power = using ? BowItem.getPowerForTime(player.getTicksUsingItem()) : 1.0F;
            if (!using && !this.idlePreview.get()) return null;
            return new Shot(origin, direction, 3.0F * power, 0.05D, "Лук");
        }
        if (use.is(Items.CROSSBOW) || (!using && (main.is(Items.CROSSBOW) || off.is(Items.CROSSBOW)))) {
            if (!this.kinds.isEnabled("Арбалет")) return null;
            ItemStack bow = use.is(Items.CROSSBOW) ? use : (main.is(Items.CROSSBOW) ? main : off);
            if (!CrossbowItem.isCharged(bow)) {
                if (!this.idlePreview.get()) return null;
                return new Shot(origin, direction, 3.15F, 0.05D, "Арбалет");
            }
            // Ракета летит заметно слабее стрелы: 1.6 против 3.15.
            ChargedProjectiles loaded = bow.get(DataComponents.CHARGED_PROJECTILES);
            boolean rocket = loaded != null && loaded.contains(Items.FIREWORK_ROCKET);
            return new Shot(origin, direction, rocket ? 1.6F : 3.15F, 0.05D, "Арбалет");
        }
        if (main.is(Items.TRIDENT) || off.is(Items.TRIDENT)) {
            // Трезубец не бросают в воде и верхом на ездовом животном.
            if (!this.kinds.isEnabled("Трезубец") || player.isInWater() || player.isPassenger()) return null;
            return new Shot(origin, direction, 2.5F, 0.05D, "Трезубец");
        }
        if (holding(Items.ENDER_PEARL, main, off)) {
            if (!this.kinds.isEnabled("Жемчуг")) return null;
            return new Shot(origin, direction, 1.5F, 0.03D, "Жемчуг");
        }
        if (holding(Items.SNOWBALL, main, off) || holding(Items.EGG, main, off)) {
            if (!this.kinds.isEnabled("Снежок/яйцо")) return null;
            return new Shot(origin, direction, 1.5F, 0.03D, "Снежок");
        }
        ItemStack thrown = main.isEmpty() ? off : main;
        if (thrown.is(Items.SPLASH_POTION) || thrown.is(Items.LINGERING_POTION)) {
            if (!this.kinds.isEnabled("Зелья")) return null;
            return new Shot(origin, direction, 0.5F, 0.05D, "Зелье");
        }
        if (thrown.is(Items.EXPERIENCE_BOTTLE)) {
            if (!this.kinds.isEnabled("Зелья")) return null;
            return new Shot(origin, direction, 0.5F, 0.07D, "Опыт");
        }
        return null;
    }

    private static Vec3 direction(LocalPlayer player) {
        // Тот же вектор взгляда, что отдаётся серверу: из чистых yaw/pitch.
        float xRotation = -player.getXRot() * Mth.DEG_TO_RAD;
        float yRotation = -player.getYRot() * Mth.DEG_TO_RAD;
        float cosYaw = Mth.cos(yRotation);
        float sinYaw = Mth.sin(yRotation);
        float cosPitch = Mth.cos(xRotation);
        float sinPitch = Mth.sin(xRotation);
        return new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch).normalize();
    }

    private static boolean holding(net.minecraft.world.item.Item item, ItemStack main, ItemStack off) {
        return main.is(item) || off.is(item);
    }

    private List<LivingEntity> entitiesAround(Minecraft mc, Vec3 origin, double reach) {
        double span = Math.min(120.0D, reach + 8.0D);
        AABB box = new AABB(origin.x - span, origin.y - span, origin.z - span,
                origin.x + span, origin.y + span, origin.z + span);
        List<LivingEntity> found = new ArrayList<>();
        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity == mc.player || !entity.isAlive() || entity.isSpectator()) continue;
            found.add(entity);
            if (found.size() >= 48) break;
        }
        return found;
    }

    private static LivingEntity pickEntity(List<LivingEntity> entities, Vec3 from, Vec3 to) {
        for (LivingEntity entity : entities) {
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius() + 0.1D);
            if (box.contains(from) || box.clip(from, to).isPresent()) return entity;
        }
        return null;
    }

    private int first() {
        return this.themeColor.get() ? Theme.getAccentColor() : this.color.getValue();
    }

    private int second() {
        float[] hsv = ColorUtil.toHsv(first());
        return ColorUtil.fromHsv((hsv[0] + 0.12F) % 1.0F, hsv[1], Math.min(1.0F, hsv[2] * 1.15F), 255);
    }

    /** Параметры запуска: точка, направление, скорость за тик, гравитация за тик, что считаем. */
    private record Shot(Vec3 origin, Vec3 direction, float velocity, double gravity, String kind) {
    }
}
