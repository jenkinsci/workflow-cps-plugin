package org.jenkinsci.plugins.workflow.cps.view;

import io.jenkins.plugins.thememanager.Theme;
import io.jenkins.plugins.thememanager.ThemeManagerPageDecorator;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public final class ThemeUtil {

    private ThemeUtil() {
        // Suppress default constructor for noninstantiability
        throw new AssertionError();
    }

    public static String getTheme() {
        try {
            Theme theme = ThemeManagerPageDecorator.get().findTheme();
            return theme.getProperty("ace-editor", "theme").orElse("tomorrow");
        } catch (LinkageError e) {
            // Optional plugin not installed
            return "tomorrow";
        }
    }
}
