package pub.godweer.mixin.input;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.Client;
import pub.godweer.util.player.MoveBlockUtility;
import pub.godweer.rotation.RotationHandler;
import pub.godweer.event.list.PlayerInputEvent;
import pub.godweer.module.impl.combat.KillAura;
import pub.godweer.module.impl.combat.KillAura1;
import pub.godweer.module.impl.combat.KillAura2;
import pub.godweer.module.impl.combat.KillAura3;
import pub.godweer.module.impl.misc.FreeCam;
import pub.godweer.module.impl.misc.ServerHelper;
import pub.godweer.module.impl.movement.NoForward;

/**
 * Create by daun kvass
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @ModifyExpressionValue(
            method = "tick",
            at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;")
    )
    public Input tickInput(Input original) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && RotationHandler.isActive()) {
            return transform(original);
        }
        return original;
    }

    @Unique
    private Input transform(Input input) {
        Minecraft mc = Minecraft.getInstance();
        float z = refrection(input.forward(), input.backward());
        float x = refrection(input.left(), input.right());

        float playerYaw = RotationHandler.isActive() ? RotationHandler.getFreeYaw() : mc.player.getYRot();
        float deltaYaw = playerYaw - RotationHandler.getServerYaw();
        float radians = deltaYaw * 0.017453292F;

        float newX = x * Mth.cos(radians) - z * Mth.sin(radians);
        float newZ = z * Mth.cos(radians) + x * Mth.sin(radians);

        int movementSideways = Math.round(newX);
        int movementForward = Math.round(newZ);

        return new Input(
                movementForward > 0,
                movementForward < 0,
                movementSideways > 0,
                movementSideways < 0,
                input.jump(),
                input.shift(),
                input.sprint()
        );
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onInputTickHead(CallbackInfo ci) {
        MoveBlockUtility.onTick();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onPlayerInputTick(CallbackInfo ci) {
        if (FreeCam.INSTANCE != null && FreeCam.INSTANCE.isEnabled()) {
            this.keyPresses = new Input(false, false, false, false, false, false, false);
            this.moveVector = new Vec2(0.0F, 0.0F);
            return;
        }

        if (MoveBlockUtility.isFrozen()) {
            this.moveVector = new Vec2(0.0F, 0.0F);
            this.keyPresses = new Input(false, false, false, false, false, this.keyPresses.shift(), false);
        } else if (MoveBlockUtility.isSprintBlocked()) {
            Input cur = this.keyPresses;
            this.keyPresses = new Input(cur.forward(), cur.backward(), cur.left(), cur.right(), false, cur.shift(), false);
        }

        PlayerInputEvent event = new PlayerInputEvent((KeyboardInput) (Object) this, this.keyPresses, this.getMoveVector());
        Client.getInstance().getEventManager().call(event);
        this.keyPresses = event.getKeyPresses();
        this.moveVector = event.getMoveVector();

        // "Both" mode: also suppress forward movement locally, keeping the move
        // vector consistent with the modified input (backward/sideways/jump/sneak/sprint intact).
        NoForward noForward = NoForward.INSTANCE;
        if (noForward != null && noForward.clientSuppressed() && this.keyPresses != null && this.keyPresses.forward()) {
            this.keyPresses = NoForward.stripForward(this.keyPresses);
            this.moveVector = new Vec2(
                    refrection(this.keyPresses.left(), this.keyPresses.right()),
                    refrection(false, this.keyPresses.backward())
            ).normalized();
        }

        // Сброс спринта KillAura/KillAura1/KillAura2/KillAura3 и легитного ServerHelper: гасим только forward.
        // Пропавший импульс вперёд ваниль сама читает в aiStep (hasForwardImpulse
        // == false) и роняет спринт, отправляя STOP_SPRINTING; клавишу спринта и
        // флаг не подменяем. Делаем это здесь, а не в слушателе события: гашение
        // не должно зависеть от того, отработали ли остальные подписчики
        // PlayerInputEvent.
        KillAura aura = KillAura.INSTANCE;
        KillAura1 aura1 = KillAura1.INSTANCE;
        KillAura2 aura2 = KillAura2.INSTANCE;
        KillAura3 aura3 = KillAura3.INSTANCE;
        ServerHelper helper = ServerHelper.INSTANCE;
        boolean sprintHold = (aura != null && aura.sprintHoldActive())
                || (aura1 != null && aura1.sprintHoldActive())
                || (aura2 != null && aura2.sprintHoldActive())
                || (aura3 != null && aura3.sprintHoldActive())
                || (helper != null && helper.sprintHoldActive());
        if (sprintHold && this.keyPresses != null) {
            Input cur = this.keyPresses;
            if (cur.forward()) {
                this.keyPresses = new Input(false, cur.backward(), cur.left(), cur.right(),
                        cur.jump(), cur.shift(), cur.sprint());
            }
            if (this.moveVector != null && this.moveVector.y > 0.0F) {
                this.moveVector = new Vec2(this.moveVector.x, 0.0F);
            }
        }
    }

    private static float refrection(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        }
        return positive ? 1.0F : -1.0F;
    }
}
