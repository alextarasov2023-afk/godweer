package pub.godweer.kitty.module.impl.combat;

import lombok.Getter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import pub.godweer.cc.Angle;
import pub.godweer.cc.RayTraceUtils;
import pub.godweer.cc.RotationHandler;
import pub.godweer.cc.UBoxPoints;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.PlayerTickEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.*;
import pub.godweer.kitty.sosiski.friend.FriendManager;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Легитный AimAssist — доворачивает прицел в сторону цели, никогда не делает снап:
 * цель берётся из конуса обзора, точка прицела — из мультипоинтов хитбокса, а сам
 * поворот проходит через нелинейную аппроксимацию (степенная кривая + инерция по осям
 * + разгон + мёртвая зона + GCD мыши + дрожание).
 */
@Getter
public class AimAssist extends Module {
    public static AimAssist INSTANCE;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // [НАСТРОЙКА] Конус захвата и дистанция поиска цели
    private final SliderSetting fov = slider("FOV", 8.0f, 1.0f, 45.0f, 0.5f);
    private final SliderSetting range = slider("Range", 8.0f, 1.0f, 24.0f, 0.5f);
    // [НАСТРОЙКА] Кого вести
    private final MultiModeSetting targets = multiMode("Targets", List.of("Players"),
            "Players", "Mobs", "Animals", "Naked", "Friends", "Villagers");
    // [НАСТРОЙКА] Точка прицела: Multipoint — 20 точек хитбокса, лучшая по углу и видимая
    private final ModeSetting aimPoint = mode("Aim Point", "Multipoint",
            "Multipoint", "Head", "Chest", "Middle", "Legs");
    // [НАСТРОЙКА] Когда работает ассист
    private final ModeSetting activation = mode("Activation", "Always",
            "Always", "While Attacking", "While Moving", "In Air");
    // [НАСТРОЙКА] Позволить вести через блоки
    private final CheckBox throughWalls = checkbox("Through Walls", false);
    // [НАСТРОЙКА] Не переключать цель раньше этого времени (защита от мерцания)
    private final SliderSetting switchDelay = slider("Switch Delay", 250.0f, 0.0f, 1500.0f, 10.0f);

    // [НАСТРОЙКА] Сила доворота за тик, % от полного угла
    private final SliderSetting strength = slider("Strength", 55.0f, 5.0f, 100.0f, 1.0f);
    // [НАСТРОЙКА] Нелинейность: >1 — плавно тормозит у цели, <1 — резче в начале
    private final SliderSetting curve = slider("Curve", 1.40f, 0.40f, 3.00f, 0.05f);
    // [НАСТРОЙКА] Окно, внутри которого сила плавно гаснет до нуля
    private final SliderSetting falloff = slider("Falloff", 14.0f, 2.0f, 45.0f, 0.5f);
    // [НАСТРОЙКА] Потолок скорости, градусов за тик
    private final SliderSetting maxSpeed = slider("Max Speed", 7.0f, 1.0f, 25.0f, 0.5f);
    // [НАСТРОЙКА] Инерция: сглаживание шага по осям (0 — мгновенно, 95 — ватно)
    private final SliderSetting smoothing = slider("Smoothing", 65.0f, 0.0f, 95.0f, 1.0f);
    // [НАСТРОЙКА] Питч крутится медленнее рысканья
    private final SliderSetting pitchRatio = slider("Pitch Ratio", 80.0f, 0.0f, 120.0f, 1.0f);
    // [НАСТРОЙКА] Разгон: столько тиков ассист выходит на полную силу (квадратично)
    private final SliderSetting rampTicks = slider("Ramp Ticks", 4.0f, 0.0f, 20.0f, 1.0f);
    // [НАСТРОЙКА] Мёртвая зона: внутри неё прицел не дёргается
    private final SliderSetting deadzone = slider("Deadzone", 0.15f, 0.0f, 3.00f, 0.05f);
    // [НАСТРОЙКА] Дрожание руки, градусов
    private final SliderSetting jitter = slider("Jitter", 0.10f, 0.0f, 1.00f, 0.01f);
    // [НАСТРОЙКА] Квантовать шаг под GCD сенсы — как настоящие движения мыши
    private final CheckBox quantizeGcd = checkbox("Quantize GCD", true);
    // [НАСТРОЙКА] Опережение точки по движению цели
    private final SliderSetting lead = slider("Lead", 0.25f, 0.0f, 1.00f, 0.05f);

    private LivingEntity target;
    private long switchBlockedUntilMs = 0L;
    private int engagedTicks = 0;
    private float velYaw = 0.0F;
    private float velPitch = 0.0F;

