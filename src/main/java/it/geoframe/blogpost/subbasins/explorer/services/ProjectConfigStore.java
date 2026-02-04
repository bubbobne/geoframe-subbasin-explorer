package it.geoframe.blogpost.subbasins.explorer.services;


import java.nio.file.Path;
import java.util.Optional;
import java.util.prefs.Preferences;

public final class ProjectConfigStore {
    private static final Preferences P = Preferences.userRoot().node("it/geoframe/subbasins-explorer");

    private ProjectConfigStore() {}

    public static void save(Path geopackage, Path sqlite) {
        P.put("geopackagePath", geopackage.toString());
        P.put("sqlitePath", sqlite.toString());
    }

    public static Optional<ProjectConfig> load() {
        String g = P.get("geopackagePath", null);
        String s = P.get("sqlitePath", null);
        if (g == null || s == null) return Optional.empty();
        return Optional.of(new ProjectConfig(Path.of(g), Path.of(s)));
    }

    public static void clear() {
        P.remove("geopackagePath");
        P.remove("sqlitePath");
    }
}
