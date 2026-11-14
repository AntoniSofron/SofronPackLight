package pl.sofron.light.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** User rules in config/sofron-pack-light-blocks.properties.
 *  ID = #RRGGBB, intensity-percent[, vanilla-emission-0..15[, rgb-radius-blocks]]
 *  Missing entries are NEUTRAL. Missing third value preserves vanilla emission.
 *  When provided, the third value overrides vanilla emission on both logical sides.
 */
public final class BlockLightRules {
    public record Rule(RgbLight color, float intensity, Integer emission, Float rgbRadius) {}
    /** Exact RGB tint radius, or the old 0.9.40 visual radius when omitted. */
    public static float tintRadius(Rule rule, float defaultRadius, int actualEmission) {
        if (rule != null && rule.rgbRadius() != null) return rule.rgbRadius();
        return (float)(defaultRadius * actualEmission / 15.0 * 0.68);
    }
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final RgbLight NEUTRAL = new RgbLight(1,1,1);
    private static volatile Map<String, Rule> rules = Map.of();
    private static volatile Path path;
    private static volatile long stamp = Long.MIN_VALUE;
    private static volatile long revision;
    public static long revision() { return revision; }
    public static int ruleCount() { return rules.size(); }
    private static volatile long lastCheck = 0;
    private BlockLightRules() {}
    /** Exact block-state ID takes precedence; wall variants inherit floor torch rules. */
    public static Rule rule(String id) {
        Rule exact = rules.get(id);
        if (exact != null) return exact;
        String base = canonicalTorchId(id);
        return base.equals(id) ? null : rules.get(base);
    }
    public static String canonicalTorchId(String id) {
        return switch (id) {
            case "minecraft:wall_torch" -> "minecraft:torch";
            case "minecraft:soul_wall_torch" -> "minecraft:soul_torch";
            case "minecraft:redstone_wall_torch" -> "minecraft:redstone_torch";
            case "minecraft:copper_wall_torch" -> "minecraft:copper_torch";
            default -> id;
        };
    }
    public static boolean isTracked(String id) { return LightPalette.LUMINOUS.contains(id) || rule(id) != null; }
    public static RgbLight neutral() { return NEUTRAL; }
    /** Null means retain vanilla emission. Read from the integrated server too. */
    public static Integer vanillaEmissionOverride(String id) {
        Rule r = rule(id);
        return r == null ? null : r.emission();
    }
    public static synchronized void initialize(Path file) {
        path = file;
        lastCheck = 0;
        refresh(true);
    }
    public static void poll() {
        long now = System.nanoTime();
        if (now-lastCheck < 2_000_000_000L) return;
        synchronized (BlockLightRules.class) {
            if (now-lastCheck < 2_000_000_000L) return;
            lastCheck = now;
            refresh(false);
        }
    }
    private static void refresh(boolean force) {
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) Files.writeString(path,
                "# Sofron Pack Light 0.9.42 default block rules\\n" +
                "# Format: namespace:block = #RRGGBB, intensityPercent[, vanillaEmission0to15[, rgbRadiusBlocks]]\\n" +
                "# Wall torch variants automatically inherit the respective floor torch rule.\\n" +
                "minecraft:torch = #FFA500, 50, 14, 3\\n" +
                "minecraft:soul_torch = #00FAFF, 50, 10, 3\\n" +
                "minecraft:redstone_torch = #FF0000, 50, 10, 3\\n" +
                "minecraft:copper_torch = #00FF2E, 50, 14, 3\\n",
                StandardCharsets.UTF_8);
            long modified = Files.getLastModifiedTime(path).toMillis();
            if (!force && modified == stamp) return;
            Map<String,Rule> parsed = new HashMap<>();
            int lineNumber = 0;
            for (String line: Files.readAllLines(path, StandardCharsets.UTF_8)) {
                lineNumber++;
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#") || s.startsWith(";")) continue;
                int equals=s.indexOf('=');
                if (equals < 1) { System.err.println("[Sofron Light] Skipping rule line "+lineNumber); continue; }
                String id=s.substring(0,equals).trim();
                String[] args=s.substring(equals+1).trim().split(",",-1);
                if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || args.length<2 || args.length>4 || !HEX.matcher(args[0].trim()).matches()) {
                    System.err.println("[Sofron Light] Invalid rule line "+lineNumber+": "+s); continue;
                }
                try {
                    String hex=args[0].trim();
                    RgbLight rgb=new RgbLight(Integer.parseInt(hex.substring(1,3),16)/255f,
                        Integer.parseInt(hex.substring(3,5),16)/255f,Integer.parseInt(hex.substring(5,7),16)/255f);
                    int power=Integer.parseInt(args[1].trim());
                    if (power<0 || power>200) throw new NumberFormatException("intensity out of range 0..200");
                    Integer emission=null;
                    if (args.length>=3 && !args[2].trim().isEmpty()) {
                        emission=Integer.valueOf(args[2].trim());
                        if (emission<0 || emission>15) throw new NumberFormatException("emission out of range 0..15");
                    }
                    Float rgbRadius=null;
                    if (args.length==4 && !args[3].trim().isEmpty()) {
                        rgbRadius=Float.valueOf(args[3].trim());
                        if (!Float.isFinite(rgbRadius) || rgbRadius<0f || rgbRadius>64f)
                            throw new NumberFormatException("RGB radius out of range 0..64 blocks");
                    }
                    parsed.put(id,new Rule(rgb,power/100f,emission,rgbRadius));
                } catch (NumberFormatException ex) {
                    System.err.println("[Sofron Light] Invalid rule on line "+lineNumber+": "+ex.getMessage());
                }
            }
            Map<String, Rule> next=Collections.unmodifiableMap(parsed);
            if (!next.equals(rules)) revision++;
            rules=next;
            stamp=modified;
            System.out.println("[Sofron Light] Loaded "+rules.size()+" block light color rules");
        } catch (IOException ex) { System.err.println("[Sofron Light] Cannot read light rules: "+ex.getMessage()); }
    }
}
