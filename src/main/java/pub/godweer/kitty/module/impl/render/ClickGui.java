package pub.godweer.kitty.module.impl.render;

import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.auxiliary.client.persiki.KeyUtil;
import pub.godweer.kitty.display.mainmenu.PanelRefractions;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.BindSetting;

/**
 * Create by daun kvass
 */
public class ClickGui extends Module {

    public static ClickGui INSTANCE;

    public final BindSetting holdKey = bind("Bind", InputConstants.KEY_LALT);

    public ClickGui() {
        super("ClickGUI", "Открывает меню клиента", Category.MISC, InputConstants.KEY_RSHIFT);
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        PanelRefractions.open(mc);
        this.setState(false);
    }


    public boolean isHoldKeyPressed(long windowHandle) {
        if (holdKey == null || holdKey.isEmpty()) return false;
        for (int code : holdKey.getValue()) {
            if (BindSetting.isMouse(code)) {
                int button = BindSetting.rawButton(code);
                if (KeyUtil.isMouseButtonDown(button)) {
                    return true;
                }
            } else if (BindSetting.isKeyboard(code)) {
                if (InputConstants.isKeyDown(code)) {
                    return true;
                }
            }
        }
        return false;
    }
}