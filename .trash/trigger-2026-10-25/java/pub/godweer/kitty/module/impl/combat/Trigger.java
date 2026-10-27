package pub.godweer.kitty.module.impl.combat;

import lombok.Getter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
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
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import pub.godweer.cc.RayTraceUtils;
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
 * Trigger — удар только по цели под прицелом: камера и ротация не трогаются,
 * бьём ровно тогда, когда игрок сам навёл прицел (рейкаст по reach).
 */
@Getter
public class Trigger extends Module {
    public static Trigger INSTANCE;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final SliderSetting reach = slider("Reach", 3.2f, 1.0f, 6.0f, 0.1f);
    private final MultiModeSetting targets = multiMode("Targets", List.of("Players"),
            "Players", "Mobs", "Animals", "Naked", "Friends", "Villagers");

    private final HeaderSetting timingHeader = header("Hit Timing");
    private final SliderSetting hoverTime = slider("Hover Time", 200.0f, 0.0f, 1000.0f, 10.0f);
    private final SliderSetting attackDelay = slider("Attack Delay", 550.0f, 200.0f, 900.0f, 10.0f);

    private final HeaderSetting critHeader = header("Criticals");
    private final ModeSetting attackMode = mode("Attack Mode", "Smart", "Smart", "Only Crits");
    private final ModeSetting sprintReset = mode("Sprint Reset", "Legit", "None", "Legit", "Wait Stop");

    private final HeaderSetting extraHeader = header("Settings");
    private final CheckBox pauseWhileUsing = checkbox("Pause while Using", true);
    private final CheckBox throughWalls = checkbox("Through Walls", false);
    private final CheckBox axeShieldBreak = checkbox("Axe Shield Break", false);

    private LivingEntity target = null;

    private LivingEntity hoverTarget = null;
    private long hoverStartMs = -1L;
    private long lastAttackMs = 0L;
    private long nextDelayMs = 550L;

    private int shieldTicks = 0;
    private int waitCooldownTicks = 0;
    private int axeStage = 0;
    private int previousSlot = -1;

    // Сколько тиков ещё держим ввод без forward/sprint (легитный отстой) и сколько
    // тиков уже стоим — удар уходит только после 1-2 тиков реальной остановки.
    private int sprintReleaseTicks = 0;
    private int sprintReleaseHoldTicks = 0;
    private int requiredLeadTicks = 1;

    public Trigger() {
        super("Trigger", "Удар по цели под прицелом: криты, отстой спринта, смена на топор", Category.COMBAT);
        INSTANCE = this;
    }

    public boolean isLegitSprintReset() {
        return sprintReset.getValue().equalsIgnoreCase("Legit");
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
        restoreAxeSlot();
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (!isEnabled() || player() == null || level() == null || gameMode() == null) return;
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;

        tickTimers();

        if (isPausedByUsing()) return;

        target = raycastTarget();
        if (target == null) {
            resetHover();
            shieldTicks = 0;
            return;
        }

        long now = System.currentTimeMillis();
        if (hoverTarget != target) {
            hoverTarget = target;
            hoverStartMs = now;
            shieldTicks = 0;
        }

        if (axeShieldBreak.getValue()) {
            if (target.isBlocking()) shieldTicks++;
            else shieldTicks = 0;
        }

        if (now - hoverStartMs < hoverTime.get()) return;
        if (now - lastAttackMs < nextDelayMs) return;

        // --- смена на топор, если цель долго держит щит ---
        if (axeShieldBreak.getValue() && shieldTicks >= 5 && axeStage == 0) {
            int slot = findAxeSlot();
            int current = player().getInventory().getSelectedSlot();
            if (slot != -1 && slot != current) {
                previousSlot = current;
                setSlot(slot);
                axeStage = 1;
                return; // один тик на смену слота, удар в следующем
            }
        }

        LocalPlayer player = player();
        boolean exception = isCritException(player);
        boolean falling = player.fallDistance > 0.0F && !player.onGround();

        if (!exception) {
            if (attackMode.getValue().equalsIgnoreCase("Only Crits") && !falling) return;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && player.getCooldowns().isOnCooldown(mainHand)) return;

        // Заряд: в падении (крит) достаточно 84.8%, на земле и при исключениях — полный.
        // Считаем честно, без +0.5 тика оптимизма: сервер видит свой тикер и удар
        // «раньше готовности» как раз и даёт «замах есть, урона нет».
        float required = (falling && !exception) ? 0.848F : 1.0F;
        boolean bypassCharge = axeStage == 1;
        if (!bypassCharge && player.getAttackStrengthScale(0.0F) < required) return;

        // Полный заряд: ждём ещё 1-2 тика, чтобы сервер точно увидел удар готовым
        if (!bypassCharge && required >= 1.0F) {
            if (waitCooldownTicks == 0) {
                waitCooldownTicks = 1 + ThreadLocalRandom.current().nextInt(2);
                return;
            }
            if (--waitCooldownTicks > 0) return;
        }

        // --- сброс спринта под крит ---
        if (falling && player.isSprinting()) {
            String mode = sprintReset.getValue();
            if (mode.equalsIgnoreCase("Wait Stop")) return;
            if (isLegitSprintReset() && !readyAfterStop(player)) return;
        }

        attack(player, falling);
    }

