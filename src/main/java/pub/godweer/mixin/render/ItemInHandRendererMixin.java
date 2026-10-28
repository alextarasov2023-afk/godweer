package pub.godweer.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.module.impl.render.SwingAnimation;
import pub.godweer.module.impl.render.ViewModel;

/**
 * Точки входа наших анимаций рук в рендер от первого лица.
 *
 * В 26.2 здесь были @Redirect на LocalPlayer.getXRot/getViewXRot/getViewYRot —
 * в 26.3 эти вызовы ушли в FirstPersonHandsAndItems.extractRenderState,
 * см. client.FirstPersonHandsAndItemsMixin.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Shadow @Final
    private Minecraft minecraft;

    /** Рука не падает вниз при смене предмета. */
    @ModifyVariable(method = "applyItemArmTransform", at = @At("HEAD"), argsOnly = true)
    private float keepArmHeight(float inverseArmHeight) {
        SwingAnimation swing = SwingAnimation.INSTANCE;
        if (swing != null && swing.isEnabled() && swing.keepHeight.get()) {
            return 0.0F;
        }
        return inverseArmHeight;
    }

    /** Еда: либо ванильная поза, либо полный её отказ. */
    @Inject(method = "applyEatTransform", at = @At("HEAD"), cancellable = true)
    private void skipEatTransform(PoseStack poseStack, float frameInterp, HumanoidArm arm,
                                  float remainingTicks, int useDuration, CallbackInfo ci) {
        SwingAnimation swing = SwingAnimation.INSTANCE;
        if (swing != null && swing.isEnabled() && swing.noEat.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
    private void applyViewModel(PlayerRenderState state, FirstPersonHandsAndItemsRenderState handsState,
                                float frameInterp, float xRot, InteractionHand hand, float attack,
                                ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
                                SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        ViewModel model = ViewModel.INSTANCE;
        if (model == null || !model.isEnabled()) return;

        LocalPlayer player = this.minecraft.player;
        HumanoidArm mainArm = player != null ? player.getMainArm() : HumanoidArm.RIGHT;
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite();

        ViewModel.Hand transform = model.hand(arm);
        if (transform.empty()) return;

        poseStack.translate(transform.x(), transform.y(), transform.z());
        poseStack.rotateDegrees(Axis.YP, transform.yaw());
        poseStack.rotateDegrees(Axis.XP, transform.pitch());
        poseStack.rotateDegrees(Axis.ZP, transform.roll());
        float scale = transform.scale();
        poseStack.scale(scale, scale, scale);
    }

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void applySwingAnimation(float attack, PoseStack poseStack, int invert,
                                     HumanoidArm arm, CallbackInfo ci) {
        SwingAnimation swing = SwingAnimation.INSTANCE;
        if (swing == null || !swing.handles(arm)) return;
        ci.cancel();
        swing.applySwing(attack, poseStack, invert, arm);
    }
}
