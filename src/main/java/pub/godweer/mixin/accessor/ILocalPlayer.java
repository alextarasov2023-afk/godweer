package pub.godweer.mixin.accessor;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LocalPlayer.class)
public interface ILocalPlayer {
    @Accessor("wasSprinting")
    boolean serverSprintState();

    @Invoker("sendIsSprintingIfNeeded")
    void syncSprintState();
}