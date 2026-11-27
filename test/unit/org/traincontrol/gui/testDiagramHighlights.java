package org.traincontrol.gui;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.DiagramHighlights.AddressedAs;
import org.traincontrol.gui.autonomy.DiagramTileRegistry;
import org.traincontrol.model.ViewListener;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import static support.Components.onTheEventThread;

/**
 * `DiagramHighlights` on its own (MT-462, OB-286): which squares a route's addresses light, through a fake of the two
 * things it reads of the main window.  Its integration tests are `regression.testTheRouteHighlightAsksWhatATileIs`,
 * `regression.testAThreeWayIsLitOnce` and `regression.testTheTailIsPickedOnTheDiagram`.
 */
public class testDiagramHighlights
{
    /** Long enough that no flash ends by itself while a claim reads it. */
    private static final int HOLD = 60000;

    /**
     * An address lights only the squares of its kind: an s88's number lights the s88, a route's the route tile, an
     * accessory's the turnout, though all three carry the same number.
     *
     * MUTATION: in `DiagramHighlights.highlightAddresses`, drop the `answersTo` test, and the s88's number lights all
     * three.
     */
    @Test
    public void testAnAddressLightsOnlyTheSquaresOfItsKind()
    {
        List<String> lit = onTheEventThread(() ->
        {
            Fake ui = new Fake();
            ui.put("Main", componentType.FEEDBACK, 0, 5, null, "s88");
            ui.put("Main", componentType.ROUTE, 1, 5, null, "route");
            ui.put("Main", componentType.SWITCH_LEFT, 2, 10, null, "turnout");
            List<String> out = new ArrayList<>();
            for (AddressedAs kind : AddressedAs.values())
                out.add(kind + " " + new DiagramHighlights(ui).highlightAddresses(Collections.singleton(5), kind,
                    Color.YELLOW, HOLD) + " " + ui.litAndEnded());
            return out;
        });

        assertEquals(lit, Arrays.asList("ACCESSORY 1 [turnout]", "FEEDBACK 1 [s88]", "ROUTE 1 [route]"));
    }

    /**
     * A route's accessory address is the logical one, and a three-way answers to its second decoder's too; a raw
     * address names nothing.
     *
     * MUTATION: in `DiagramHighlights.highlightAddresses`, match accessories on `getLogicalAddress()` as the other
     * kinds are, and the three-way's second address lights nothing.
     */
    @Test
    public void testAThreeWayAnswersToItsSecondAddress()
    {
        List<String> lit = onTheEventThread(() ->
        {
            Fake ui = new Fake();
            ui.put("Main", componentType.SWITCH_THREE, 0, 10, null, "three-way");
            ui.put("Other", componentType.SWITCH_LEFT, 0, 40, accessoryDecoderType.DCC, "turnout");
            List<String> out = new ArrayList<>();
            for (Set<Integer> addresses : Arrays.asList(Collections.singleton(6), new HashSet<>(Arrays.asList(10, 40)),
                new HashSet<>(Arrays.asList(5, 20)), Collections.<Integer>emptySet()))
                out.add(new DiagramHighlights(ui).highlightAddresses(addresses, AddressedAs.ACCESSORY, Color.YELLOW,
                    HOLD) + " " + ui.litAndEnded());
            return out;
        });

        assertEquals(lit, Arrays.asList("1 [three-way]", "0 []", "2 [three-way, turnout]", "0 []"),
            "the second address, the raw addresses, the logical ones on two pages, none");
    }

    /**
     * A command lights the accessories its address reaches in its own protocol: MM2 turnout 5 and DCC turnout 5 are two
     * decoders, and a three-way is reached at its address and the next.
     *
     * MUTATION: in `DiagramHighlights.reaches`, ask in the tile's protocol rather than the command's, and the MM2
     * command lights the DCC turnout too.
     */
    @Test
    public void testACommandLightsOnlyItsProtocolsDecoder()
    {
        List<String> lit = onTheEventThread(() ->
        {
            Fake ui = new Fake();
            ui.put("Main", componentType.SWITCH_LEFT, 0, 10, accessoryDecoderType.MM2, "MM2 5");
            ui.put("Main", componentType.SWITCH_RIGHT, 1, 10, accessoryDecoderType.DCC, "DCC 5");
            ui.put("Main", componentType.SWITCH_THREE, 2, 14, null, "three-way 7");
            ui.put("Main", componentType.FEEDBACK, 3, 5, null, "s88 5");
            List<String> out = new ArrayList<>();
            for (accessoryDecoderType protocol : accessoryDecoderType.values())
                out.add(protocol + " " + new DiagramHighlights(ui).highlightAccessories(by(5, protocol, 8, protocol),
                    Color.YELLOW, HOLD, null) + " " + ui.litAndEnded());
            return out;
        });

        assertEquals(lit, Arrays.asList("MM2 2 [MM2 5, three-way 7]", "DCC 1 [DCC 5]"),
            "an MM2 command to 5 and 8, then a DCC one (a null protocol is MM2)");
    }

