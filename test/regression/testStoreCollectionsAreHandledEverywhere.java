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
 * Every collection the store keeps is handled everywhere the store does bookkeeping.
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
 * **What this test does, and what it deliberately does not.** The review's own recommendation is a
 * registry of kept collections, each knowing how to do the bookkeeping to itself - roughly 830 lines
 * restructured. That is the better end state and it is also the biggest blast radius in the file, so it
 * is not what this is. This closes the defect CLASS by a cheaper route: it reads the source, and a
 * collection missing from a site fails the build with the site named.
 *
 * Every omission listed above would have been caught by it on the commit that introduced it.
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
    }

    /**
     * The bookkeeping sites, by the name of the method or constant that performs them.
     */
    private static final String[] SITES =
    {
        "sharedFields", "KNOWN_SHARED", "readShared", "clear", "clearShared", "renamePage",
        "moveTiles", "forgetSquares", "snapshotPage", "restorePage", "reconcile", "applyTo",
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
        // excludedPages is keyed by PAGE, not by square, so the square-level sites have nothing to say
        // about it.
        EXEMPT.put("moveTiles:excludedPages", "keyed by page, and moving squares does not move a page");
        EXEMPT.put("forgetSquares:excludedPages", "keyed by page; deleting squares never deletes one");
        EXEMPT.put("snapshotPage:excludedPages", "the page's own exclusion is not part of its contents");
        EXEMPT.put("restorePage:excludedPages", "as snapshotPage");
        EXEMPT.put("reconcile:excludedPages", "reconcile compares SQUARES against the diagram");

        // Written under a different name in the file than in the code
        EXEMPT.put("KNOWN_SHARED:disabledPortals", "listed by its JSON name, disabledLinks");

        // Handled by a helper of its own, called from here
        EXEMPT.put("reconcile:captions", "done by reconcileCaptions - a caption goes when either the "
            + "square it is drawn on or the sensor it is about does, which is a rule of its own");

        // applyTo puts the setup onto the TILE GRAPH, and the graph is a model of TRACK: what is
        // paired to what, which links are switched off, which way each route runs. A name, a length,
        // a caption or a barred arrival is a fact about the railway that the graph has nowhere to put
        // and no use for. This is the one site where "handled everywhere" is the wrong rule, so it is
        // written down once, here, rather than argued about each time somebody reads that method.
        for (String notTrack : new String[] {"pointNames", "stations", "tileLengths", "barredArrivals",
            "stationSignals", "captions", "linkNames", "excludedPages"})
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
            + ". Add each to KEPT - and then to all " + SITES.length + " bookkeeping sites, which is "
            + "what the rest of this test will insist on - or to NOT_KEPT with the reason it is not "
            + "part of the setup. A collection nobody classified is how the eleventh one took five "
            + "commits to finish adding");
    }

    /**
     * And every kept collection is named at every site.
     */
    @Test
    public void testEveryKeptCollectionIsHandledAtEverySite() throws Exception
    {
        if (!SOURCE.isFile()) return;

        String source = new String(Files.readAllBytes(SOURCE.toPath()), StandardCharsets.UTF_8);

        List<String> missing = new ArrayList<>();

        for (String site : SITES)
        {
            String body = bodyOf(source, site);

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
            "a collection the store keeps is not handled at one of its bookkeeping sites. Each of "
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
        if (!SOURCE.isFile()) return;

        String source = new String(Files.readAllBytes(SOURCE.toPath()), StandardCharsets.UTF_8);

        List<String> stale = new ArrayList<>();

        for (String key : EXEMPT.keySet())
        {
            String site = key.substring(0, key.indexOf(':'));
            String kept = key.substring(key.indexOf(':') + 1);

            String body = bodyOf(source, site);

            if (body != null && body.contains(kept)) stale.add(key);
        }

        assertEquals(stale, new ArrayList<String>(),
            "these collections ARE handled at these sites now, so the exemptions claiming they do not "
            + "apply are wrong. An exemption is a statement about the code, and a stale one is worse "
            + "than none - it is a comment that reads as a decision");
    }

    /**
     * The text of one method, or of one constant's initialiser, by brace matching.
     *
     * Crude and adequate: this asks whether a name is mentioned, not what is done with it. Something
     * that reads the file cannot tell a real handling from a mention in a comment - which is a
     * weakness worth stating rather than hiding, and still catches every omission listed in the class
     * javadoc, because a collection nobody handled is a collection nobody wrote about either.
     */
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
