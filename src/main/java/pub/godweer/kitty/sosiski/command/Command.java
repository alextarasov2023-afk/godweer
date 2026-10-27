package pub.godweer.kitty.sosiski.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.util.List;

/**
 * Create by daun kvass
 */
public abstract class Command {
    private final String name;
    private final String description;

    protected Command(String name, String description) {
        this(name, description, ":small_blue_diamond:");
    }

    protected Command(String name, String description, String emoji) {
        this.name = name;
        this.description = description;
    }

    public final String name() {
        return name;
    }

    public final String description() {
        return description;
    }

    public List<String> aliases() {
        return List.of();
    }

    public abstract void build(LiteralArgumentBuilder<Object> builder);
}