    private void attack(LocalPlayer player, boolean falling) {
        lastAttackMs = System.currentTimeMillis();
        nextDelayMs = (long) attackDelay.get() + ThreadLocalRandom.current().nextInt(0, 51);
        waitCooldownTicks = 0;

        if (falling && isLegitSprintReset() && isMovingForward(player)) {
            // После удара стоим ещё 1-2 тика: сервер должен видеть замедление и вокруг
            // самого удара, а не только до него.
            sprintReleaseTicks = Math.max(sprintReleaseTicks, 1 + ThreadLocalRandom.current().nextInt(2));
        } else {
            sprintReleaseTicks = 0;
            sprintReleaseHoldTicks = 0;
        }

        // Локальную симуляцию удара считаем без спринта, чтобы клиентские критовые
        // партиклы совпадали с серверным результатом.
        boolean sprinting = player.isSprinting();
        player.setSprinting(false);
        gameMode().attack(player, target);
        player.setSprinting(sprinting);

        SwingAnimation swingAnim = player.getMainHandItem().getAttackAnimation();
        player.swing(InteractionHand.MAIN_HAND, swingAnim, false);

        restoreAxeSlot();
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
    }

    /**
     * Остановка армится ТОЛЬКО когда удар готов по остальным условиям: если идти
     * вперёд — стоим lead тиков без forward/sprint и бьём. Если удар сорвался —
     * отстой просто истекает и ходьба возвращается.
     */
    private boolean readyAfterStop(LocalPlayer player) {
        if (!isMovingForward(player)) return true;
        if (sprintReleaseHoldTicks >= requiredLeadTicks) return true;
        if (sprintReleaseTicks <= 0) {
            requiredLeadTicks = ThreadLocalRandom.current().nextInt(1, 3);
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

    private boolean isPausedByUsing() {
        return pauseWhileUsing.getValue() && player().isUsingItem();
    }

    private static boolean isCritException(LocalPlayer player) {
        return player.getAbilities().flying
                || player.isFallFlying()
                || player.hasEffect(MobEffects.BLINDNESS)
                || player.isInWater()
                || player.isInLava()
                || player.onClimbable()
                || player.isPassenger();
    }

    // --------------------------------------------------------------------- цели

    /**
     * Рейкаст по прицелу: из глаз вдоль look-вектора на reach, с клипом стен.
     * Берём ближайшую цель, в чей хитбокс реально попадает луч — ровно так же,
     * как это делает ваниль для удара.
     */
    private LivingEntity raycastTarget() {
        LocalPlayer player = player();
        if (player == null || level() == null) return null;

        double range = reach.get();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        boolean checkWalls = !throughWalls.getValue();
        Vec3 eyes = player.getEyePosition();

        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range + 1.0D), e -> e != player)) {
            if (!isValid(living, range)) continue;
            if (!RayTraceUtils.isLookingAt(living, range, yaw, pitch, checkWalls)) continue;

            double distance = RayTraceUtils.getClosestDistanceSqr(eyes, living.getBoundingBox());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }

        return best;
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
            // «Голые» — отдельная категория: игрок без брони бьётся и без «Игроков»
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
