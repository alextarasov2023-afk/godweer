package pub.godweer.enjoy.mixin.accessor;

import com.mojang.authlib.services.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("rightClickDelay")
    int getRightClickDelay();

    @Accessor("rightClickDelay")
    void setRightClickDelay(int delay);

    @Accessor("user")
    @Mutable
    void setUser(User user);

    @Accessor("profileFuture")
    @Mutable
    void setProfileFuture(CompletableFuture<ProfileResult> profileFuture);
}