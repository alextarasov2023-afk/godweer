package pub.godweer.ui.modal;

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

    /** Есть ли в окне активное поле: только тогда клавиша может дать символ. */
    default boolean isTextFocused() {
        return false;
    }

    /** Колесо над списком окна двигает список окна, а не панель за ним. */
    default void mouseScroll(double delta) {
    }
}