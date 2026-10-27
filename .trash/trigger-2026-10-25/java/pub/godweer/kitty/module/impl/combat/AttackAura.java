package pub.godweer.kitty.module.impl.combat;

import lombok.Getter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import pub.godweer.cc.RayTraceUtils;
import pub.godweer.cc.RotationHandler;
import pub.godweer.cc.UBoxPoints;
import pub.godweer.cc.UBoxPoints.TargetPoint;
import pub.godweer.cc.rotations.SmoothRotation;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.PlayerInputEvent;
import pub.godweer.enjoy.event.list.PlayerTickEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.*;
import pub.godweer.kitty.sosiski.friend.FriendManager;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AttackAura — плавная Smooth-ротация плюс тайминг удара (наведение, заряд,
 * отстой спринта, криты) и разбивание щита топором.
 */
@Getter
public class AttackAura extends Module {
    public static AttackAura INSTANCE;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // Дистанции
    private final SliderSetting attackRange = slider("Attack Range", 3.0f, 1.0f, 6.0f, 0.1f);
    private final SliderSetting aimRange = slider("Aim Range", 4.5f, 1.0f, 8.0f, 0.1f);
    private final ModeSetting rotMode = mode("Rotation", "Smooth", "Smooth", "None");
    private final MultiModeSetting targets = multiMode("Targets", List.of("Players"),
            "Players", "Mobs", "Animals", "Naked", "Friends", "Villagers");

    // Тайминг удара
    private final HeaderSetting hitHeader = header("Hit Timing");
    private final SliderSetting hoverTime = slider("Hover Time", 200.0f, 0.0f, 1000.0f, 10.0f);
    private final SliderSetting hitDelay = slider("Attack Delay", 550.0f, 200.0f, 900.0f, 10.0f);
    // Заряд, при котором бьём. 1.0 — ждать полного тикера плюс 1-2 тика запаса:
    // на границе тика сервер ещё не считает удар готовым («замах есть, урона нет»).
    private final SliderSetting hitCharge = slider("Hit Charge", 0.90f, 0.50f, 1.00f, 0.01f);

    // Криты и спринт
    private final HeaderSetting critHeader = header("Criticals");
    private final ModeSetting critMode = mode("Crit Mode", "Smart", "Smart", "Only Crits");
    private final SliderSetting minFallDistance = slider("Min Fall Distance", 0.10f, 0.05f, 0.60f, 0.05f);
    private final ModeSetting sprintReset = mode("Sprint Reset", "Legit", "None", "Legit", "Wait Stop");

    // Дополнительно
    private final HeaderSetting extraHeader = header("Settings");
    private final CheckBox pauseWhileUsing = checkbox("Pause while Using", true);
    private final CheckBox throughWalls = checkbox("Through Walls", false);
    private final CheckBox axeShieldBreak = checkbox("Axe Shield Break", false);

    private final SmoothRotation smooth = new SmoothRotation();

    private LivingEntity target = null;

    private LivingEntity hoverTarget = null;
    private long hoverStartMs = -1L;
    private long lastAttackMs = 0L;
    private long nextDelayMs = 550L;

    private int waitCooldownTicks = 0;
    private int shieldTicks = 0;
    private int axeStage = 0;
    private int previousSlot = -1;

    // Сколько тиков ещё держим ввод без forward/sprint (легитный отстой) и сколько
    // тиков уже стоим — удар уходит только после 1-2 тиков реальной остановки.
    private int sprintReleaseTicks = 0;
    private int sprintReleaseHoldTicks = 0;
    private int requiredLeadTicks = 1;

    private int descentTicks = 0;

    public AttackAura() {
        super("AttackAura", "Плавная Smooth-ротация с таймингом удара, критами и сбросом спринта", Category.COMBAT);
        INSTANCE = this;
    }

    public boolean isNoneRotation() {
        return rotMode.getValue().equalsIgnoreCase("None");
    }

    public boolean isLegitSprintReset() {
        return sprintReset.getValue().equalsIgnoreCase("Legit");
    }

    @Override
    public void onEnable() {
        lastAttackMs = 0L;
    }

