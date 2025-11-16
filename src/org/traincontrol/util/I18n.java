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
     * Example: messages.properties -> log.userLogin=User {0} logged in.Usage: I18n.f("log.userLogin", username);
     * @param key
     * @param args
     * @return 
     */
    public static String f(String key, Object... args)
    {
        return MessageFormat.format(bundle.getString(key), args);
    }
}
