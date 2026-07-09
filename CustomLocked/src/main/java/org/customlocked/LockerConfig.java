package org.customlocked;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("locker")
public interface LockerConfig extends Config
{
    @ConfigItem(
            keyName = "unlockedItems",
            name = "Unlocked Items",
            description = "Stores unlocked items as CSV"
    )
    default String unlockedItems()
    {
        return "";
    }

    @ConfigItem(
            keyName = "unlockedNPCs",
            name = "Unlocked NPCs",
            description = "Stores unlocked NPC ids as CSV"
    )
    default String unlockedNPCs()
    {
        return "";
    }
}