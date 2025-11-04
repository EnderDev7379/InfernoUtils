package net.gooseman.inferno_utils.config;


public class InfernoConfig {
    public static String provider(String namespace) {
        return """
                    # Semi-Hardcore
                    
                    # How long the death ban should last | Default=8h
                    ban_time=8h
                    
                    # What Death/Damage Types shouldn't cause a ban, comma seperated list
                    # For possible values and their explanations check out https://minecraft.wiki/w/Damage_type#List_of_damage_types
                    # Append minecraft: everytwhere
                    ban_exclusions=[minecraft:sweet_berry_bush,minecraft:sting,starve,minecraft:spit,minecraft:sonic_boom,minecraft:outside_border,minecraft:out_of_world,minecraft:mob_projectile,minecraft:mob_attack_no_aggro,minecraft:mob_attack,minecraft:lightning_bolt,minecraft:hot_floor,minecraft:generic_kill,minecraft:generic,minecraft:freeze,minecraft:fly_into_wall,minecraft:fireball,minecraft:ender_pearl,minecraft:drown,minecraft:cramming,minecraft:campfire,minecraft:cactus,minecraft:arrow]
                    """;
    }
    public static SimpleConfig config = SimpleConfig.of("inferno_utils-config").provider(InfernoConfig::provider).request();

    public static String[] getStringArray(String key) {
        String arrayString = config.getOrDefault(key, "[]");
        arrayString = arrayString.substring(1, arrayString.length() - 1);
        return arrayString.split(",");
    }
}
