package org.customlocked;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;

import javax.inject.Inject;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;


class NPCLockerOverlay extends Overlay
{
    private final Client client;
    private final LockerPlugin plugin;


    @Inject
    private NPCLockerOverlay(
            Client client,
            LockerPlugin plugin
    )
    {
        this.client = client;
        this.plugin = plugin;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }


    @Override
    public Dimension render(Graphics2D graphics)
    {
        for (NPC npc : client.getNpcs())
        {
            if (npc == null)
            {
                continue;
            }

            int npcId = npc.getId();

            if (!plugin.isNPCLocked(npcId))
            {
                continue;
            }

            Shape hull = npc.getConvexHull();

            if (hull == null)
            {
                continue;
            }

            graphics.setColor(new Color(80, 80, 80, 150));
            graphics.fill(hull);

            OverlayUtil.renderPolygon(
                    graphics,
                    hull,
                    Color.RED
            );
        }

        return null;
    }
}