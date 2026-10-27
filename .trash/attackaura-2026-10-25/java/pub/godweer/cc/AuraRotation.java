package pub.godweer.cc;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Create by daun kvass
 */
public interface AuraRotation {
    String getName();
    void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely);
    default void onAttack() {}
    default void reset() {}
}