package pub.godweer.cc;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import pub.godweer.kitty.module.impl.combat.AuraModule;

import java.util.concurrent.ThreadLocalRandom;

import static pub.godweer.IMinecraft.mc;

public final class AttackHandler {

    // Чисто косметическая «очеловечивающая» задержка поверх тикового кулдауна.
    // Сам тайминг удара задаёт кулдаун (85-90%), поэтому здесь лишь небольшой
    // рандом, чтобы удары не уходили идеально равномерно.
    private static final long MIN_DELAY_MS = 120L;
    private static final long MAX_DELAY_MS = 180L;

    /**
     * Сколько тиков ПОДРЯД игрок именно снижается (dy < 0), чтобы крит считался
     * видимым серверу. Считается падение, а не полёт: набор высоты после отрыва
     * — это ~6 тиков, и «нахожусь в воздухе» серверу ничего не гарантирует.
     * Плюс удар уходит на PRE-тике, т.е. ещё до пакетов движения этого тика,
     * значит сервер судит по движению предыдущего тика + его опережение на лаг.
     */
    private static final int MIN_CRIT_DESCENT_TICKS = 1;

    private static long lastAttackTime = 0L;
    private static long nextAttackDelayMs = 500L;
    private static float nextFallCooldownThreshold = 0.875F;

    // --- сброс спринта под крит ---
    // Legit (ввод): на время отстоя убираем из ввода forward/sprint, и ваниль САМА
    // выключает спринт (canStartSprinting/shouldStopRunSprinting) и шлёт
    // STOP_SPRINTING, корректно ведя LocalPlayer.wasSprinting. Движение и состояние
    // сервера остаются согласованными — никакого ручного пакетного десинхрона.
    // Packet: историчный режим — вручную STOP_SPRINTING/START_SPRINTING.
    private static int sprintReleaseTicks = 0;   // Legit: сколько тиков ещё не спринтуем
    private static int sprintRestoreTicks = 0;   // Packet: отстой до возврата спринта
    // Legit: сколько тиков ПОДРЯД уже стоим (forward/sprint убраны из ввода) —
    // удар уходит только после 1-2 тиков такой «остановки в ходьбе»
    private static int sprintReleaseHoldTicks = 0;
    private static int requiredLeadTicks = 1;

    // --- окно падения ---
    private static int descentTicks = 0;
    private static float minCritFallDistance = 0.15F;
    private static int lagTicks = 0;

    private AttackHandler() {
    }

    // ---------------------------------------------------------------- состояние

    public static void reset() {
        endSprintReset();
        lastAttackTime = 0L;
        descentTicks = 0;
        rollNextTimings();
    }

    /**
     * Тиковые таймеры: сброс спринта, счётчик тиков снижения и актуальный лаг.
     * Вызывается раз в тик на PRE, до поиска цели и сборки ввода.
     */
    public static void tickTimers() {
        if (sprintReleaseTicks > 0) {
            sprintReleaseTicks--;
            sprintReleaseHoldTicks++;
        } else {
            sprintReleaseHoldTicks = 0;
        }
        tickSprintReset();

        LocalPlayer player = mc.player;
        // Только НЕПРЕРЫВНОЕ снижение: подъём или приземление обнуляют счётчик.
        if (player == null || player.onGround() || player.getDeltaMovement().y >= 0.0D) {
            descentTicks = 0;
        } else if (descentTicks < 32) {
            descentTicks++;
        }

        AuraModule aura = AuraModule.INSTANCE;
        minCritFallDistance = aura != null ? aura.getMinFallDistance().getValue() : 0.15F;
        lagTicks = estimatedLagTicks();
    }

    /**
     * Задержка в тиках в одну сторону: сервер применяет наше движение с лагом,
     * и его копия fallDistance отстаёт примерно на столько же.
     */
    private static int estimatedLagTicks() {
        int ping = 60;
        try {
            if (mc.getConnection() != null && mc.player != null) {
                PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (info != null) ping = Math.max(0, info.getLatency());
            }
        } catch (Exception ignored) {
        }
        // ~половина RTT в одну сторону, 1 тик = 50 мс → ping/100.
        return Math.min(4, (int) Math.ceil(ping / 100.0D));
    }

    /**
     * Тик отстоя для Packet-режима: по его окончании возвращаем спринт на сервере.
     * Если игрок за это время сам перестал спринтовать — START_SPRINTING не шлём,
     * ваниль сама синхронизирует состояние по своему тику.
     */
    private static void tickSprintReset() {
        if (sprintRestoreTicks <= 0) return;
        if (--sprintRestoreTicks > 0) return;
        sendStartSprinting();
    }

