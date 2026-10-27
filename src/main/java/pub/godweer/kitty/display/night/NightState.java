package pub.godweer.kitty.display.night;

import lombok.Getter;
import lombok.Setter;
import pub.godweer.kitty.display.mainmenu.popup.Modal;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.auxiliary.math.Animation;

import java.util.HashSet;
import java.util.Set;

/**
 * Fresh ClickGUI state (NightX style).
 */
@Getter
@Setter
public final class NightState {
    private final Animation openAnim = new Animation(0.0F, 0.16F);
    private final Animation contentAnim = new Animation(1.0F, 0.30F);

    private boolean open;
    private boolean closing;

    private Category category = Category.COMBAT;
    private int direction = 1;

    private String search = "";
    private boolean searchFocused;

    private final Set<String> expandedModules = new HashSet<>();

    private Module selected;
    private Modal modal;

    private float panelX = 120.0F;
    private float panelY = 70.0F;
    private final float panelW = 560.0F;
    private final float panelH = 340.0F;
    private boolean placed;

    private float scroll;
    private float targetScroll;
    private float maxScroll;

    private boolean settingsOpen;

    // Fitted size + drag state (panel never leaves the screen)
    private float fitW = 560.0F;
    private float fitH = 340.0F;
    private boolean panelDragging;
    private float dragLastX;
    private float dragLastY;

    public void fitToScreen(float sw, float sh) {
        fitW = Math.min(panelW, Math.max(280.0F, sw - 12.0F));
        fitH = Math.min(panelH, Math.max(200.0F, sh - 12.0F));
        if (fitW > sw) fitW = sw;
        if (fitH > sh) fitH = sh;
        float maxX = Math.max(2.0F, sw - fitW - 2.0F);
        float maxY = Math.max(2.0F, sh - fitH - 2.0F);
        panelX = Math.max(2.0F, Math.min(maxX, panelX));
        panelY = Math.max(2.0F, Math.min(maxY, panelY));
    }

    public void update() {
        openAnim.setTarget(open && !closing ? 1.0F : 0.0F);
        openAnim.update();
        if (closing && openAnim.getValue() <= 0.01F) {
            open = false;
            closing = false;
            searchFocused = false;
            settingsOpen = false;
            modal = null;
            resetScroll();
        }
        contentAnim.update();
        targetScroll = Math.max(0.0F, Math.min(targetScroll, maxScroll));
        scroll += (targetScroll - scroll) * 0.3F;
        if (Math.abs(targetScroll - scroll) < 0.2F) scroll = targetScroll;
    }

    public void scrollBy(float delta) {
        targetScroll = Math.max(0.0F, Math.min(maxScroll, targetScroll - delta));
    }

    public void resetScroll() {
        scroll = 0.0F;
        targetScroll = 0.0F;
    }

    public void switchCategory(Category cat) {
        if (this.category == cat) return;
        this.direction = cat.ordinal() >= category.ordinal() ? 1 : -1;
        this.category = cat;
        this.selected = null;
        this.contentAnim.setValue(0.7F);
        this.contentAnim.setTarget(1.0F);
        resetScroll();
    }

    public boolean isExpanded(Module m) {
        return m != null && expandedModules.contains(m.getName());
    }

    public void toggleExpanded(Module m) {
        if (m == null) return;
        if (!expandedModules.remove(m.getName())) expandedModules.add(m.getName());
        m.setExpanded(isExpanded(m));
    }

    public boolean isInteractive() {
        return open && !closing;
    }

    public boolean isTyping() {
        return isInteractive() && (searchFocused || modal != null);
    }

    public void open() {
        open = true;
        closing = false;
        openAnim.setTarget(1.0F);
    }

    public void beginClose() {
        if (!open || closing) return;
        closing = true;
        openAnim.setTarget(0.0F);
        settingsOpen = false;
    }

    public void forceClose() {
        open = false;
        closing = false;
        searchFocused = false;
        settingsOpen = false;
        modal = null;
        openAnim.setValue(0.0F);
        resetScroll();
    }
}
