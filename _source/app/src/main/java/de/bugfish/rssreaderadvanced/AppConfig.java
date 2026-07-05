package de.bugfish.rssreaderadvanced;

/**
 * Build switches you can flip by hand for the two product flavours.
 *
 * <p>Change {@link #FREE_VERSION} only:
 * <ul>
 *   <li>{@code true}  → FREE build: the user may keep at most
 *       {@link #FREE_MAX_SOURCES} sources; adding more prompts to buy Pro.</li>
 *   <li>{@code false} → PRO build: no limit.</li>
 * </ul>
 */
public final class AppConfig {

    /**
     * true = FREE build (limited), false = PRO build (unlimited).
     * <p>This is driven by the Gradle product flavour via {@code BuildConfig}
     * (the {@code free} flavour sets it true, {@code pro} sets it false), so you
     * normally never edit it here — just build the flavour you want. To force a
     * value for a quick local test, replace the right-hand side with a literal.
     */
    public static final boolean FREE_VERSION = BuildConfig.FREE_VERSION;

    /** Maximum number of sources allowed in the free build. */
    public static final int FREE_MAX_SOURCES = 3;

    /** Play Store package of the Pro app (base id + ".pro" suffix). */
    public static final String PRO_PACKAGE = "de.bugfish.rssreaderadvanced.pro";

    private AppConfig() {
    }
}
