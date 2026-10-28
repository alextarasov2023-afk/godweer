package pub.godweer.event;

/**
 * Create by daun kvass
 */
@FunctionalInterface
public interface EventListener<T extends Event> {
    void onEvent(T event);
}