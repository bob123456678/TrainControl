package regression;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyCompanionStore;

/**
 * Every collection the store keeps is in the registry, and the registry is what the sites walk.
 *
 * DD-A1 / OB-025. `AutonomyCompanionStore` keeps eleven collections and repeats the same per-collection
 * shape at fourteen sites - saving, loading, clearing, renaming a page, moving squares, forgetting
 * squares, snapshotting and restoring a page, reconciling against the diagram, applying to another
 * store. Adding one collection means finding all fourteen.
 *
 * **The eleventh took five commits and five days.** `disabledPortals` arrived on 2026-08-17 wired into
 * eight sites, and the other four were each found afterwards as a bug: the rename loop, the move, the
 * snapshot/restore pair, and forgetting a square. `renamePage`'s own comment records what the first of
 * them cost - "a link switched off is remembered by its square, so a rename turned every one of them
 * back on - silently, and only on the renamed page."
 *
 * The same shape produced at least four more, all documented in the source: `clear()` missing
 * `stationSignals` while `clearShared()` had it, which threw a cancelled signal on real hardware;
 * `captions` missing from `KNOWN_SHARED`, which reverted every caption edit on the next save;
 * directions left behind by every move; captions rekeyed on one side only.
 *
 * **This test used to be the whole answer, and now it is half of one.** For as long as each site
 * listed the collections by hand, this read the source and failed the build when a collection was
 * missing from one of them - every omission above would have been caught on the commit that
 * introduced it. The registry the review actually recommended landed in OB-025, so the sites no longer
 * list anything: they walk `kept()`. Adding a collection is one entry.
 *
 * So what is left to check is different, and smaller:
 *
 *   - Nothing the store holds is unclassified. Unchanged, and still the first line of defence: a
 *     collection nobody classified is how the eleventh one took five commits to finish adding.
 *   - Every kept collection is IN the registry. This is the new single point of failure and it is the
 *     only one that a compiler cannot check - a `Kept` subclass that forgets a method will not build,
 *     but a collection that is simply never registered builds perfectly and does nothing.
 *   - Every site that used to list them walks the registry instead. Guards against a collection being
 *     quietly re-inlined at one site, which would put the divergence back.
 *   - `reconcile` and `applyTo` still name every collection, because those two are still written by
 *     hand on purpose (DD-D9): each asks a question the registry cannot express.
 *
 * The exemptions below are the other half of the value. DD-A1's complaint about `reconcile` was not
 * only that two collections were missing, but that "there is no comment claiming this is deliberate,
 * and there is one for every other decision in that method." An exemption here is that comment, in a
 * place that fails if it stops being true.
 *
 * @author Adam
 */
public class testStoreCollectionsAreHandledEverywhere
{
    private static final File SOURCE =
        new File("src/org/traincontrol/automationui/AutonomyCompanionStore.java");

    /**
     * The collections that are part of the SETUP - the things a square or a page carries.
     */
    private static final Set<String> KEPT = new LinkedHashSet<>(java.util.Arrays.asList(
        "pointNames", "stations", "tileLengths", "tileDirections", "barredArrivals", "portals",
        "blockedPoints",
        "stationSignals", "captions", "linkNames", "excludedPages", "disabledPortals"));

    /**
     * Everything else the store holds, and why it is not setup.
     *
     * Listed rather than ignored: a new field has to be classified, and classifying it is the moment
     * to notice it needs the fourteen sites.
     */
    private static final Map<String, String> NOT_KEPT = new LinkedHashMap<>();

    static
    {
        NOT_KEPT.put("configurations", "the configurations themselves, not a per-square setting");
        NOT_KEPT.put("unknownSharedFields", "what a NEWER build wrote and this one must not destroy");
        NOT_KEPT.put("pageNameToId", "page identity bookkeeping, rebuilt on load");
        NOT_KEPT.put("pageIdToName", "page identity bookkeeping, rebuilt on load");
        NOT_KEPT.put("pageNamesWhenWritten", "page identity bookkeeping, rebuilt on load");
        NOT_KEPT.put("pageIdConflicts", "diagnostics about a load, not setup");
        // The one collection whose whole purpose is that no bookkeeping site touches it. It holds the
        // file's own JSON for pages that are not loaded, keyed by FIELD name rather than by square,
        // and it is written back exactly as it came in. A rename, a move or a delete acting on it
        // would be acting on a page nobody can see - which is the loss it exists to prevent (OB-067).
        NOT_KEPT.put("heldForAbsentPages", "entries for pages that are not loaded, kept verbatim");
    }

