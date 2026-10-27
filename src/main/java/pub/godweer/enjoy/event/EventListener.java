package pub.godweer.enjoy.event;

/**
 * Create by daun kvass
 */
@FunctionalInterface
public interface EventListener<T extends Event> {
    void onEvent(T event);
}