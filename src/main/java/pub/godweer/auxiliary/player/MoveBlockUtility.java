package pub.godweer.auxiliary.player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Create by daun kvass
 */
public class MoveBlockUtility {

    private static final Map<Object, Integer> freezeLocks = new ConcurrentHashMap<>();
    private static final Map<Object, Integer> sprintBlockLocks = new ConcurrentHashMap<>();

    public static void freeze(Object owner, int ticks) {
        if (owner != null && ticks > 0) {
            freezeLocks.put(owner, ticks);
        }
    }

    public static void blockSprint(Object owner, int ticks) {
        if (owner != null && ticks > 0) {
            sprintBlockLocks.put(owner, ticks);
        }
    }


    public static void unblock(Object owner) {
        if (owner != null) {
            freezeLocks.remove(owner);
            sprintBlockLocks.remove(owner);
        }
    }


    public static void unblockAll() {
        freezeLocks.clear();
        sprintBlockLocks.clear();
    }

    public static boolean isFrozen() {
        return !freezeLocks.isEmpty();
    }

    public static boolean isSprintBlocked() {
        return !sprintBlockLocks.isEmpty() || isFrozen();
    }

    public static void onTick() {
        if (!freezeLocks.isEmpty()) {
            for (Object key : freezeLocks.keySet()) {
                freezeLocks.computeIfPresent(key, (k, val) -> (val <= 1) ? null : val - 1);
            }
        }

        if (!sprintBlockLocks.isEmpty()) {
            for (Object key : sprintBlockLocks.keySet()) {
                sprintBlockLocks.computeIfPresent(key, (k, val) -> (val <= 1) ? null : val - 1);
            }
        }
    }
}