    /** Гасим pending-сброс (отключение ауры) — чтобы сервер не остался без спринта. */
    private static void endSprintReset() {
        sprintReleaseTicks = 0;
        if (sprintRestoreTicks <= 0) return;
        sprintRestoreTicks = 0;
        sendStartSprinting();
    }

    private static void sendStartSprinting() {
        LocalPlayer player = mc.player;
        if (player == null || player.connection == null) return;
        if (!player.isSprinting()) return;
        player.connection.send(new ServerboundPlayerCommandPacket(
                player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
    }

    private static void rollNextTimings() {
        nextAttackDelayMs = ThreadLocalRandom.current().nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1L);
        nextFallCooldownThreshold = ThreadLocalRandom.current().nextFloat(0.80F, 0.86F);
    }

    private static void startSprintReset(LocalPlayer player, AuraModule aura) {
        int min = Math.max(1, Math.round(aura.getMinStopTicks().getValue()));
        int max = Math.max(min, Math.round(aura.getMaxStopTicks().getValue()));
        sprintRestoreTicks = ThreadLocalRandom.current().nextInt(min, max + 1);
        player.connection.send(new ServerboundPlayerCommandPacket(
                player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
    }

    // ---------------------------------------------------------------- спринт

    public static boolean isLegitSprintReset(AuraModule aura) {
        return aura != null && aura.getSprintResetMode().getValue().toLowerCase().startsWith("legit");
    }

    /**
     * Готовим легитный сброс спринта ЗАРАНЕЕ: как только заряд почти добит и цель
     * в диапазоне, отпускаем спринт вводом. К моменту удара сервер уже получил
     * ванильный STOP_SPRINTING, поэтому удар читается как удар без спринта — ровно
     * так, как это делает живой человек.
     */
    /** Движется ли игрок вперёд — спринтом или простой ходьбой (W). */
    private static boolean isMovingForward(LocalPlayer player) {
        return player.isSprinting() || mc.options.keyUp.isDown();
    }

    /** Нужно ли в этом тике убрать из ввода forward/sprint (KeyboardInput). */
    public static boolean wantsSprintRelease() {
        return sprintReleaseTicks > 0;
    }

    // ---------------------------------------------------------------- условия

    public static boolean isWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES)
                || stack.is(Items.TRIDENT)
                || stack.is(Items.MACE);
    }

    public static boolean isInAttackRange(Player player, LivingEntity target, AuraModule aura) {
        if (player == null || target == null || aura == null) return false;
        if (target.getBoundingBox().contains(player.getEyePosition())) return true;
        double distSq = RayTraceUtils.getClosestDistanceSqr(player.getEyePosition(), target.getBoundingBox());
        double range = aura.getAttackRange().getValue();
        return distSq <= (range * range);
    }

    public static boolean isInCobweb(Player player) {
        if (mc.level == null || player == null) return false;
        return mc.level.getBlockState(player.blockPosition()).is(Blocks.COBWEB)
                || mc.level.getBlockState(player.blockPosition().above()).is(Blocks.COBWEB);
    }

    public static boolean isBypassCritCondition(Player player) {
        return player.getAbilities().flying
                || player.hasEffect(MobEffects.LEVITATION)
                || player.hasEffect(MobEffects.BLINDNESS)
                || player.hasEffect(MobEffects.SLOW_FALLING)
                || player.isInWater()
                || player.isUnderWater()
                || player.isInLava()
                || player.onClimbable()
                || player.isPassenger()
                || isInCobweb(player);
    }

    /**
     * Окно крита: ванильное (!onGround && fallDistance > 0 && dy < 0) плюс запас на
     * то, что сервер копит свою высоту падения сам, по пакетам движения и с задержкой.
     */
    public static boolean isFallingForCrit(Player player) {
        if (player.onGround() || player.getDeltaMovement().y >= 0.0D) return false;
        if (player.onClimbable() || player.isPassenger()) return false;
        if (player.fallDistance < minCritFallDistance) return false;
        // 1 тик снижения + максимум 1 тик лага: окно падения короткое, и если
        // ждать лишние тики — прыжок кончается раньше, чем уйдёт крит.
        return descentTicks >= MIN_CRIT_DESCENT_TICKS + Math.min(1, lagTicks);
    }

    /**
     * Заряд удара. Честный (0.0) — как его видит сервер, без «оптимистичных»
     * +0.5 тика (оптимизм и давал удары раньше, чем сервер считает удар готовым).
     */
    public static float swingStrength(LocalPlayer player) {
        return player.getAttackStrengthScale(0.0F);
    }

    /**
     * Порог кулдауна: случайные 80-85% на каждый удар.
     */
    public static float getRequiredCooldown() {
        return Mth.clamp(nextFallCooldownThreshold, 0.80F, 0.85F);
    }

    public static boolean canCritical(LocalPlayer player, AuraModule aura) {
        if (!aura.getCriticals().getValue()) return true;
        if (isBypassCritCondition(player)) return true;
        return isFallingForCrit(player);
    }

    // ---------------------------------------------------------------- удар

    public static boolean shouldAttack(LivingEntity target, AuraModule aura) {
        LocalPlayer player = mc.player;
        if (player == null || target == null || aura == null) return false;

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && player.getCooldowns().isOnCooldown(mainHand)) return false;

        if (!isInAttackRange(player, target, aura)) return false;

        if (System.currentTimeMillis() - lastAttackTime < nextAttackDelayMs) return false;

        float scale = swingStrength(player);
        if (scale < getRequiredCooldown()) return false;

        // Криты: окно падения открыто — бьём СРАЗУ. Свой прыжок (держим пробел):
        // ждём окно, но у самого апекса заранее копим остановку, чтобы окно
        // не тратилось на lead-тики, и не держим удар дольше 0.93 заряда.
        // На земле и без прыжка — сразу по кулдауну.
        if (aura.getCriticals().getValue() && !isBypassCritCondition(player)) {
            if (isFallingForCrit(player)) {
                return armLegitStop(player, aura);
            }
            if (aura.getCritMode().getValue().equalsIgnoreCase("Only Crits")) return false;
            if (!player.onGround() && mc.options.keyJump.isDown()) {
                // у апекса армим остановку сразу, независимо от заряда — окно
                // падения не должно тратить тики на lead-остановку
                if (player.getDeltaMovement().y <= 0.08D) {
                    armLegitStop(player, aura);
                }
                return scale >= 0.90F && armLegitStop(player, aura);
            }
        }

        return armLegitStop(player, aura);
    }

