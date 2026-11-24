package org.traincontrol.gui;

import java.awt.Color;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.autonomy.DiagramTileRegistry;
import org.traincontrol.model.ViewListener;

/**
 * The route editor's "Highlight on Diagram" (MT-462): flashes the squares, on any page, that a route's addresses name.
 */
public final class DiagramHighlights
{
    /** Long enough for anyone to find the squares; the question ends the highlight itself once it is answered. */
    private static final int HOLD_MS = 30 * 60 * 1000;

    /**
     * What an address names, since every tile carries a number (MT-462): a page link's address is a page, a route
     * tile's the route's id, an s88's the sensor.  Three values because a route can command another route
     * (`RouteCommand.isRoute`), and its tile must not be lit by an accessory of the same number.
     */
    public enum AddressedAs
    {
        /** A switch, a signal or an uncoupler - anything the railway throws. */
        ACCESSORY,

        /** An s88 contact. */
        FEEDBACK,

        ROUTE
    }

    /** What the highlights read of the main window: its pages, and the squares on screen. */
    interface Diagrams
    {
        /** The railway whose pages are searched. */
        ViewListener getModel();

        /** The labels on screen for each square. */
        DiagramTileRegistry getDiagramTileRegistry();
    }

    private final Diagrams ui;

    DiagramHighlights(Diagrams ui)
    {
        this.ui = ui;
    }

    /** Holds squares lit on the track diagram while a question about them is up, or puts them out. */
    public static void hold(Diagrams ui, Collection<TileGraph.TileKey> squares, boolean on)
    {
        for (TileGraph.TileKey square : squares)
        {
            for (LayoutLabel label : ui.getDiagramTileRegistry().labelsFor(square))
            {
                if (on) label.flashHighlight(org.traincontrol.util.ImageUtil.HIGHLIGHT, HOLD_MS);
                else label.endFlash();
            }
        }
    }

    /** Whether a tile is what an address of this kind names; a lamp answers to an accessory address (`isSignal()`). */
    private static boolean answersTo(LayoutDiagramComponent tile, AddressedAs kind)
    {
        if (tile == null) return false;
        switch (kind)
        {
            case FEEDBACK:
                return tile.isFeedback();

            case ROUTE:
                return tile.isRoute();

            default:
                return tile.isAccessory();
        }
    }

    /** Lights the squares of this kind carrying one of these logical addresses, on screen or not; returns how many. */
    int highlightAddresses(Set<Integer> addresses, AddressedAs kind,
        Color wash, int holdMs)
    {
        if (addresses == null || addresses.isEmpty()) return 0;

        return lightWhere(tile ->
        {
            if (!answersTo(tile, kind)) return false;

            // An accessory's decoder is asked in the tile's protocol, which also answers a three-way's second address.
            if (kind == AddressedAs.ACCESSORY)
            {
                for (Integer address : addresses)
                {
                    if (address != null && tile.answersToAccessoryAddress(address, tile.getProtocol())) return true;
                }
                return false;
            }

            // The logical address, as a route records it: an accessory's raw address is twice that, and would match
            // an s88, route tile or link carrying the same number.
            return addresses.contains(tile.getLogicalAddress());
        }, wash, holdMs);
    }

    private int lightWhere(Predicate<LayoutDiagramComponent> answers,
        Color wash, int holdMs)
    {
        int lit = 0;
        for (String page : this.ui.getModel().getLayoutList())
        {
            LayoutDiagram diagram = this.ui.getModel().getLayout(page);
            if (diagram == null) continue;

            for (LayoutDiagramComponent tile : diagram.getAll())
            {
                if (tile == null || !answers.test(tile)) continue;

                TileGraph.TileKey key = new TileGraph.TileKey(page, tile.getX(), tile.getY());
                for (LayoutLabel label : this.ui.getDiagramTileRegistry().labelsFor(key))
                {
                    label.flashHighlight(wash, holdMs);
                    lit++;
                }
            }
        }
        return lit;
    }

    /** Lights the accessory squares these commands reach and `notThese` (or null) doesn't (OB-286); returns a count. */
    int highlightAccessories(
        Map<Integer, Set<Accessory.accessoryDecoderType>> byAddress,
        Color wash, int holdMs,
        Map<Integer, Set<Accessory.accessoryDecoderType>> notThese)
    {
        if (byAddress == null || byAddress.isEmpty()) return 0;

        return lightWhere(tile ->
            answersTo(tile, AddressedAs.ACCESSORY) && reaches(tile, byAddress)
                && (notThese == null || !reaches(tile, notThese)), wash, holdMs);
    }

    private static boolean reaches(LayoutDiagramComponent tile,
        Map<Integer, Set<Accessory.accessoryDecoderType>> byAddress)
    {
        for (Map.Entry<Integer, Set<Accessory.accessoryDecoderType>>
            wanted : byAddress.entrySet())
        {
            for (Accessory.accessoryDecoderType protocol : wanted.getValue())
            {
                if (tile.answersToAccessoryAddress(wanted.getKey(), protocol)) return true;
            }
        }
        return false;
    }
}
