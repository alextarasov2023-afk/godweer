package pub.godweer.module.impl.player;

import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.module.settings.impl.MultiModeSetting;

/**
 * Create by daun kvass
 */
public final class AntiPush extends Module {

    public static AntiPush INSTANCE;
    public final MultiModeSetting modes = multiMode("No push","Entity","Blocks","Water");

    public AntiPush() {
        super("AntiPush", "Убирает толкание от чего-то", Category.PLAYER);
        INSTANCE = this;
    }

}