    /**
     * The two bookkeeping sites still written out by hand, and why they are.
     *
     * DD-D9, carried into OB-025's ticket as a condition on doing it at all: these must stay
     * hand-written. Each asks a question a registry cannot express. `reconcile` decides what a
     * square's absence from the diagram MEANS, and the answer differs per collection - a caption goes
     * when either the square it is drawn on or the sensor it is about goes, which is a rule of its
     * own. `applyTo` populates the tile GRAPH, which models track and has nowhere to put a name.
     *
     * A registry entry answering "not applicable" for these would be pretending they are uniform, so
     * they are still checked the old way: name every collection, or say why not.
     */
    private static final String[] SITES = { "reconcile", "applyTo" };

    /**
     * The sites that DO walk the registry, by the name of the method that performs them.
     *
     * Each of these used to carry its own list of the twelve collections. What is checked now is that
     * they still walk `kept()` - a collection re-inlined at one of them would put the divergence back,
     * one site at a time, which is exactly how the file got into the state OB-025 was raised about.
     */
    private static final String[] REGISTRY_SITES =
    {
        "sharedFields", "readShared", "clear", "clearShared", "renamePage", "moveTiles",
        "forgetSquares", "snapshotPage", "restorePage",
        // deletePage gathers the page's squares and hands them to forgetSquares. It is on this list
        // because the GATHERING has to know every collection: one missed means a page's worth of that
        // one setting survives the page, keyed to track that is gone.
        "deletePage",
        // Not a bookkeeping site but the same failure: the file's known-field list. A collection
        // missing from it is read into its own collection AND kept as an unknown field, so the next
        // save writes both and the stale copy wins. That is what reverted every caption edit.
        "knownShared",
    };

    /**
     * Where a collection is deliberately absent from a site, and why.
     *
     * Keyed "site:collection". Every line here is a decision somebody made; a line that stops being
     * needed fails this test rather than sitting unread.
     */
    private static final Map<String, String> EXEMPT = new LinkedHashMap<>();

    static
    {
        // excludedPages is keyed by PAGE, not by square.  The square-level sites used to need an
        // exemption each; now the collection says so once, in its own registry entry, and only the
        // hand-written pair still need it here.
        EXEMPT.put("reconcile:excludedPages", "reconcile compares SQUARES against the diagram");

        // Handled by a helper of its own, called from here
        EXEMPT.put("reconcile:captions", "done by reconcileCaptions - a caption goes when either the "
            + "square it is drawn on or the sensor it is about does, which is a rule of its own");

        // applyTo puts the setup onto the TILE GRAPH, and the graph is a model of TRACK: what is
        // paired to what, which links are switched off, which way each route runs. A name, a length,
        // a caption or a barred arrival is a fact about the railway that the graph has nowhere to put
        // and no use for. This is the one site where "handled everywhere" is the wrong rule, so it is
        // written down once, here, rather than argued about each time somebody reads that method.
        for (String notTrack : new String[] {"pointNames", "stations", "tileLengths", "barredArrivals",
            "stationSignals", "captions", "linkNames", "excludedPages", "blockedPoints"})
        {
            EXEMPT.put("applyTo:" + notTrack, "applyTo populates the tile GRAPH, which models track - "
                + "pairings, switched-off links and directions. This is not a property of track");
        }
    }

    /**
     * Nothing the store holds is unclassified.
     */
    @Test
    public void testEveryCollectionIsEitherKeptOrExplainedAway()
    {
        List<String> unclassified = new ArrayList<>();

        for (Field field : AutonomyCompanionStore.class.getDeclaredFields())
        {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;

            if (!Map.class.isAssignableFrom(field.getType())
                && !Set.class.isAssignableFrom(field.getType())) continue;

            if (KEPT.contains(field.getName()) || NOT_KEPT.containsKey(field.getName())) continue;

            unclassified.add(field.getName());
        }

        assertTrue(unclassified.isEmpty(),
            "the store holds collections this test has never been told about: " + unclassified
            + ". Add each to KEPT - and then to the store's registry, which is what the rest of this "
            + "test will insist on - or to NOT_KEPT with the reason it is not part of the setup. A "
            + "collection nobody classified is how the eleventh one took five commits to finish "
            + "adding");
    }

