package pub.godweer.enjoy.event;

import pub.godweer.enjoy.event.list.*;

/**
 * Create by daun kvass
 */
public final class Events {
    public static final MenuRenderEvent FINAL_GUI_RENDER = new MenuRenderEvent();
    public static final KeyboardInputEvent KEYBOARD_INPUT = new KeyboardInputEvent();
    public static final MouseInputEvent MOUSE_INPUT = new MouseInputEvent();
    public static final CharacterInputEvent CHARACTER_INPUT = new CharacterInputEvent();
    public static final ScreenKeyEvent SCREEN_KEY = new ScreenKeyEvent();
    public static final ScreenMouseButtonEvent SCREEN_MOUSE_BUTTON = new ScreenMouseButtonEvent();
}