package ez.falseclient.mixins.render;

import ez.falseclient.system.features.misc.FreeCam;
import ez.falseclient.system.features.movement.GrimExploit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean thirdPerson;

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void false$freeCamRotation(Args args) {
        FreeCam freeCam = FreeCam.getInstance();
        if (freeCam != null && freeCam.isEnabled()) {
            float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true);
            args.set(0, freeCam.getCameraYaw(tickDelta));
            args.set(1, freeCam.getCameraPitch(tickDelta));
            thirdPerson = true;
        }
    }

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V"))
    private void false$freeCamPosition(Args args) {
        FreeCam freeCam = FreeCam.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (freeCam != null && freeCam.isEnabled()) {
            float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
            args.set(0, freeCam.getCameraX(tickDelta));
            args.set(1, freeCam.getCameraY(tickDelta));
            args.set(2, freeCam.getCameraZ(tickDelta));
            return;
        }

        GrimExploit grimExploit = GrimExploit.getInstance();
        if (grimExploit != null && grimExploit.shouldApplyVisualSmoothing()) {
            float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
            Vec3d pos = grimExploit.getVisualCameraPosition(tickDelta);
            args.set(0, pos.x);
            args.set(1, pos.y);
            args.set(2, pos.z);
        }
    }
}