    /**
     * Every kept collection is in the registry.
     *
     * The one thing the compiler cannot check about this design. A `Kept` subclass that forgets a
     * method will not build; a collection that is simply never registered builds perfectly, and does
     * nothing at ten sites at once - which is worse than the state this replaced, not better, because
     * there is now nowhere else it could have been handled.
     */
    @Test
    public void testEveryKeptCollectionIsRegistered() throws Exception
    {
        String body = withoutComments(bodyOf(source(), "kept"));

        assertNotNull(body, "no kept() in the store - if the registry was renamed, rename it here "
            + "too, because this test is the only thing checking every collection is in it");

        List<String> missing = new ArrayList<>();

        for (String kept : KEPT)
        {
            if (!body.contains(kept)) missing.add(kept);
        }

        assertEquals(missing, new ArrayList<String>(),
            missing + ": a collection the store keeps is not in the registry, so nothing clears it, "
            + "saves it, loads it, renames it, moves it, forgets it, snapshots it or deletes it with "
            + "its page. That is every bookkeeping site at once");
    }

    /**
     * And every site that used to list the collections walks the registry instead.
     */
    @Test
    public void testEverySiteWalksTheRegistry() throws Exception
    {
        List<String> notWalking = new ArrayList<>();

        for (String site : REGISTRY_SITES)
        {
            String body = withoutComments(bodyOf(source(), site));

            assertNotNull(body, "no site called " + site + " in the store - if it was renamed, rename "
                + "it in REGISTRY_SITES too, because this test is the only thing checking it still "
                + "covers everything");

            if (!body.contains("kept()")) notWalking.add(site);
        }

        assertEquals(notWalking, new ArrayList<String>(),
            notWalking + ": a bookkeeping site has stopped walking the registry. Whatever it does "
            + "instead is a list of collections that can now fall out of step with the other nine - "
            + "which is the state OB-025 was raised about, arriving one site at a time");
    }

    /**
     * And every kept collection is named at the sites still written by hand.
     */
    @Test
    public void testEveryKeptCollectionIsHandledAtEverySite() throws Exception
    {
        assertTrue(SOURCE.isFile(),
            "cannot find " + SOURCE.getAbsolutePath() + " - a test that reads the source cannot pass by not finding it. This returned quietly, so renaming or moving that file would have taken this rule with it and said nothing");

        String source = new String(Files.readAllBytes(SOURCE.toPath()), StandardCharsets.UTF_8);

        List<String> missing = new ArrayList<>();

        for (String site : SITES)
        {
            String body = withoutComments(bodyOf(source, site));

            assertNotNull(body, "no site called " + site + " in the store - if it was renamed, rename "
                + "it in SITES too, because this test is the only thing checking it covers everything");

            for (String kept : KEPT)
            {
                if (body.contains(kept)) continue;

                if (EXEMPT.containsKey(site + ":" + kept)) continue;

                missing.add(site + " says nothing about " + kept);
            }
        }

        assertEquals(missing, new ArrayList<String>(),
            missing + ": a collection the store keeps is not handled at one of its bookkeeping sites. Each of "
            + "these is the shape that produced the disabledPortals defects - a setting that survives "
            + "a rename, a move, a delete or a discard because one site of " + SITES.length + " never "
            + "heard of it. Handle it, or add a line to EXEMPT saying why it does not apply");
    }

    /**
     * An exemption that is no longer needed is removed rather than left to rot.
     */
    @Test
    public void testNoExemptionIsStale() throws Exception
    {
        assertTrue(SOURCE.isFile(),
            "cannot find " + SOURCE.getAbsolutePath() + " - a test that reads the source cannot pass by not finding it. This returned quietly, so renaming or moving that file would have taken this rule with it and said nothing");

        String source = new String(Files.readAllBytes(SOURCE.toPath()), StandardCharsets.UTF_8);

        List<String> stale = new ArrayList<>();

        for (String key : EXEMPT.keySet())
        {
            String site = key.substring(0, key.indexOf(':'));
            String kept = key.substring(key.indexOf(':') + 1);

            String body = withoutComments(bodyOf(source, site));

            if (body != null && body.contains(kept)) stale.add(key);
        }

        assertEquals(stale, new ArrayList<String>(),
            "these collections ARE handled at these sites now, so the exemptions claiming they do not "
            + "apply are wrong. An exemption is a statement about the code, and a stale one is worse "
            + "than none - it is a comment that reads as a decision. Stale: " + stale);
    }

