package pub.godweer;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;

/**
 * Create by daun kvass
 */
public interface IMinecraft {
    Minecraft mc = Minecraft.getInstance();
    default Minecraft client() {return mc;}
    default Window window(){return mc.getWindow();}
    default long hwindow(){return mc.getWindow().handle();}
    default LocalPlayer player() {return mc.player;}
    default ClientLevel level() {return mc.level;}
    default MultiPlayerGameMode gameMode() {return mc.gameMode;}
    default Screen screen() {return mc.gui.screen();}
    default boolean inGame() {return player() != null && level() != null;}
}
