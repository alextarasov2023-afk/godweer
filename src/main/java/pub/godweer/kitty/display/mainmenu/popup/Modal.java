package pub.godweer.kitty.display.mainmenu.popup;

/**
 * Create by daun kvass
 */
public interface Modal {
    void render(int mouseX, int mouseY, float screenWidth, float screenHeight, float alpha);
    boolean mouseClicked(double mouseX, double mouseY, int button);
    void mouseReleased(double mouseX, double mouseY, int button);
    boolean keyPressed(int keyCode, int scanCode, int modifiers);
    void charTyped(int codePoint);
    boolean isFinished();
}