    /**
     * Legit-остановка перед ударом. Армируется ТОЛЬКО когда удар готов по всем
     * остальным условиям: если идти вперёд — стоим без forward/sprint lead
     * тиков (1-2) и бьём; сервер видит остановку и STOP_SPRINTING до удара.
     * Если удар сорвался (цель ушла из прицела и т.п.) — отстой просто
     * истекает и ходьба возвращается. Никакого реарма каждый тик: иначе W
     * блокируется навсегда, пока заряд высокий и цель в прицеле.
     */
    private static boolean armLegitStop(LocalPlayer player, AuraModule aura) {
        if (!aura.getSprintReset().getValue() || !isLegitSprintReset(aura)) return true;
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

    public static void attack(LivingEntity target, AuraModule aura, AuraRotation rotation) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || player.connection == null) return;

        lastAttackTime = System.currentTimeMillis();
        rollNextTimings();

        if (aura != null && aura.getSprintReset().getValue() && isMovingForward(player)) {
            if (isLegitSprintReset(aura)) {
                // Legit: после удара стоим ещё 1-2 тика (ввод без forward/sprint и
                // с погашенным moveVector), ваниль сама разбирается с пакетами и
                // своей копией состояния — сервер видит реальную остановку.
                sprintReleaseTicks = Math.max(sprintReleaseTicks, ThreadLocalRandom.current().nextInt(1, 3));
            } else if (player.isSprinting()) {
                startSprintReset(player, aura);
            }
        }

        // Локальную симуляцию удара считаем без спринта, чтобы клиентские критовые
        // партиклы совпали с серверным результатом. Движение этот тик не меняем:
        // aiStep ещё не выполнялся.
        boolean sprinting = player.isSprinting();
        player.setSprinting(false);
        mc.gameMode.attack(player, target);
        player.setSprinting(sprinting);

        SwingAnimation swingAnim = player.getItemInHand(InteractionHand.MAIN_HAND).getAttackAnimation();
        player.swing(InteractionHand.MAIN_HAND, swingAnim, false);
        player.connection.send(ServerboundPunchPacket.INSTANCE);

        if (rotation != null) {
            rotation.onAttack();
        }
    }
}
