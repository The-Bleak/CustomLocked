package org.customlocked;

import com.google.inject.Provides;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.widgets.Widget;

import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostMenuSort;

import net.runelite.client.events.ConfigChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;


@Slf4j
@PluginDescriptor(
        name = "Locker"
)
public class LockerPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ItemLockerOverlay itemOverlay;

    @Inject
    private NPCLockerOverlay npcOverlay;


    private final Set<Integer> unlockedItems = new HashSet<>();

    private final Set<Integer> unlockedNPCs = new HashSet<>();


    private static final Set<Integer> LOCKED_NPCS = LockedNPCs.NPCS;


    @Provides
    LockerConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(LockerConfig.class);
    }


    @Override
    protected void startUp()
    {
        loadItems();
        loadNPCs();

        itemOverlay.invalidateCache();

        overlayManager.add(itemOverlay);
        overlayManager.add(npcOverlay);
    }


    @Override
    protected void shutDown()
    {
        overlayManager.remove(itemOverlay);
        overlayManager.remove(npcOverlay);
    }



    // ITEM LOCKER


    boolean isItemUnlocked(int id)
    {
        return unlockedItems.contains(id);
    }


    void unlockItem(int id)
    {
        unlockedItems.add(id);
        saveItems();
        itemOverlay.invalidateCache();
    }


    void lockItem(int id)
    {
        unlockedItems.remove(id);
        saveItems();
        itemOverlay.invalidateCache();
    }


    private void loadItems()
    {
        String csv =
                configManager.getConfiguration(
                        "locker",
                        "unlockedItems"
                );


        if(csv == null || csv.isEmpty())
            return;


        unlockedItems.addAll(parseCSV(csv));
    }



    private void saveItems()
    {
        configManager.setConfiguration(
                "locker",
                "unlockedItems",
                writeCSV(unlockedItems)
        );
    }



    // NPC LOCKER



    boolean isManagedNPC(int id)
    {
        return LockedNPCs.NPCS.contains(id);
    }


    boolean isNPCUnlocked(int id)
    {
        return unlockedNPCs.contains(id);
    }


    boolean isNPCLocked(int id)
    {
        return isManagedNPC(id)
                && !isNPCUnlocked(id);
    }


    void unlockNPC(int id)
    {
        unlockedNPCs.add(id);
        saveNPCs();
    }


    void lockNPC(int id)
    {
        unlockedNPCs.remove(id);
        saveNPCs();
    }


    private void loadNPCs()
    {
        String csv =
                configManager.getConfiguration(
                        "locker",
                        "unlockedNPCs"
                );


        if(csv == null || csv.isEmpty())
            return;


        unlockedNPCs.addAll(parseCSV(csv));
    }



    private void saveNPCs()
    {
        configManager.setConfiguration(
                "locker",
                "unlockedNPCs",
                writeCSV(unlockedNPCs)
        );
    }


    // LIVE CONFIG REFRESH


    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"locker".equals(event.getGroup()))
            return;

        switch (event.getKey())
        {
            case "unlockedItems":
                unlockedItems.clear();
                unlockedItems.addAll(parseCSV(event.getNewValue()));
                itemOverlay.invalidateCache();
                break;

            case "unlockedNPCs":
                unlockedNPCs.clear();
                unlockedNPCs.addAll(parseCSV(event.getNewValue()));
                break;

            default:
                break;
        }
    }


    // SHARED MENU HANDLING


    private boolean isOwnSyntheticEntry(MenuEntry entry)
    {
        if (entry.getType() == MenuAction.RUNELITE)
        {
            String option = entry.getOption();
            return "Lock".equals(option) || "Unlock".equals(option);
        }

        if (entry.getType() == MenuAction.CANCEL)
        {
            return "Locked".equals(entry.getOption());
        }

        return false;
    }


    @Subscribe
    public void onMenuOpened(MenuOpened event)
    {
        boolean shiftHeld = client.isKeyPressed(KeyCode.KC_SHIFT);

        List<MenuEntry> kept = new ArrayList<>();

        Map<Integer, String> lockedNpcTargets = new LinkedHashMap<>();
        Map<Integer, String> lockedItemTargets = new LinkedHashMap<>();
        Map<Integer, String> managedNpcTargets = new LinkedHashMap<>();
        Map<Integer, String> seenItemTargets = new LinkedHashMap<>();

        for (MenuEntry entry : event.getMenuEntries())
        {

            if (isOwnSyntheticEntry(entry))
            {
                continue;
            }

            NPC npc = entry.getNpc();

            if (npc != null)
            {
                int id = npc.getId();

                if (isManagedNPC(id))
                {
                    managedNpcTargets.putIfAbsent(id, entry.getTarget());

                    if (isNPCLocked(id))
                    {
                        lockedNpcTargets.putIfAbsent(id, entry.getTarget());
                        continue;
                    }
                }

                kept.add(entry);
                continue;
            }


            Widget widget = entry.getWidget();
            int itemId = entry.getItemId();

            if (widget != null && itemId > -1)
            {
                seenItemTargets.putIfAbsent(itemId, entry.getTarget());

                if (!isItemUnlocked(itemId))
                {
                    lockedItemTargets.putIfAbsent(itemId, entry.getTarget());
                    continue;
                }
            }

            kept.add(entry);
        }

        client.getMenu().setMenuEntries(kept.toArray(new MenuEntry[0]));

        for (String target : lockedNpcTargets.values())
        {
            client.getMenu()
                    .createMenuEntry(-1)
                    .setOption("Locked")
                    .setTarget(target)
                    .setType(MenuAction.CANCEL);
        }

        for (String target : lockedItemTargets.values())
        {
            client.getMenu()
                    .createMenuEntry(-1)
                    .setOption("Locked")
                    .setTarget(target)
                    .setType(MenuAction.CANCEL);
        }

        if (!shiftHeld)
            return;

        for (Map.Entry<Integer, String> e : managedNpcTargets.entrySet())
        {
            int id = e.getKey();
            boolean unlocked = isNPCUnlocked(id);

            client.getMenu()
                    .createMenuEntry(-1)
                    .setOption(unlocked ? "Lock" : "Unlock")
                    .setTarget(e.getValue())
                    .setType(MenuAction.RUNELITE)
                    .onClick(ev ->
                    {
                        if (unlocked)
                            lockNPC(id);
                        else
                            unlockNPC(id);
                    });
        }

        for (Map.Entry<Integer, String> e : seenItemTargets.entrySet())
        {
            int id = e.getKey();
            boolean unlocked = isItemUnlocked(id);

            client.getMenu()
                    .createMenuEntry(-1)
                    .setOption(unlocked ? "Lock" : "Unlock")
                    .setTarget(e.getValue())
                    .setType(MenuAction.RUNELITE)
                    .onClick(ev ->
                    {
                        if (unlocked)
                            lockItem(id);
                        else
                            unlockItem(id);
                    });
        }
    }


    @Subscribe
    public void onPostMenuSort(PostMenuSort event)
    {
        if (client.isMenuOpen())
            return;

        MenuEntry[] entries =
                client.getMenu().getMenuEntries();

        if (entries.length == 0)
            return;

        MenuEntry entry =
                entries[entries.length - 1];

        NPC npc = entry.getNpc();

        if (npc != null)
        {
            if (!isNPCLocked(npc.getId()))
                return;

            MenuEntry locked =
                    client.getMenu()
                            .createMenuEntry(-1);

            locked.setOption("Locked");
            locked.setTarget(entry.getTarget());
            locked.setType(MenuAction.CANCEL);
            return;
        }

        Widget widget = entry.getWidget();
        int itemId = entry.getItemId();

        if (widget == null || itemId <= -1 || isItemUnlocked(itemId))
            return;

        MenuEntry locked =
                client.getMenu()
                        .createMenuEntry(-1);

        locked.setOption("Locked");
        locked.setTarget(entry.getTarget());
        locked.setType(MenuAction.CANCEL);
    }



    // CSV HELPERS


    private Set<Integer> parseCSV(String csv)
    {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .map(x ->
                {
                    try
                    {
                        return Integer.parseInt(x);
                    }
                    catch(Exception e)
                    {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }



    private String writeCSV(Set<Integer> set)
    {
        return set.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}