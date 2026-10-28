package pub.godweer.util.render.font;

public enum IconUse {
    FIGHT("b"),
    MOVEMENT("c"),
    RENDER("d"),
    PLAYER("e"),
    MISC("G"),
    SCRIPT("g"),
    SEARCH("h"),
    CHECK("i"),
    DOWN("j"),
    UP("k"),
    CUBE("l"),
    PERSONS("n"),
    GEAR("o"),
    EXIT("p"),
    ADD("q"),
    REFRESH("r"),
    MICROSOFT("s"),
    STAR("t"),
    CROSS("u"),
    HOME("v"),
    KEYBOARD("w"),
    COMPASS("x"),
    BACK("z"),
    INFO("a"),
    WARN("B"),
    POTION("C"),
    CLOCK("D"),
    SPUTNIK("E"),
    GROUP("F"),
    LINK("y"),
    ADDFRIEND("H"),
    REMOVEFRIEND("I"),
    STAFF("J"),
    ONMODULE("L"),
    OFFMODULE("K"),
    WORLD("M"),
    BPS("c"),
    TPS("D"),
    FPS("N"),
    CORD("R"),
    PING("S"),
    /** Musical note - taken from the emoji font instead of the icon font. */
    MUSIC("\uD83C\uDFB5", true);
    public final String glyph;
    /** Whether the glyph lives in the emoji font rather than the icon font. */
    public final boolean emoji;
    IconUse(String glyph) {this(glyph, false);}
    IconUse(String glyph, boolean emoji) {this.glyph = glyph; this.emoji = emoji;}
    public String getGlyph() {return this.glyph;}
    @Override
    public String toString() {return this.glyph;}
}