    public AimAssist() {
        super("AimAssist", "Легитный доворот прицела: мультипоинты, нелинейное сглаживание, GCD", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        clear();
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;

        LocalPlayer player = player();
        if (player == null || level() == null) {
            clear();
            return;
        }
        // Аура/ротация уже владеют углом — не воюем с ней.
        if (RotationHandler.isActive()) {
            clear();
            return;
        }
        if (!activeNow(player)) {
            clear();
            return;
        }

        Vec3 eyes = player.getEyePosition();
        LivingEntity hit = pickTarget(player, eyes);
        if (hit == null) {
            clear();
            return;
        }

        Vec3 point = aimPosition(hit, eyes);
        if (point == null) return;

        Angle aim = Angle.fromVec(eyes, point);
        float deltaYaw = Mth.wrapDegrees(aim.getYaw() - player.getYRot());
        float deltaPitch = aim.getPitch() - player.getXRot();
        float error = (float) Math.hypot(deltaYaw, deltaPitch);

        // Мёртвая зона: не доворачиваем то, что и так попали, иначе прицел звенит.
        if (error < deadzone.get()) {
            velYaw *= 0.35F;
            velPitch *= 0.35F;
            return;
        }

        // Нелинейность: сила падает как степень от доли ошибки, оставшейся до цели.
        float t = Mth.clamp(error / Math.max(1.0F, falloff.get()), 0.0F, 1.0F);
        double factor = (strength.get() / 100.0D) * Math.pow(t, curve.get());
        double ramp = ramp();

        float wantYaw = (float) (deltaYaw * factor * ramp);
        float wantPitch = (float) (deltaPitch * factor * ramp * (pitchRatio.get() / 100.0D));

        // Инерция: экспоненциальное сглаживание шага по каждой оси.
        float smooth = smoothing.get() / 100.0F;
        velYaw = Mth.lerp(smooth, wantYaw, velYaw);
        velPitch = Mth.lerp(smooth, wantPitch, velPitch);

        float limit = maxSpeed.get() * (float) ramp;
        velYaw = Mth.clamp(velYaw, -limit, limit);
        velPitch = Mth.clamp(velPitch, -limit, limit);

        float jitterAmount = jitter.get();
        if (jitterAmount > 0.0F) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            velYaw += (float) random.nextDouble(-jitterAmount, jitterAmount);
            velPitch += (float) random.nextDouble(-jitterAmount, jitterAmount);
        }

        if (quantizeGcd.getValue()) {
            velYaw = quantize(velYaw);
            velPitch = quantize(velPitch);
        }

        if (Math.abs(velYaw) < 1.0E-4F && Math.abs(velPitch) < 1.0E-4F) return;

        float newYaw = Mth.wrapDegrees(player.getYRot() + velYaw);
        float newPitch = Mth.clamp(player.getXRot() + velPitch, -90.0F, 90.0F);
        player.setYRot(newYaw);
        player.setXRot(newPitch);
        player.yHeadRot = newYaw;
        engagedTicks++;
    }

    // ------------------------------------------------------------------ сглаживание

    /** Квадратичный ease-in: первый тик почти ничего, дальше — доворот до полной силы. */
    private double ramp() {
        float total = rampTicks.get();
        if (total <= 0.0F) return 1.0D;
        double p = Math.min(1.0D, (engagedTicks + 1.0D) / total);
        return p * p;
    }

    private static float quantize(float delta) {
        if (mc.options == null) return delta;
        double sens = mc.options.sensitivity().get() * 0.6D + 0.2D;
        double gcd = sens * sens * sens * 1.2D;
        if (gcd < 1.0E-4D) return delta;
        return (float) (Math.round(delta / gcd) * gcd);
    }

    private boolean activeNow(LocalPlayer player) {
        return switch (activation.getValue()) {
            case "While Attacking" -> mc.options != null && mc.options.keyAttack.isDown();
            case "While Moving" -> isMoving(player);
            case "In Air" -> !player.onGround();
            default -> true;
        };
    }

