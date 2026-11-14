package pl.sofron.light.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.*;

/** Lightweight JSON configuration; unknown keys are ignored. */
public final class LightConfig {
    public boolean dynamicLighting = true;
    public boolean coloredLighting = true;
    public boolean entityAndItemLighting = true;
    public boolean heldItems = true;
    public boolean stationaryBlockLighting = true;
    public boolean droppedItems = true;
    public boolean burningEntities = true;
    public int intensityPercent = 85;
    public String quality = "quality";
    public int maximumDynamicLights = 256;
    public double dynamicLightRadius = 10.0;
    public boolean affectMobSpawning = false;
    public String rgbDebugMode = "off";

    private static boolean bool(String source, String name, boolean fallback) {
        Matcher m = Pattern.compile(Pattern.quote("\"" + name + "\"") + "\\s*:\\s*(true|false)").matcher(source);
        return m.find() ? Boolean.parseBoolean(m.group(1)) : fallback;
    }
    private static int integer(String source, String name, int fallback) {
        Matcher m = Pattern.compile(Pattern.quote("\"" + name + "\"") + "\\s*:\\s*(-?\\d+)").matcher(source);
        if (m.find()) try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) { }
        return fallback;
    }
    private static double decimal(String source, String name, double fallback) {
        Matcher m = Pattern.compile(Pattern.quote("\"" + name + "\"") + "\\s*:\\s*(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+))").matcher(source);
        if (m.find()) try { return Double.parseDouble(m.group(1)); } catch (NumberFormatException ignored) { }
        return fallback;
    }
    private static String string(String source, String name, String fallback) {
        Matcher m = Pattern.compile(Pattern.quote("\"" + name + "\"") + "\\s*:\\s*\"([^\"]*)\"").matcher(source);
        return m.find() ? m.group(1) : fallback;
    }
    public void validate() {
        intensityPercent = Math.clamp(intensityPercent, 25, 150);
        maximumDynamicLights = Math.clamp(maximumDynamicLights, 1, 512);
        dynamicLightRadius = Math.clamp(dynamicLightRadius, 1.0, 32.0);
        if (!"fast".equals(quality) && !"balanced".equals(quality) && !"quality".equals(quality)) quality = "quality";
        affectMobSpawning = false;
        if (!"off".equals(rgbDebugMode) && !"raw".equals(rgbDebugMode) && !"strength".equals(rgbDebugMode) && !"mask".equals(rgbDebugMode)) rgbDebugMode = "off";
    }
    public static LightConfig load(Path path) {
        LightConfig cfg = new LightConfig();
        try {
            if (Files.exists(path)) {
                String s = Files.readString(path, StandardCharsets.UTF_8);
                cfg.dynamicLighting = bool(s,"dynamicLighting",cfg.dynamicLighting);
                cfg.coloredLighting = bool(s,"coloredLighting",cfg.coloredLighting);
                cfg.entityAndItemLighting = bool(s,"entityAndItemLighting",cfg.entityAndItemLighting);
                cfg.heldItems = bool(s,"heldItems",cfg.heldItems);
                cfg.stationaryBlockLighting = bool(s,"stationaryBlockLighting",cfg.stationaryBlockLighting);
                cfg.droppedItems = bool(s,"droppedItems",cfg.droppedItems);
                cfg.burningEntities = bool(s,"burningEntities",cfg.burningEntities);
                cfg.intensityPercent = integer(s,"intensityPercent",cfg.intensityPercent);
                cfg.maximumDynamicLights = integer(s,"maximumDynamicLights",cfg.maximumDynamicLights);
                cfg.dynamicLightRadius = decimal(s,"dynamicLightRadius",cfg.dynamicLightRadius);
                cfg.quality = string(s,"quality",cfg.quality);
                cfg.rgbDebugMode = string(s,"rgbDebugMode",cfg.rgbDebugMode);
            }
            cfg.validate();
            Files.createDirectories(path.getParent());
            Files.writeString(path, cfg.toJson(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ex) {
            System.err.println("[Sofron Pack Light] Configuration issue: " + ex.getMessage());
        }
        return cfg;
    }
    public String toJson() {
        return "{\n" +
            "  \"dynamicLighting\": " + dynamicLighting + ",\n" +
            "  \"coloredLighting\": " + coloredLighting + ",\n" +
            "  \"entityAndItemLighting\": " + entityAndItemLighting + ",\n" +
            "  \"heldItems\": " + heldItems + ",\n" +
            "  \"stationaryBlockLighting\": " + stationaryBlockLighting + ",\n" +
            "  \"droppedItems\": " + droppedItems + ",\n" +
            "  \"burningEntities\": " + burningEntities + ",\n" +
            "  \"intensityPercent\": " + intensityPercent + ",\n" +
            "  \"quality\": \"" + quality + "\",\n" +
            "  \"maximumDynamicLights\": " + maximumDynamicLights + ",\n" +
            "  \"dynamicLightRadius\": " + dynamicLightRadius + ",\n" +
            "  \"affectMobSpawning\": false,\n" +
            "  \"rgbDebugMode\": \"" + rgbDebugMode + "\"\n}";
    }
}
