package pub.godweer.kitty.module;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Create by daun kvass
 */

@Getter
@RequiredArgsConstructor
public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    RENDER("Render"),
    PLAYER("Player"),
    MISC("Misc");

    private final String displayName;
}