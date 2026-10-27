package pub.godweer.auxiliary.client.clients;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pub.godweer.Client;
import pub.godweer.cc.RayTraceUtils;
import pub.godweer.kitty.sosiski.friend.FriendManager;
import pub.godweer.kitty.module.impl.combat.AuraModule;
import pub.godweer.kitty.module.settings.impl.MultiModeSetting;

import java.util.ArrayList;
import java.util.List;

import static pub.godweer.IMinecraft.mc;

/**
 * Create by daun kvass
 */
public final class Targets {

    @Getter
    private static LivingEntity target;
    @Getter
    @Setter
    private static LivingEntity lastTarget;
    @Getter
    private static boolean targetLocked = false;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    public static boolean isNaked(Player player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!player.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Дистанция от глаз игрока до ближайшей точки хитбокса цели.
     * Используется и здесь, и в AttackHandler#isInAttackRange, чтобы поиск цели
     * и проверка досягаемости удара не расходились между собой.
     */
    private static double distanceSq(LivingEntity target) {
        return RayTraceUtils.getClosestDistanceSqr(mc.player.getEyePosition(), target.getBoundingBox());
    }

    /** Прямая видимость: один рейкаст до центра груди (дешевле трёх точек). */
    private static boolean canSee(LivingEntity target, Vec3 eyes, AABB box) {
        double cx = box.minX + (box.maxX - box.minX) * 0.5D;
        double cz = box.minZ + (box.maxZ - box.minZ) * 0.5D;
        double cy = box.minY + (box.maxY - box.minY) * 0.65D;
        return RayTraceUtils.canSeePoint(eyes, new Vec3(cx, cy, cz));
    }

    /**
     * Скоринг: ближе — лучше, видимый через стены — хуже,
     * цель «в спине» — худшая. Без этого аура цеплялась за ближайшего
     * за стеной игрока и не била того, кто реально стоит перед прицелом.
     */
    private static double score(LivingEntity candidate, Vec3 eyes, Vec3 look, double range, boolean requireVisibility) {
        AABB box = candidate.getBoundingBox();
        boolean visible = canSee(candidate, eyes, box);
        if (requireVisibility && !visible) return Double.MAX_VALUE;

        double dist = Math.sqrt(distanceSq(candidate));

        Vec3 center = box.getCenter();
        Vec3 to = center.subtract(eyes);
        double length = to.length();
        double angle = 0.0D;
        if (length > 1.0E-4D) {
            double cos = Math.max(-1.0D, Math.min(1.0D, look.dot(to) / length));
            angle = Math.acos(cos); // радианы: 0 — точно в прицеле, PI — за спиной
        }

        double score = dist + angle * 2.0D;
        if (!visible) {
            score += Math.max(6.0D, range); // штраф за цель за стеной
        }
        return score;
    }

    public static boolean isValidTarget(Entity entity, double range, MultiModeSetting filter) {
        if (mc.player == null || entity == null || entity == mc.player) return false;
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isDeadOrDying() || living.isRemoved()) return false;
        if (living.isSpectator()) return false;
        if (distanceSq(living) > (range * range)) return false;

        if (entity instanceof Player player) {
            boolean isFriend = FriendManager.getInstance().isFriend(player);
            boolean friendsEnabled = filter.isEnabled("Friends") || filter.isEnabled("Друзья");
            if (isFriend) return friendsEnabled;

            boolean playersEnabled = filter.isEnabled("Players") || filter.isEnabled("Игроки");
            boolean nakedEnabled = filter.isEnabled("Naked") || filter.isEnabled("Голые");
            // «Голые» — отдельная категория: игрок без брони бьётся и без включённых «Игроков»,
            // но при включённых «Игроках» голые не требуют отдельной галочки.
            if (playersEnabled) return true;
            return nakedEnabled && isNaked(player);
        }

        if (filter.isEnabled("Villagers") && (entity instanceof Villager || entity instanceof IronGolem)) return true;
        if ((filter.isEnabled("Mobs") || filter.isEnabled("Мобы")) && (entity instanceof Enemy)) return true;
        if ((filter.isEnabled("Animals") || filter.isEnabled("Животные")) &&
                (entity instanceof Animal || entity instanceof WaterAnimal || entity instanceof AmbientCreature)) return true;

        return false;
    }