    /**
     * The text of one method, or of one constant's initialiser, by brace matching.
     *
     * Crude and adequate: this asks whether a name is mentioned, not what is done with it. Something
     * that reads the file cannot tell a real handling from a mention in a comment - which is a
     * weakness worth stating rather than hiding, and still catches every omission listed in the class
     * javadoc, because a collection nobody handled is a collection nobody wrote about either.
     */
    /**
     * A method body with its comments taken out.
     *
     * NR-8, from the night review, and it produced its own counter-example. This test asks whether a
     * collection's NAME appears at a site, and its javadoc above argues that "a collection nobody
     * handled is a collection nobody wrote about either" - which stopped being true the moment
     * forgetSquares lost its `tileDirections.remove(key)` line and gained a comment explaining why it
     * was dead. Delete the real handling tomorrow and the string would still be there, in the comment,
     * and this test would stay green for the very collection it guards.
     *
     * The same trap caught this change from the other side: a comment added to reconcile mentioning
     * captions made an exemption read as stale.
     *
     * A test that reads source has to read the CODE. Copied rather than shared with the two other
     * tests that do this - a test helper reaching into another test class is a dependency between
     * things that are supposed to fail independently.
     */
    /**
     * The store's source, or a failure that says so.
     *
     * A test that reads the source cannot pass by not finding it. This used to be three copies of the
     * same two lines; the assertion inside is the reason they existed and it is kept.
     *
     * @return the file's text
     * @throws Exception if it cannot be read
     */
    private String source() throws Exception
    {
        assertTrue(SOURCE.isFile(),
            "cannot find " + SOURCE.getAbsolutePath() + " - a test that reads the source cannot pass "
            + "by not finding it. Renaming or moving that file would otherwise have taken this rule "
            + "with it and said nothing");

        return new String(Files.readAllBytes(SOURCE.toPath()), StandardCharsets.UTF_8);
    }

    private String withoutComments(String body)
    {
        if (body == null) return null;

        StringBuilder out = new StringBuilder();

        boolean inLine = false, inBlock = false;

        for (int i = 0; i < body.length(); i++)
        {
            char c = body.charAt(i);
            char next = i + 1 < body.length() ? body.charAt(i + 1) : ' ';

            if (inLine)
            {
                if (c == '\n') { inLine = false; out.append(c); }
            }
            else if (inBlock)
            {
                if (c == '*' && next == '/') { inBlock = false; i++; }
            }
            else if (c == '/' && next == '/') inLine = true;
            else if (c == '/' && next == '*') inBlock = true;
            else out.append(c);
        }

        return out.toString();
    }

    private String bodyOf(String source, String name)
    {
        String longest = null;

        // Anchored on a DECLARATION - start of line, a modifier, then the name - so a call to
        // forgetSquares() elsewhere in the file is not mistaken for the method itself.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "^[ \t]*(?:private|public|protected)[^\n=;]*\\b" + name + "\\s*\\(",
            java.util.regex.Pattern.MULTILINE).matcher(source);

        // The LONGEST of them, because several of these are overloaded and the short one is a two-line
        // delegate to the real one. Reading the delegate would report every collection as missing,
        // which is a false alarm loud enough to get the whole test switched off.
        while (m.find())
        {
            String body = braced(source, m.end() - 1);

            if (body != null && (longest == null || body.length() > longest.length())) longest = body;
        }

        if (longest != null) return longest;

        // Not a method - a constant, whose "body" is its initialiser up to the semicolon
        java.util.regex.Matcher constant = java.util.regex.Pattern.compile(
            "^[ \t]*(?:private|public|protected)[^\n]*\\b" + name + "\\s*=",
            java.util.regex.Pattern.MULTILINE).matcher(source);

        if (!constant.find()) return null;

        int end = source.indexOf(';', constant.end());

        return end < 0 ? null : source.substring(constant.start(), end + 1);
    }

    /**
     * The braced block starting at or after {@code from}.
     */
    private String braced(String source, int from)
    {
        int open = source.indexOf('{', from);

        if (open < 0) return null;

        int depth = 0;

        for (int i = open; i < source.length(); i++)
        {
            char c = source.charAt(i);

            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }

        return null;
    }

}