    @Override
    public void onDisable() {
        target = null;
        hoverTarget = null;
        hoverStartMs = -1L;
        shieldTicks = 0;
        waitCooldownTicks = 0;
        sprintReleaseTicks = 0;
        sprintReleaseHoldTicks = 0;
        requiredLeadTicks = 1;
        descentTicks = 0;
        restoreAxeSlot();
        smooth.reset();
        RotationHandler.disengage("Instant");
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (!inGame() || player() == null || gameMode() == null) return;
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;

        tickTimers();

        boolean noneRot = isNoneRotation();
        double findRange = noneRot ? attackRange.get() : Math.max(attackRange.get(), aimRange.get());
        target = findTarget(findRange, noneRot);

        if (target == null) {
            resetHover();
            RotationHandler.disengage(noneRot ? "Instant" : "Smooth");
            return;
        }

        if (noneRot) {
            // Trigger-режим: камеру не трогаем, бьём только когда прицел уже на цели
            if (RotationHandler.isActive()) RotationHandler.clear();
            boolean aimed = RayTraceUtils.isLookingAt(target, attackRange.get(),
                    player().getYRot(), player().getXRot(), !throughWalls.getValue());
            processAttack(aimed);
            return;
        }

        TargetPoint point = UBoxPoints.getBestPoint(target);
        if (point == null) {
            RotationHandler.disengage("Smooth");
            return;
        }

        // Цель мигает за краем блока — ротация продолжает вести прицел, удар
        // просто пропускается: disengage стоил бы re-engage таксу на каждом мерцании.
        if (!throughWalls.getValue() && !point.isVisible()) {
            smooth.tick(player(), target, point.point(), false);
            return;
        }

        // Удар проверяется по ТЕКУЩЕМУ серверному углу — до того, как ротация сдвинет
        // его в этом тике, иначе били бы по углу, который сервер ещё не получил.
        processAttack(RayTraceUtils.isLookingAt(target, attackRange.get(),
                RotationHandler.isActive() ? RotationHandler.getServerYaw() : player().getYRot(),
                RotationHandler.isActive() ? RotationHandler.getServerPitch() : player().getXRot(),
                !throughWalls.getValue()));

        smooth.tick(player(), target, point.point(), canAttackSoon());
    }

    private void processAttack(boolean aimed) {
        LocalPlayer player = player();
        if (player == null || target == null) return;
        if (isPausedByUsing()) return;

        if (!aimed) {
            resetHover();
            return;
        }

        long now = System.currentTimeMillis();
        if (hoverTarget != target) {
            hoverTarget = target;
            hoverStartMs = now;
            shieldTicks = 0;
        }
        if (now - hoverStartMs < hoverTime.get()) return;
        if (now - lastAttackMs < nextDelayMs) return;

        // --- разбивание щита топором ---
        if (axeShieldBreak.getValue()) {
            if (target.isBlocking()) shieldTicks++;
            else shieldTicks = 0;

            if (shieldTicks >= 5 && axeStage == 0) {
                int slot = findAxeSlot();
                int current = player.getInventory().getSelectedSlot();
                if (slot != -1 && slot != current) {
                    previousSlot = current;
                    setSlot(slot);
                    axeStage = 1;
                    return; // один тик на смену слота, удар в следующем
                }
            }
        }

        boolean exception = isCritException(player);
        boolean falling = !exception && isFallingForCrit(player);
        if (critMode.getValue().equalsIgnoreCase("Only Crits") && !falling && !exception) return;

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && player.getCooldowns().isOnCooldown(mainHand)) return;

        float required = falling ? Math.min(hitCharge.get(), 0.86F) : hitCharge.get();
        boolean bypassCharge = axeStage == 1;
        if (!bypassCharge && player.getAttackStrengthScale(0.0F) < required) return;

        if (!bypassCharge && required >= 0.999F) {
            if (waitCooldownTicks == 0) {
                waitCooldownTicks = 1 + ThreadLocalRandom.current().nextInt(2);
                return;
            }
            if (--waitCooldownTicks > 0) return;
        }

        // --- сброс спринта под крит ---
        if (falling && player.isSprinting()) {
            if (sprintReset.getValue().equalsIgnoreCase("Wait Stop")) return;
            if (isLegitSprintReset() && !readyAfterStop(player)) return;
        }

