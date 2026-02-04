package it.geoframe.blogpost.subbasins.explorer.services;


import java.nio.file.Path;

public record ProjectConfig(Path geopackagePath, Path sqlitePath) {}