    private static boolean isMoving(LocalPlayer player) {
        Input keys = player.input != null ? player.input.keyPresses : null;
        if (keys != null && (keys.forward() || keys.backward() || keys.left() || keys.right())) {
            return true;
        }
        return Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) > 0.005D;
    }

    // ----------------------------------------------------------------------- цели

    /**
     * Липкая цель: пока текущая жива, в радиусе и внутри конуса — не ищем новую,
     * иначе ассист начинает дёргаться между двумя игроками. После потери цели
     * переключение блокируется на Switch Delay; ванильный пик прицела не считается
     * мерцанием — это сам игрок навёл.
     */
    private LivingEntity pickTarget(LocalPlayer player, Vec3 eyes) {
        double lockFov = fov.get();
        double lockRange = range.get();

        if (target != null
                && (!acceptable(target) || beyond(target, eyes, lockRange) || angleTo(eyes, target) > lockFov)) {
            release();
        }
        if (target != null) return target;

        if (!throughWalls.getValue()
                && mc.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity living
                && living != player && acceptable(living)
                && angleTo(eyes, living) <= lockFov && !beyond(living, eyes, lockRange)) {
            return engage(living);
        }

        if (System.currentTimeMillis() < switchBlockedUntilMs) return null;

        Vec3 dir = RayTraceUtils.getLookVec(player.getYRot(), player.getXRot());
        AABB area = player.getBoundingBox().inflate(lockRange + 1.0D);

        LivingEntity best = null;
        double bestAngle = lockFov;
        double bestDist = Double.MAX_VALUE;

        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, area, e -> e != player)) {
            if (!acceptable(living)) continue;

            double angle = angleTo(eyes, living);
            if (angle > lockFov) continue;

            // Центр за спиной тоже формально «в конусе» по углу — отрезаем по лучу взгляда.
            Vec3 toCenter = living.getBoundingBox().getCenter().subtract(eyes);
            if (toCenter.dot(dir) <= 0.0D) continue;

            double dist = toCenter.length();
            if (angle < bestAngle - 0.5D || (angle <= bestAngle + 0.5D && dist < bestDist)) {
                bestAngle = angle;
                bestDist = dist;
                best = living;
            }
        }

        return best == null ? null : engage(best);
    }

    private LivingEntity engage(LivingEntity entity) {
        target = entity;
        engagedTicks = 0;
        velYaw = 0.0F;
        velPitch = 0.0F;
        return entity;
    }

    private void release() {
        target = null;
        engagedTicks = 0;
        velYaw = 0.0F;
        velPitch = 0.0F;
        switchBlockedUntilMs = System.currentTimeMillis() + (long) switchDelay.get();
    }

    private static boolean beyond(LivingEntity entity, Vec3 eyes, double range) {
        return RayTraceUtils.getClosestDistanceSqr(eyes, entity.getBoundingBox()) > range * range;
    }

    private static double angleTo(Vec3 eyes, LivingEntity entity) {
        Angle aim = Angle.fromVec(eyes, entity.getBoundingBox().getCenter());
        LocalPlayer player = mc.player;
        if (player == null) return Double.MAX_VALUE;
        return Math.hypot(Mth.wrapDegrees(aim.getYaw() - player.getYRot()),
                aim.getPitch() - player.getXRot());
    }

    // -------------------------------------------------------------- точка прицела

    private Vec3 aimPosition(LivingEntity entity, Vec3 eyes) {
        Vec3 point;

        if (aimPoint.getValue().equals("Multipoint")) {
            UBoxPoints.TargetPoint best = UBoxPoints.getBestPoint(entity);
            if (best == null) return null;
            if (!best.isVisible() && !throughWalls.getValue()) return null;
            point = best.point();
        } else {
            AABB box = entity.getBoundingBox();
            double height = box.maxY - box.minY;
            double frac = switch (aimPoint.getValue()) {
                case "Head" -> 0.92D;
                case "Chest" -> 0.70D;
                case "Legs" -> 0.28D;
                default -> 0.55D;
            };
            point = new Vec3(box.minX + (box.maxX - box.minX) * 0.5,
                    box.minY + height * frac,
                    box.minZ + (box.maxZ - box.minZ) * 0.5);
            if (!throughWalls.getValue() && !RayTraceUtils.canSeePoint(eyes, point)) return null;
        }

        double leadAmount = lead.get();
        if (leadAmount > 0.0D) {
            Vec3 velocity = new Vec3(entity.getX() - entity.xo,
                    entity.getY() - entity.yo,
                    entity.getZ() - entity.zo);
            if (velocity.lengthSqr() > 1.0E-6D) {
                double ticks = eyes.distanceTo(point) * 0.05D;
                double scale = Math.min(2.5D, leadAmount * ticks);
                point = point.add(velocity.scale(scale));
            }
        }
        return point;
    }

    private void clear() {
        target = null;
        engagedTicks = 0;
        velYaw = 0.0F;
        velPitch = 0.0F;
    }

    // ------------------------------------------------------------------- фильтры

    private boolean acceptable(LivingEntity living) {
        if (!living.isAlive() || living.isDeadOrDying() || living.isRemoved()) return false;
        if (living.isSpectator()) return false;

        if (living instanceof Player other) {
            if (FriendManager.getInstance().isFriend(other)) return targets.isEnabled("Friends");
            if (targets.isEnabled("Players")) return true;
            return targets.isEnabled("Naked") && isNaked(other);
        }

        if (targets.isEnabled("Villagers") && (living instanceof Villager || living instanceof IronGolem)) {
            return true;
        }
        if (targets.isEnabled("Mobs") && living instanceof Enemy) return true;
        return targets.isEnabled("Animals")
                && (living instanceof Animal || living instanceof WaterAnimal
                || living instanceof AmbientCreature);
    }

    private static boolean isNaked(Player player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!player.getItemBySlot(slot).isEmpty()) return false;
        }
        return true;
    }
}
