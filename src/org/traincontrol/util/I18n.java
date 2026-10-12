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
     * @return the locale of the messages on show
     */
    public static Locale getLocale()
    {
        return bundle.getLocale();
    }

    /**
     * Which form of a counted noun the messages on show take for a number: "One", "Few", or "" for the plural - appended
     * to a key, as `countWarnings` has `countWarningsOne` and `countWarningsFew` beside it (RSA28-C2).  French takes the
     * singular for 0 as well; Polish a form of its own for 2 to 4, not 12 to 14; the rest the singular for 1 only.
     *
     * @param n the number counted
     * @return the key's ending
     */
    public static String countForm(long n)
    {
        String language = bundle.getLocale().getLanguage();

        if ("fr".equals(language)) return n == 0 || n == 1 ? "One" : "";

        if ("pl".equals(language))
        {
            if (n == 1) return "One";

            long last = n % 10, lastTwo = n % 100;

            return last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14) ? "Few" : "";
        }

        return n == 1 ? "One" : "";
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
