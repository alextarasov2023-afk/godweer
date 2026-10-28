package pub.godweer.module.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.SliderSetting;

/**
 * Create by daun kvass
 */
public final class FreeCam extends Module {
    public static FreeCam INSTANCE;
    public final SliderSetting speed = slider("Speed", 1.2F, 0.1F, 5.0F, 0.1F);

    public double posX, posY, posZ;
    public double prevPosX, prevPosY, prevPosZ;
    public float yaw, pitch;

    public boolean justDisabled = false;
    public double lastCamX, lastCamY, lastCamZ;
    public float lastCamYaw, lastCamPitch;

    public FreeCam() {
        super("FreeCam", "Свободный полёт камеры", Category.MISC);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 eyePos = mc.player.getEyePosition();
        this.posX = this.prevPosX = eyePos.x;
        this.posY = this.prevPosY = eyePos.y;
        this.posZ = this.prevPosZ = eyePos.z;

        this.yaw = mc.player.getYRot();
        this.pitch = mc.player.getXRot();
        this.justDisabled = false;
    }

    @Override
    public void onDisable() {
        this.justDisabled = true;
        this.lastCamX = this.posX;
        this.lastCamY = this.posY;
        this.lastCamZ = this.posZ;
        this.lastCamYaw = this.yaw;
        this.lastCamPitch = this.pitch;
    }

    public void updateMovement() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        float forward = 0.0F;
        float strafe = 0.0F;
        float vertical = 0.0F;

        if (mc.options.keyUp.isDown()) forward += 1.0F;
        if (mc.options.keyDown.isDown()) forward -= 1.0F;
        if (mc.options.keyLeft.isDown()) strafe += 1.0F;
        if (mc.options.keyRight.isDown()) strafe -= 1.0F;
        if (mc.options.keyJump.isDown()) vertical += 1.0F;
        if (mc.options.keyShift.isDown()) vertical -= 1.0F;

        float moveSpeed = this.speed.getValue();
        float vSpeed = this.speed.getValue();

        float rad = this.yaw * ((float) Math.PI / 180.0F);
        float sin = Mth.sin(rad);
        float cos = Mth.cos(rad);

        double dx = (strafe * cos - forward * sin) * (moveSpeed * 0.35D);
        double dz = (forward * cos + strafe * sin) * (moveSpeed * 0.35D);
        double dy = vertical * (vSpeed * 0.35D);

        this.posX += dx;
        this.posY += dy;
        this.posZ += dz;
    }

    public void turn(double deltaYaw, double deltaPitch) {
        this.yaw += (float) deltaYaw * 0.15f;
        this.pitch += (float) deltaPitch*0.15f;
        this.pitch = Mth.clamp(this.pitch , -90.0F, 90.0F);
    }
}