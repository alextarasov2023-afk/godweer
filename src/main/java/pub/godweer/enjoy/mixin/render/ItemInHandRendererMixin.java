package pub.godweer.enjoy.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.cc.RotationHandler;
import pub.godweer.kitty.module.impl.render.SwingAnimation;
import pub.godweer.kitty.module.impl.render.ViewModel;

/**
 * Create by daun kvass
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Shadow @Final
    private Minecraft minecraft;

    // В 26.2 здесь было три @Redirect на LocalPlayer.getXRot/getViewXRot/getViewYRot.
    // В 26.3 эти вызовы перенесены в FirstPersonHandsAndItems.extractRenderState —
    // см. FirstPersonHandsAndItemsMixin.

    @ModifyVariable(method = "applyItemArmTransform", at = @At("HEAD"), argsOnly = true)
    private float fixReequipDrop(float inverseArmHeight) {
        if (SwingAnimation.INSTANCE != null && SwingAnimation.INSTANCE.isEnabled() && SwingAnimation.INSTANCE.noReequip.getValue()) {
            return 0.0F;
        }
        return inverseArmHeight;
    }

    @Inject(method = "applyEatTransform", at = @At("HEAD"), cancellable = true)
    private void cancelEatTransform(PoseStack poseStack, float frameInterp, HumanoidArm arm, float remainingTicksArg, int useDuration, CallbackInfo ci) {
        if (SwingAnimation.INSTANCE != null && SwingAnimation.INSTANCE.isEnabled() && SwingAnimation.INSTANCE.noEat.getValue()) {
            ci.cancel();

            Player player = this.minecraft.player;
            ItemStack itemStack = player == null ? ItemStack.EMPTY : player.getItemInHand(arm == HumanoidArm.LEFT ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            float remaining = (float) (player == null ? 0 : player.getUseItemRemainingTicks()) - frameInterp + 1.0F;
            float eatProgress = remaining / (float) itemStack.getUseDuration(player);
            float f3 = 1.0F - (float) Math.pow((double) eatProgress, 27.0D);
            int i = arm == HumanoidArm.RIGHT ? 1 : -1;

            poseStack.translate(f3 * 0.6F * (float) i, f3 * -0.5F, 0.0F);
            poseStack.rotate(Axis.YP, (float) i * f3 * 90.0F);
            poseStack.rotate(Axis.XP, f3 * 10.0F);
            poseStack.rotate(Axis.ZP, (float) i * f3 * 30.0F);
        }
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
    private void applyViewModel(PlayerRenderState state, net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState handsState, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        if (ViewModel.INSTANCE != null && ViewModel.INSTANCE.isEnabled()) {
            LocalPlayer localPlayer = this.minecraft.player;
            HumanoidArm mainArm = localPlayer != null ? localPlayer.getMainArm() : HumanoidArm.RIGHT;
            boolean isMainHand = hand == InteractionHand.MAIN_HAND;
            HumanoidArm arm = isMainHand ? mainArm : mainArm.getOpposite();
            if (arm == HumanoidArm.RIGHT) {
                poseStack.translate(ViewModel.INSTANCE.rightX.getValue(), ViewModel.INSTANCE.rightY.getValue(), ViewModel.INSTANCE.rightZ.getValue());
                float scale = ViewModel.INSTANCE.rightScale.getValue();
                poseStack.scale(scale, scale, scale);
            } else {
                poseStack.translate(ViewModel.INSTANCE.leftX.getValue(), ViewModel.INSTANCE.leftY.getValue(), ViewModel.INSTANCE.leftZ.getValue());
                float scale = ViewModel.INSTANCE.leftScale.getValue();
                poseStack.scale(scale, scale, scale);
            }
        }
    }

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void onSwingArm(float attack, PoseStack poseStack, int invert, HumanoidArm arm, CallbackInfo ci) {
        if (SwingAnimation.INSTANCE != null && SwingAnimation.INSTANCE.isEnabled()) {
            if (arm == HumanoidArm.LEFT && !SwingAnimation.INSTANCE.offhand.getValue()) {
                return;
            }
            ci.cancel();
            SwingAnimation.INSTANCE.applySwing(attack, poseStack, invert, arm);
        }
    }
}