package pub.godweer.auxiliary.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import pub.godweer.IMinecraft;
import pub.godweer.auxiliary.client.persiki.KeyUtil;

/**
 * Create by daun kvass
 */
public final class KeyCheck implements IMinecraft {

    private static final KeyCheck INSTANCE = new KeyCheck();

    public static boolean isPressed(KeyMapping keyMapping) {
        if (keyMapping == null) return false;
        InputConstants.Key key = InputConstants.getKey(keyMapping.saveString());

        if (key.getType() == InputConstants.Type.MOUSE) {
            return KeyUtil.isMouseButtonDown(key.getValue());
        } else {
            return InputConstants.isKeyDown(key.getValue());
        }
    }

    public static boolean isKeyDown(int code) {
        if (code == 0) return false;

        if (KeyUtil.isMouseButton(code)) {
            return KeyUtil.isMouseButtonDown(KeyUtil.toMouseButton(code));
        } else {
            return InputConstants.isKeyDown(code);
        }
    }
}
