package pub.godweer.event;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventManager {
    private static final Map<Class<? extends Event>, List<Invoker>> REGISTRY = new ConcurrentHashMap<>();
    private static final List<Invoker> GLOBAL_INVOKERS = new CopyOnWriteArrayList<>();

    public static void register(Object subscriber) {
        try {
            Method onEventMethod = subscriber.getClass().getMethod("onEvent", Event.class);
            if (onEventMethod.getDeclaringClass() != Object.class) {
                onEventMethod.setAccessible(true);
                GLOBAL_INVOKERS.add(new Invoker(subscriber, onEventMethod, (byte) 2));
            }
        } catch (NoSuchMethodException ignored) {}

        for (Method method : subscriber.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(EventTarget.class) && method.getParameterCount() == 1) {
                Class<?> eventClass = method.getParameterTypes()[0];
                if (Event.class.isAssignableFrom(eventClass)) {
                    method.setAccessible(true);
                    EventTarget target = method.getAnnotation(EventTarget.class);

                    Invoker invoker = new Invoker(subscriber, method, (byte) target.priority());
                    REGISTRY.computeIfAbsent((Class<? extends Event>) eventClass, k -> new CopyOnWriteArrayList<>())
                            .add(invoker);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Event> void on(Class<T> eventClass, EventListener<T> listener) {
        REGISTRY.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>())
                .add(new Invoker(listener, (event) -> listener.onEvent((T) event), (byte) 2));
    }

    public static void register(Consumer<Event> consumer) {
        GLOBAL_INVOKERS.add(new Invoker(consumer, consumer, (byte) 2));
    }

    public static void unregister(Object subscriber) {
        GLOBAL_INVOKERS.removeIf(inv -> inv.source.equals(subscriber));
        REGISTRY.values().forEach(list -> list.removeIf(inv -> inv.source.equals(subscriber)));
    }

    public static <T extends Event> T call(T event) {
        for (Invoker global : GLOBAL_INVOKERS) {
            try {
                global.invoke(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        List<Invoker> invokers = REGISTRY.get(event.getClass());
        if (invokers != null && !invokers.isEmpty()) {
            for (Invoker invoker : invokers) {
                try {
                    invoker.invoke(event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return event;
    }

    private static final class Invoker {
        final Object source;
        final Consumer<Event> consumer;
        final byte priority;

        Invoker(Object source, Method method, byte priority) {
            this.source = source;
            this.priority = priority;
            this.consumer = (event) -> {
                try {
                    method.invoke(source, event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
        }

        Invoker(Object source, Consumer<Event> consumer, byte priority) {
            this.source = source;
            this.consumer = consumer;
            this.priority = priority;
        }

        void invoke(Event event) {
            consumer.accept(event);
        }
    }
}