        attack(player, falling);
    }

    private void attack(LocalPlayer player, boolean falling) {
        lastAttackMs = System.currentTimeMillis();
        nextDelayMs = (long) hitDelay.get() + ThreadLocalRandom.current().nextInt(0, 51);

        if (falling && isLegitSprintReset() && isMovingForward(player)) {
            // После удара стоим ещё 1-2 тика: сервер должен видеть замедление и вокруг
            // самого удара, а не только до него.
            sprintReleaseTicks = Math.max(sprintReleaseTicks, 1 + ThreadLocalRandom.current().nextInt(2));
        } else {
            sprintReleaseTicks = 0;
            sprintReleaseHoldTicks = 0;
        }

        // Локальную симуляцию удара считаем без спринта, чтобы клиентские критовые
        // партиклы совпали с серверным результатом. Движение этот тик не меняем.
        boolean sprinting = player.isSprinting();
        player.setSprinting(false);
        gameMode().attack(player, target);
        player.setSprinting(sprinting);

        SwingAnimation swingAnim = player.getMainHandItem().getAttackAnimation();
        player.swing(InteractionHand.MAIN_HAND, swingAnim, false);

        restoreAxeSlot();
        smooth.onAttack();
    }

    /**
     * Убираем forward/sprint из ввода на время отстоя. Гасим и moveVector: keyPresses
     * уходит в пакет ввода и в логику спринта, а реальную остановку сервер видит только
     * по пакетам движения, т.е. по погашенному moveVector.
     */
    @EventTarget
    public void onInput(PlayerInputEvent event) {
        if (!isEnabled() || sprintReleaseTicks <= 0 || player() == null) return;

        Input current = event.getKeyPresses();
        if (current == null || (!current.forward() && !current.sprint())) return;

        event.setKeyPresses(new Input(
                false,
                current.backward(),
                current.left(),
                current.right(),
                current.jump(),
                current.shift(),
                false
        ));
        Vec2 move = event.getMoveVector();
        if (move != null && move.y > 0.0F) {
            event.setMoveVector(new Vec2(move.x, 0.0F));
        }
    }

    // ------------------------------------------------------------------ таймеры

    private void tickTimers() {
        if (sprintReleaseTicks > 0) {
            sprintReleaseTicks--;
            sprintReleaseHoldTicks++;
        } else {
            sprintReleaseHoldTicks = 0;
        }

        LocalPlayer player = player();
        // Считается именно снижение: набор высоты после отрыва — это ~6 тиков,
        // и «нахожусь в воздухе» серверу про крит ничего не гарантирует.
        if (player == null || player.onGround() || player.getDeltaMovement().y >= 0.0D) {
            descentTicks = 0;
        } else if (descentTicks < 32) {
            descentTicks++;
        }
    }

    /**
     * Остановка армится ТОЛЬКО когда удар готов по остальным условиям: если идти
     * вперёд — стоим lead тиков без forward/sprint и бьём. Если удар сорвался
     * (цель ушла из прицела) — отстой просто истекает и ходьба возвращается.
     */
    private boolean readyAfterStop(LocalPlayer player) {
        if (!isMovingForward(player)) return true;
        if (sprintReleaseHoldTicks >= requiredLeadTicks) return true;
        if (sprintReleaseTicks <= 0) {
            requiredLeadTicks = ThreadLocalRandom.current().nextInt(1, 3);
            // Ровно lead тиков остановки ДО удара (+1 на сам тик удара);
            // после удара attack() продлит отстой на свои 1-2 тика.
            sprintReleaseTicks = requiredLeadTicks + 1;
        }
        return false;
    }

    private boolean isMovingForward(LocalPlayer player) {
        Input input = player.input != null ? player.input.keyPresses : null;
        return player.isSprinting() || (input != null && input.forward());
    }

    private void resetHover() {
        hoverTarget = null;
        hoverStartMs = -1L;
        waitCooldownTicks = 0;
    }

    // ------------------------------------------------------------------- условия

    private boolean isPausedByUsing() {
        return pauseWhileUsing.getValue() && player().isUsingItem();
    }

    private boolean canAttackSoon() {
        LocalPlayer player = player();
        return player != null && target != null && !isPausedByUsing()
                && isInAttackRange(player, target, attackRange.get());
    }

    private static boolean isCritException(LocalPlayer player) {
        return player.getAbilities().flying
                || player.isFallFlying()
                || player.hasEffect(MobEffects.LEVITATION)
                || player.hasEffect(MobEffects.BLINDNESS)
                || player.hasEffect(MobEffects.SLOW_FALLING)
                || player.isInWater()
                || player.isInLava()
                || player.onClimbable()
                || player.isPassenger();
    }

    /**
     * Окно крита: ванильное (!onGround && fallDistance > 0 && dy < 0) плюс минимум
     * один тик непрерывного снижения — сервер копит свою высоту падения сам и с задержкой.
     */
    private boolean isFallingForCrit(LocalPlayer player) {
        if (player.onGround() || player.getDeltaMovement().y >= 0.0D) return false;
        if (player.onClimbable() || player.isPassenger()) return false;
        if (player.fallDistance < minFallDistance.get()) return false;
        return descentTicks >= 1;
    }

    private static boolean isInAttackRange(LocalPlayer player, LivingEntity target, double range) {
        if (target.getBoundingBox().contains(player.getEyePosition())) return true;
        return RayTraceUtils.getClosestDistanceSqr(player.getEyePosition(), target.getBoundingBox())
                <= range * range;
    }

    // --------------------------------------------------------------------- цели

    private LivingEntity findTarget(double range, boolean crosshairOnly) {
        LocalPlayer player = player();
        if (player == null || level() == null) return null;

        Vec3 eyes = player.getEyePosition();
        Vec3 look = RayTraceUtils.getLookVec(player.getYRot(), player.getXRot());
        boolean checkWalls = !throughWalls.getValue();

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range + 1.0D), e -> e != player)) {
            if (!isValid(living, range)) continue;

            double angle = angleTo(eyes, look, living.getBoundingBox().getCenter());
            double score;
            if (crosshairOnly) {
                // Берём только того, на кого реально смотрит прицел: без этого штраф
                // угла мал и выбирается сосед, по которому raytrace не проходит
                if (!RayTraceUtils.isLookingAt(living, range, player.getYRot(), player.getXRot(), checkWalls)) {
                    continue;
                }
                score = angle;
            } else {
                if (checkWalls && !canSee(living, eyes)) continue;
                score = Math.sqrt(RayTraceUtils.getClosestDistanceSqr(eyes, living.getBoundingBox()))
                        + angle * 2.0D;
            }

            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }

        // Привязка: почти равную цель не меняем, иначе re-engage такса и потеря удара
        if (!crosshairOnly && target != null && best != null && target != best
                && isValid(target, range)
                && scoreOf(target, eyes, look, range, checkWalls) <= bestScore + 1.5D) {
            best = target;
        }

        return best;
    }

    private double scoreOf(LivingEntity living, Vec3 eyes, Vec3 look, double range, boolean checkWalls) {
        if (checkWalls && !canSee(living, eyes)) return Double.MAX_VALUE;
        double dist = Math.sqrt(RayTraceUtils.getClosestDistanceSqr(eyes, living.getBoundingBox()));
        return dist + angleTo(eyes, look, living.getBoundingBox().getCenter()) * 2.0D;
    }

    private static double angleTo(Vec3 eyes, Vec3 look, Vec3 center) {
        Vec3 to = center.subtract(eyes);
        double length = to.length();
        if (length < 1.0E-4D) return 0.0D;
        return Math.acos(Mth.clamp(look.dot(to) / length, -1.0D, 1.0D));
    }

    /** Прямая видимость: один рейкаст до центра груди — дешевле скана всех точек. */
    private static boolean canSee(LivingEntity living, Vec3 eyes) {
        AABB box = living.getBoundingBox();
        Vec3 chest = new Vec3(
                box.minX + (box.maxX - box.minX) * 0.5D,
                box.minY + (box.maxY - box.minY) * 0.65D,
                box.minZ + (box.maxZ - box.minZ) * 0.5D
        );
        return RayTraceUtils.canSeePoint(eyes, chest);
    }

    private boolean isValid(LivingEntity living, double range) {
        LocalPlayer player = player();
        if (player == null || living == player) return false;
        if (!living.isAlive() || living.isDeadOrDying() || living.isRemoved()) return false;
        if (living.isSpectator()) return false;
        if (RayTraceUtils.getClosestDistanceSqr(player.getEyePosition(), living.getBoundingBox())
                > range * range) {
            return false;
        }

        if (living instanceof Player other) {
            if (FriendManager.getInstance().isFriend(other)) return targets.isEnabled("Friends");
            if (targets.isEnabled("Players")) return true;
            // «Голые» — отдельная категория: игрок без брони бьётся и без «Игроков»,
            // но при включённых «Игроках» отдельная галочка не нужна.
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

    // -------------------------------------------------------------------- топор

    private static int findAxeSlot() {
        LocalPlayer player = mc.player;
        if (player == null) return -1;
        List<ItemStack> hotbar = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < Math.min(9, hotbar.size()); i++) {
            if (hotbar.get(i).is(ItemTags.AXES)) return i;
        }
        return -1;
    }

    private void setSlot(int slot) {
        LocalPlayer player = player();
        if (player == null || slot < 0 || slot > 8 || player.connection == null) return;
        player.getInventory().setSelectedSlot(slot);
        player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private void restoreAxeSlot() {
        if (axeStage != 1) return;
        if (previousSlot != -1) setSlot(previousSlot);
        previousSlot = -1;
        axeStage = 0;
        shieldTicks = 0;
    }
}