    /**
     * A three-way a route commands at one decoder and checks at the other is lit once, as commanded: the checked lights
     * leave out what the commands reach.
     *
     * MUTATION: in `DiagramHighlights.highlightAccessories`, leave out the `notThese` test, and the three-way is lit
     * by the check too.
     */
    @Test
    public void testAThreeWayCommandedAndCheckedIsLitOnce()
    {
        String lit = onTheEventThread(() ->
        {
            Fake ui = new Fake();
            ui.put("Main", componentType.SWITCH_THREE, 0, 10, null, "three-way 5");
            ui.put("Main", componentType.SWITCH_LEFT, 1, 12, null, "turnout 6");
            int checked = new DiagramHighlights(ui).highlightAccessories(by(6, null), Color.ORANGE, HOLD, by(5, null));
            return checked + " " + ui.litAndEnded();
        });

        assertEquals(lit, "1 [turnout 6]", "the check on 6 beside a command to 5");
    }

    /**
     * A question holds its squares lit, every copy of each on screen, until it puts them out.
     *
     * MUTATION: in `DiagramHighlights.hold`, leave out `label.endFlash()`, and the squares stay lit once put out.
     */
    @Test
    public void testAQuestionHoldsItsSquaresUntilItPutsThemOut()
    {
        List<String> lit = onTheEventThread(() ->
        {
            Fake ui = new Fake();
            ui.put("Main", componentType.STRAIGHT, 0, 0, null, "a");
            ui.put("Main", componentType.STRAIGHT, 1, 0, null, "b");
            ui.label("Main", 0, "a again");
            List<TileKey> held = Arrays.asList(new TileKey("Main", 0, 0), new TileKey("Main", 2, 0));
            DiagramHighlights.hold(ui, held, true);
            List<String> out = new ArrayList<>(Collections.singletonList(ui.lit().toString()));
            DiagramHighlights.hold(ui, held, false);
            out.add(ui.lit().toString());
            return out;
        });

        assertEquals(lit, Arrays.asList("[a, a again]", "[]"), "held, then put out");
    }

    /** A command or a check: each address with its protocols, in pairs (address, protocol). */
    private static Map<Integer, Set<accessoryDecoderType>> by(Object... pairs)
    {
        Map<Integer, Set<accessoryDecoderType>> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
            out.computeIfAbsent((Integer) pairs[i], k -> new HashSet<>()).add((accessoryDecoderType) pairs[i + 1]);
        return out;
    }

    /** The main window as the highlights read it: pages of tiles in one row, each with a label on screen. */
    private static final class Fake implements DiagramHighlights.Diagrams
    {
        private final Map<String, LayoutDiagram> pages = new LinkedHashMap<>();
        private final DiagramTileRegistry registry = new DiagramTileRegistry();
        private final List<LayoutLabel> labels = new ArrayList<>();

        @Override
        public ViewListener getModel()
        {
            return (ViewListener) Proxy.newProxyInstance(ViewListener.class.getClassLoader(), new Class<?>[]
                {ViewListener.class}, (proxy, method, args) -> method.getName().equals("getLayoutList")
                ? new ArrayList<>(this.pages.keySet()) : this.pages.get(args[0]));
        }

        @Override
        public DiagramTileRegistry getDiagramTileRegistry()
        {
            return this.registry;
        }

        /** A tile at x on page, at this raw address, with a label named `name` showing it. */
        void put(String page, componentType type, int x, int raw, accessoryDecoderType protocol, String name)
            throws Exception
        {
            this.pages.computeIfAbsent(page, p -> new LayoutDiagram(p, 4, 1, null, null)).addComponent(type, x, 0, 0, 0,
                raw, raw, protocol, null);
            label(page, x, name);
        }

        /** Another label showing the square at x on page, as a popup's copy of it does. */
        void label(String page, int x, String name)
        {
            LayoutLabel label = new LayoutLabel(null, new JPanel(), 30, null, false);
            label.setIcon(new ImageIcon(new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB)));
            label.setName(name);
            this.registry.register(new TileKey(page, x, 0), label);
            this.labels.add(label);
        }

        /** The labels lit now, sorted. */
        List<String> lit()
        {
            List<String> out = new ArrayList<>();
            for (LayoutLabel l : this.labels) if (l.isFlashOutstanding()) out.add(l.getName());
            Collections.sort(out);
            return out;
        }

        /** The labels lit now, each then put out for the next ask. */
        List<String> litAndEnded()
        {
            List<String> out = lit();
            for (LayoutLabel l : this.labels) l.endFlash();
            return out;
        }
    }
}
