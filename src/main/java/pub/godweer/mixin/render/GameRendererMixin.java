package pub.godweer.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.Client;
import pub.godweer.event.EventManager;
import pub.godweer.event.list.Render3DEvent;
import pub.godweer.module.impl.render.FireHands;
import pub.godweer.module.impl.render.OutlineHands;
import pub.godweer.module.impl.render.ShaderHands;
import pub.godweer.module.impl.render.GlowHands;
import pub.godweer.module.impl.render.Removals;
import pub.godweer.util.RenderExtend;
import pub.godweer.util.render.Render3DUtil;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Create by daun kvass
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    private static final Render3DEvent RENDER_3D_EVENT = new Render3DEvent();

    @Shadow
    @Final
    private Minecraft minecraft;

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
    private Matrix4f captureLevelProjection(Matrix4f projectionMatrix) {
        Render3DUtil.captureLevelProjection(projectionMatrix);
        return projectionMatrix;
    }
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void onRender3D(CallbackInfo ci) {
        // В 26.3 GameRenderer.renderLevel() не принимает DeltaTracker.
        DeltaTracker deltaTracker = this.minecraft.getDeltaTracker();
        RenderExtend.enter3D((GameRenderer) (Object) this, deltaTracker);
        try {
            EventManager.call(RENDER_3D_EVENT.set(this.minecraft, (GameRenderer) (Object) this, deltaTracker));
        } finally {
            RenderExtend.exit3D();
        }
    }
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void onBobHurt(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (Removals.INSTANCE != null && Removals.INSTANCE.isEnabled() && Removals.INSTANCE.getOverlays().isEnabled("HurtCam")) {
            ci.cancel();
        }
    }

    /**
     * В 26.3 vanilla-пасс рук создаётся прямо в renderItemInHand и закрывается сразу
     * после renderAllFeatures, поэтому эффекты рук разделены:
     * - снапшот сцены (копия глубины) — до открытия пасса (encoder свободен);
     * - mask/blur/composite-пассы — в TAIL renderItemInHand, после close().
     */
    @WrapOperation(
            method = "renderItemInHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"
            )
    )
    private RenderPass beforeHandPass(CommandEncoder encoder, Supplier label, GpuTextureView colorView, Optional colorClear, GpuTextureView depthView, OptionalDouble depthClear, Operation<RenderPass> original) {
        ShaderHands shaderModule = Client.INSTANCE.moduleManager.getModule(ShaderHands.class);
        GlowHands glowModule = Client.INSTANCE.moduleManager.getModule(GlowHands.class);
        FireHands fireModule = Client.INSTANCE.moduleManager.getModule(FireHands.class);
        OutlineHands outlineModule = Client.INSTANCE.moduleManager.getModule(OutlineHands.class);

        if (shaderModule != null && shaderModule.isEnabled()) {
            try {
                shaderModule.getRenderer().prepareSnapshot();
            } catch (Throwable ignored) {}
        }
        if (glowModule != null && glowModule.isEnabled()) {
            try {
                glowModule.getRenderer().prepareSnapshot();
            } catch (Throwable ignored) {}
        }
        if (fireModule != null && fireModule.isEnabled()) {
            try {
                fireModule.getRenderer().prepareSnapshot(fireModule);
            } catch (Throwable ignored) {}
        }
        if (outlineModule != null && outlineModule.isEnabled()) {
            try {
                outlineModule.getRenderer().prepareSnapshot(outlineModule);
            } catch (Throwable ignored) {}
        }
        return original.call(encoder, label, colorView, colorClear, depthView, depthClear);
    }

    @Inject(method = "renderItemInHand", at = @At("TAIL"))
    private void afterHandPass(CallbackInfo ci) {
        GlowHands glowModule = Client.INSTANCE.moduleManager.getModule(GlowHands.class);
        ShaderHands shaderModule = Client.INSTANCE.moduleManager.getModule(ShaderHands.class);
        FireHands fireModule = Client.INSTANCE.moduleManager.getModule(FireHands.class);
        OutlineHands outlineModule = Client.INSTANCE.moduleManager.getModule(OutlineHands.class);

        // Порядок композита как в 26.2: внутренний модуль (Glow) первым, Shader вторым.
        if (glowModule != null && glowModule.isEnabled()) {
            try {
                glowModule.getRenderer().compose(glowModule);
            } catch (Throwable ignored) {}
        }
        if (shaderModule != null && shaderModule.isEnabled()) {
            try {
                shaderModule.getRenderer().compose(shaderModule);
            } catch (Throwable ignored) {}
        }
        // FireHands последним: его аддитивный композит поверх всех эффектов рук.
        if (fireModule != null && fireModule.isEnabled()) {
            try {
                fireModule.getRenderer().compose(fireModule);
            } catch (Throwable ignored) {}
        }
        // OutlineHands после FireHands: rim/glow/fire по выбранному стилю.
        if (outlineModule != null && outlineModule.isEnabled()) {
            try {
                outlineModule.getRenderer().compose(outlineModule);
            } catch (Throwable ignored) {}
        }
    }
}