    public static LivingEntity findTarget(double range, MultiModeSetting filter) {
        if (mc.player == null || mc.level == null) {
            reset();
            return null;
        }

        lastTarget = target;

        // Без «Through Walls» аура всё равно не сможет ударить сквозь блок,
        // поэтому такие цели не набираем вовсе.
        AuraModule aura = AuraModule.INSTANCE;
        boolean throughWalls = aura != null && aura.getThroughWalls().getValue();
        boolean crosshairOnly = aura != null && aura.isNoneRotation();

        double rangeSq = range * range;
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 look = RayTraceUtils.getLookVec(mc.player.getYRot(), mc.player.getXRot());
        LivingEntity current = target;

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double bestAngle = Double.MAX_VALUE;
        double currentScore = Double.MAX_VALUE;

        // Секции чанков вокруг игрока вместо полного прохода по
        // entitiesForRendering() — вне радиуса ауры сущности не нужны.
        List<LivingEntity> nearby = mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(range + 1.0D), e -> e != mc.player);

        if (crosshairOnly) {
            // None (TriggerBot): берём только того, на кого реально смотрит прицел —
            // иначе штраф угла мал и выбирается сосед, по которому raytrace не проходит
            for (LivingEntity living : nearby) {
                if (!isValidTarget(living, range, filter)) {
                    if (living == current) current = null;
                    continue;
                }

                AABB box = living.getBoundingBox();
                Vec3 to = box.getCenter().subtract(eyes);
                double len = to.length();
                double angle = len < 1.0E-4D
                        ? 0.0D
                        : Math.acos(Math.max(-1.0D, Math.min(1.0D, look.dot(to) / len)));

                if (!RayTraceUtils.isLookingAt(living, range,
                        mc.player.getYRot(), mc.player.getXRot(), !throughWalls)) {
                    if (living == current) current = null;
                    continue;
                }
                if (angle < bestAngle) {
                    bestAngle = angle;
                    best = living;
                }
            }

            target = best;
            targetLocked = (target != null);
            return target;
        }

        // Кандидаты с базовым скором (дистанция + угол) без единого рейкаста;
        // рейкаст стен делаем только пока базовый скор кандидата может побить
        // лучший итоговый — после сортировки все последующие итогово не хуже.
        record Candidate(LivingEntity entity, double base) {}
        List<Candidate> scored = new ArrayList<>(nearby.size());

        for (LivingEntity living : nearby) {
            if (!isValidTarget(living, range, filter)) {
                if (living == current) current = null;
                continue;
            }

            double distSq = distanceSq(living);
            if (distSq > rangeSq) {
                if (living == current) current = null;
                continue;
            }

            Vec3 to = living.getBoundingBox().getCenter().subtract(eyes);
            double len = to.length();
            double angle = len < 1.0E-4D
                    ? 0.0D
                    : Math.acos(Math.max(-1.0D, Math.min(1.0D, look.dot(to) / len)));

            scored.add(new Candidate(living, Math.sqrt(distSq) + angle * 2.0D));
        }

        scored.sort(java.util.Comparator.comparingDouble(Candidate::base));

        for (Candidate candidate : scored) {
            if (candidate.base() >= bestScore) break;
            LivingEntity living = candidate.entity();
            boolean visible = canSee(living, eyes, living.getBoundingBox());
            if (!visible && !throughWalls) continue;

            double score = candidate.base();
            if (!visible) score += Math.max(6.0D, range);

            if (living == current) currentScore = score;
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }

        if (!crosshairOnly && current != null && current != best
                && isValidTarget(current, range, filter)) {
            // Привязка: почти равную цель не меняем, а лучшую за блоком игнорируем —
            // иначе disengage/re-engage каждый тик и потеря удара каждые пару секунд
            if (currentScore <= bestScore + 1.5D || best == null) {
                best = current;
            }
        }

        target = best;
        targetLocked = (target != null);

        return target;
    }

    public static LivingEntity findTarget(LivingEntity currentTarget, double range, MultiModeSetting filter) {
        if (currentTarget != null && target == null) {
            target = currentTarget;
        }
        return findTarget(range, filter);
    }

    public static void unlockTarget() {
        target = null;
        targetLocked = false;
    }

    public static void reset() {
        target = null;
        lastTarget = null;
        targetLocked = false;
    }

    public static void lockTarget() {
        if (target != null) {
            targetLocked = true;
        }
    }

    public static LivingEntity getCurrentTarget() {
        if (target != null && target.isAlive() && !target.isRemoved()) {
            return target;
        }

        if (mc.player == null || mc.level == null) return null;

        if (Client.INSTANCE != null && Client.INSTANCE.moduleManager != null) {
            AuraModule aura = (AuraModule) Client.INSTANCE.moduleManager.getAuraModule();
            if (aura != null && aura.isEnabled() && aura.getTarget() != null && aura.getTarget().isAlive()) {
                return aura.getTarget();
            }
        }

        return null;
    }

}