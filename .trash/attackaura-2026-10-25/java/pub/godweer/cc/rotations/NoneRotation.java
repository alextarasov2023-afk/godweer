package pub.godweer.cc.rotations;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.cc.AuraRotation;
import pub.godweer.cc.RotationHandler;

/**
 * None — без доводки камеры/ротации (режим триггер-бота: бьёт, когда игрок сам навёлся на цель).
 */
public final class NoneRotation implements AuraRotation {

    @Override
    public String getName() {
        return "None";
    }

    @Override
    public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
        if (RotationHandler.isActive()) {
            RotationHandler.clear();
        }
    }
}
