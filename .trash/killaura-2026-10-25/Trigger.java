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
import pub.godweer.enjoy.mixin.accessor.ILocalPlayer;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.*;
import pub.godweer.kitty.sosiski.friend.FriendManager;

import java.util.List;

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
    // Кулдаун между ударами в тиках: 520-570 мс => ровно 11 тиков (550 мс). Гейт по
    // счётчику тиков, а не по wall-clock: иначе удар уходил на первом тике ПОСЛЕ
    // истечения окна и окно раздувалось до 620 мс.
    private final SliderSetting attackDelay = slider("Attack Delay", 520.0f, 520.0f, 570.0f, 1.0f);
    // 100% = удар ровно на полном заряде. 92% (=11/12) снимает 1 тик с паузы
    // ценой ~8% урона: полезно, когда кулдаун оружия длиннее Attack Delay.
    private final SliderSetting minCharge = slider("Min Charge", 100.0f, 60.0f, 100.0f, 1.0f);

    private final HeaderSetting critHeader = header("Criticals");
    private final ModeSetting attackMode = mode("Attack Mode", "Smart", "Smart", "Only Crits");
    private final ModeSetting sprintReset = mode("Sprint Reset", "Legit", "None", "Legit", "Wait Stop");
    // Сколько тиков падения должно пройти до удара. Пакет позиции текущего тика
    // уходит ПОСЛЕ PRE, поэтому в первом тику падения сервер ещё видит свою
    // копию «земля / fallDistance = 0», его крит-проверка не проходит и удар
    // считается обычным. Раньше это случайно компенсировал фиксированный lead.
    private final SliderSetting critFallTicks = slider("Crit Fall Ticks", 2.0f, 1.0f, 4.0f, 1.0f);

    private final HeaderSetting extraHeader = header("Settings");
    private final CheckBox pauseWhileUsing = checkbox("Pause while Using", true);
    private final CheckBox throughWalls = checkbox("Through Walls", false);
    private final CheckBox axeShieldBreak = checkbox("Axe Shield Break", false);

    private LivingEntity target = null;

    private LivingEntity hoverTarget = null;
    // Абсолютный игровой тик, начиная с которого удар снова разрешён.
    private long readyTick = 0L;

    private int shieldTicks = 0;
    private int axeStage = 0;
    private int previousSlot = -1;

    // Тики падения подряд по состоянию на начало тика (0 = стоим на земле).
    private int fallingTicks = 0;

    // Сколько тиков ещё держим ввод без forward/sprint (легитный отстой). Снимаем не
    // «N тиков», а ровно до тех пор, пока сервер не перестал считать нас бегущими:
    // wasSprinting == false означает, что STOP_SPRINTING уже ушёл в канал.
    private int sprintReleaseTicks = 0;
    // Страховочный потолок: флаг обычно гаснет за 1-2 тика, дольше держать ввод
    // смысла нет — вместо этого отдаём ход и перечимся заново на следующем тике.
    private static final int SPRINT_RELEASE_CAP = 4;
    private static final int SPRINT_RELEASE_REARM = 2;

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
        shieldTicks = 0;
        sprintReleaseTicks = 0;
        fallingTicks = 0;
        restoreAxeSlot();
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (!isEnabled() || player() == null || level() == null || gameMode() == null) return;
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;

        tickTimers();

        LocalPlayer player = player();
        boolean falling = player.fallDistance > 0.0F && !player.onGround();
        fallingTicks = falling ? fallingTicks + 1 : 0;

        if (isPausedByUsing()) return;

        target = raycastTarget();
        if (target == null) {
            resetHover();
            shieldTicks = 0;
            return;
        }

        if (hoverTarget != target) {
            hoverTarget = target;
            shieldTicks = 0;
        }

        if (axeShieldBreak.getValue()) {
            if (target.isBlocking()) shieldTicks++;
            else shieldTicks = 0;
        }

        long gameTime = level().getGameTime();
        if (gameTime < readyTick) return;

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

        boolean exception = isCritException(player);

        // Отстой легитного спринта армимся заранее — на подъёме прыжка. Lead-тики
        // иначе съедают короткое окно падения и крит просто не успевает биться.
        if (!falling && !player.onGround() && isLegitSprintReset()) {
            armSprintReleaseOnRise(player);
        }

        if (!exception) {
            if (attackMode.getValue().equalsIgnoreCase("Only Crits") && !falling) return;
            // Крит бьём только по «созревшему» падению: пока сервер не получил
            // пакет позиции уже в воздухе, его проверка fallDistance > 0 &&
            // !onGround не проходит и удар уходит обычным.
            if (falling && fallingTicks < (int) critFallTicks.get()) return;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && player.getCooldowns().isOnCooldown(mainHand)) return;

        // Заряд: в падении (крит) достаточно 84.8%, на земле и при исключениях —
        // Min Charge. Считаем честно, без +0.5 тика оптимизма: сервер видит свой
        // тикер и удар «раньше готовности» как раз и даёт «замах есть, урона нет».
        float required = (falling && !exception) ? 0.848F : minCharge.get() / 100.0F;
        boolean bypassCharge = axeStage == 1;
        if (!bypassCharge && player.getAttackStrengthScale(0.0F) < required) return;

        // --- сброс спринта под крит: бьём ровно в том тику, где сервер перестал
        // считать нас бегущими. Ждать «N тиков отстоя» не нужно вовсе.
        if (falling && isServerSprinting(player)) {
            String mode = sprintReset.getValue();
            if (mode.equalsIgnoreCase("Wait Stop")) return;
            if (isLegitSprintReset()) {
                keepSprintReleaseArmed(player);
                return;
            }
        }

        attack(player, gameTime);
    }

    private void attack(LocalPlayer player, long gameTime) {
        readyTick = gameTime + delayTicks();

        // После удара отстой не держим: серверный крит считается по последнему
        // пакету движения, то есть по тому, что ушло ДО удара, а лишние тики
        // стояния только отодвигают следующий прыжок и весь кри-цикл.
        sprintReleaseTicks = 0;

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

    /**
     * Attack Delay в тиках: окно 520-570 мс целиком лежит в одном тиковом шаге,
     * поэтому джиттер «+0-50 мс» внутри него физически невозможен, а раньше он
     * превращал 520-570 мс в реальные 550-620 мс (удар всё равно мог уйти только
     * на границе тика).
     */
    private long delayTicks() {
        return Math.max(1L, (long) Math.ceil(attackDelay.get() / 50.0F));
    }

    private void tickTimers() {
        if (sprintReleaseTicks <= 0) return;
        sprintReleaseTicks--;
        // Как только сервер перестал считать нас бегущими (STOP_SPRINTING ушёл),
        // держать снятый ввод незачем — возвращаем ходьбу/спринт.
        LocalPlayer player = player();
        if (player != null && !isServerSprinting(player)) {
            sprintReleaseTicks = 0;
        }
    }

    /**
     * Подъём прыжка: снимаем forward/sprint заранее, пока сервер ещё считает нас
     * бегущими и окно крита не открылось. Держим не фиксированный lead, а до
     * флипа wasSprinting — как только STOP_SPRINTING ушёл, ввод возвращаем.
     */
    private void armSprintReleaseOnRise(LocalPlayer player) {
        if (sprintReleaseTicks > 0) return;
        if (player.getDeltaMovement().y <= 0.0D) return;
        if (!isMovingForward(player) || !isServerSprinting(player)) return;
        sprintReleaseTicks = SPRINT_RELEASE_CAP;
    }

    /**
     * Падем, а сервер всё ещё считает нас бегущими: перечим ввод ещё пару тиков.
     * Удар выпустит тот тик, где isServerSprinting() уже false.
     */
    private void keepSprintReleaseArmed(LocalPlayer player) {
        if (!isMovingForward(player)) return;
        if (sprintReleaseTicks < SPRINT_RELEASE_REARM) {
            sprintReleaseTicks = SPRINT_RELEASE_REARM;
        }
    }

    private boolean isMovingForward(LocalPlayer player) {
        Input input = player.input != null ? player.input.keyPresses : null;
        return player.isSprinting() || (input != null && input.forward());
    }

    /** Спринт таким, каким его видит сервер (wasSprinting), а не локальный флаг. */
    private static boolean isServerSprinting(LocalPlayer player) {
        try {
            return ((ILocalPlayer) player).serverSprintState();
        } catch (Throwable ignored) {
            return player.isSprinting();
        }
    }

    private void resetHover() {
        hoverTarget = null;
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
