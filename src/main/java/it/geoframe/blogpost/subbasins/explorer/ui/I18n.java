package it.geoframe.blogpost.subbasins.explorer.ui;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class I18n {

    private static final Locale APP_LOCALE = Locale.ITALIAN.getLanguage().equals(Locale.getDefault().getLanguage())
            ? Locale.ITALIAN
            : Locale.ENGLISH;

    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("i18n.messages", APP_LOCALE);

    private I18n() {
    }

    public static String tr(String key) {
        try {
            return BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }
}
