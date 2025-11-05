package net.gooseman.inferno_utils.config;


public class InfernoConfig {
    public static String provider(String namespace) {
        return """
                    # General
                    
                    # Whether to show debug messages
                    debug=true
                    
                    
                    # Semi-Hardcore / Combat Logging Detection
                    
                    # How long being in combat lasts in ticks
                    combat_length=400
                    
                    # How long the death ban should last
                    death_ban_time=8h
                    # Reason for the death ban
                    death_ban_reason=You have died! If this death was not caused by a player, or you think it was otherwise unfair, please contact the moderators through the #tickets discord channel.
                    
                    # How long the combat logging ban should last
                    combat_ban_time=8h
                    # Reason for the combat logging ban
                    combat_ban_reason=Combat logging isn't permitted! If you weren't in combat, or you think this ban is otherwise unfair, please contact the moderators through the #tickets discord channel.
                    
                    # What Damage Types don't put the player in combat / cause a ban on death, comma seperated list
                    # For possible values and their explanations check out https://minecraft.wiki/w/Damage_type#List_of_damage_types
                    # Append the namespace of the damage type (e.g. "minecraft:" for vanilla damage types) in front of the damage type
                    combat_exclusions=[minecraft:sweet_berry_bush,minecraft:sting,starve,minecraft:spit,minecraft:sonic_boom,minecraft:outside_border,minecraft:out_of_world,minecraft:mob_projectile,minecraft:mob_attack_no_aggro,minecraft:mob_attack,minecraft:lightning_bolt,minecraft:hot_floor,minecraft:generic_kill,minecraft:generic,minecraft:freeze,minecraft:fly_into_wall,minecraft:fireball,minecraft:ender_pearl,minecraft:drown,minecraft:cramming,minecraft:campfire,minecraft:cactus,minecraft:arrow]
                    """;
    }
    public static SimpleConfig.ConfigRequest configRequest = SimpleConfig.of("inferno_utils-config").provider(InfernoConfig::provider);
    public static SimpleConfig config = configRequest.request();

    public static void reloadConfig() {
        config = configRequest.request();
        config.getOrDefault("debug", true);
    }

    public static String[] getStringArray(String key) {
        String arrayString = config.getOrDefault(key, "[]");
        arrayString = arrayString.substring(1, arrayString.length() - 1);
        return arrayString.split(",");
    }
}
