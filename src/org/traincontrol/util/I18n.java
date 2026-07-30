package org.traincontrol.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Centralized internationalization helper.
 * 
 * Usage:
 *   I18n.t("error.invalidLogin");
 *   I18n.f("log.userLogin", username);
 */
public final class I18n
{
    private static final String BUNDLE_NAME = "org.traincontrol.resources.messages";
    private static ResourceBundle bundle =
            ResourceBundle.getBundle(BUNDLE_NAME, Locale.getDefault());

    // Prevent instantiation
    private I18n() {}

    /**
     * Switch the current locale at run time.
     * Example: I18n.setLocale(new Locale("de", "DE"));
     * @param locale
     */
    public static void setLocale(Locale locale)
    {
        bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
    }

    /**
     * Fetch a plain string by key.
     * @param key
     * @return 
     */
    public static String t(String key)
    {
        return bundle.getString(key);
    }

    /**
     * Fetch a formatted string with placeholders.
     * Example: messages.properties -> log.userLogin=User {0} logged in.
     * Usage: I18n.f("log.userLogin", username);
     *
     * Whole numbers are passed through as text rather than as numbers.  MessageFormat sends a bare
     * {0} to the locale's NumberFormat, which groups: a feedback UID of 1001 rendered as "1,001" on
     * the track diagram, and as "1.001" or "1 001" for anyone running a European locale.  Every
     * whole number this application puts in a message is an identifier or a count - an accessory
     * address, an s88 UID, a route id, a delay in milliseconds - and none of them are grouped
     * anywhere else in the UI.
     *
     * Only integral types.  A float or double still goes to NumberFormat, which is right for a
     * measured value: it keeps the locale's decimal separator and does not expose binary rounding
     * the way Double.toString does.  Nothing passes one today.
     *
     * This relies on no message asking for a format of its own - a {0,number} placeholder handed a
     * String throws.  testMessageBundles.testNoPlaceholderAsksForItsOwnFormat holds that line.
     *
     * @param key
     * @param args
     * @return 
     */
    public static String f(String key, Object... args)
    {
        Object[] asText = new Object[args.length];

        for (int i = 0; i < args.length; i++)
        {
            boolean whole = args[i] instanceof Integer || args[i] instanceof Long
                         || args[i] instanceof Short   || args[i] instanceof Byte;

            asText[i] = whole ? args[i].toString() : args[i];
        }

        return MessageFormat.format(bundle.getString(key), asText);
    }
}
