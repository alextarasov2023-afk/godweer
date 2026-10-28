package pub.godweer.module.impl.misc;

import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.KeyboardInputEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.BindSetting;
import pub.godweer.util.music.MusicAction;
import pub.godweer.util.music.MusicService;

/**
 * Global hotkeys for the Music HUD.
 *
 * <p>Bind play/pause, next and previous track in the ClickGUI (Misc -> Music)
 * and control playback from anywhere in the game, even when the cursor is free.
 */
public final class Music extends Module {

    public final BindSetting playPause = bind("Play/Pause", 0);
    public final BindSetting next = bind("Next", 0);
    public final BindSetting prev = bind("Previous", 0);

    public Music() {
        super("Music", "Горячие клавиши Music HUD", Category.MISC);
    }

    @EventTarget
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (!isEnabled()) return;
        if (event.getAction() != InputConstants.PRESS) return;
        if (event.isCancelled()) return;

        int key = event.getKey();
        if (playPause.matches(key)) {
            MusicService.getInstance().control(MusicAction.PLAY_PAUSE);
            event.cancel();
        } else if (next.matches(key)) {
            MusicService.getInstance().control(MusicAction.NEXT);
            event.cancel();
        } else if (prev.matches(key)) {
            MusicService.getInstance().control(MusicAction.PREVIOUS);
            event.cancel();
        }
    }
}
