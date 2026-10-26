package support;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Finds runs of 60 or more tokens that stand twice in the sources, the way docs/tools/dupscan.py does (this is its port,
 * and `main` prints the numbers dupscan prints so the two can be compared).
 *
 * A token is a keyword, a punctuation mark, or `I` for any name and `L` for any literal, so a copy with other names and
 * numbers is still a copy.  Comments, the NetBeans generated regions, import and package lines and generic arguments do
 * not count, and a qualified name is one name.  Copies that overlap are one group; a group is named by the classes that
 * hold its copies, never by path, so a class moving to another package is the same group.  Files are taken in the
 * order of their class names for the same reason (overlapping copies merge in file order).
 */
public final class CopyScan
{
    public static final int MIN = 60;

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList((
        "abstract assert boolean break byte case catch char class const continue default do double else enum extends "
        + "final finally float for goto if implements import instanceof int interface long native new package private "
        + "protected public return short static strictfp super switch synchronized this throw throws transient try void "
        + "volatile while true false null").split(" ")));

    private static final Set<String> TYPE_ARGUMENT = new HashSet<>(Arrays.asList(
        "I", ".", ",", "?", "extends", "super", "[", "]", "<", ">", "&"));

    /** One group of copies. */
    public static final class Group
    {
        /** The most tokens in any one copy. */
        public final int tokens;

        /** The classes holding a copy, one entry per copy, sorted. */
        public final List<String> classes;

        /** Where each copy is: Class.java:first-last. */
        public final List<String> places;

        Group(int tokens, List<String> classes, List<String> places)
        {
            this.tokens = tokens;
            this.classes = classes;
            this.places = places;
        }

        /** The classes with how many copies each, e.g. `NodeAnd:1,NodeOr:1`: what names the group. */
        public String key()
        {
            Map<String, Integer> counts = new TreeMap<>();

            for (String c : classes) counts.put(c, counts.containsKey(c) ? counts.get(c) + 1 : 1);

            StringBuilder out = new StringBuilder();

            for (Map.Entry<String, Integer> e : counts.entrySet())
            {
                out.append(out.length() == 0 ? "" : ",").append(e.getKey()).append(':').append(e.getValue());
            }

            return out.toString();
        }

        /** The line the baseline holds: the key and the tokens of the longest copy. */
        public String entry()
        {
            return key() + " (" + tokens + ")";
        }
    }

    private static final class Tokens
    {
        final int[] id;
        final int[] line;

        Tokens(int[] id, int[] line)
        {
            this.id = id;
            this.line = line;
        }
    }

    private CopyScan()
    {
    }

    /** The groups of copies under `src`, longest first.  @throws IOException when a file cannot be read */
    public static List<Group> scan(File src) throws IOException
    {
        Map<String, String> sources = new LinkedHashMap<>();

        collect(src, src, sources);

        return scan(sources);
    }

    /** The groups of copies in sources given as relative path to text, longest first. */
    public static List<Group> scan(Map<String, String> sources)
    {
        List<String> paths = new ArrayList<>(sources.keySet());

        Collections.sort(paths, new Comparator<String>()
        {
            @Override
            public int compare(String a, String b)
            {
                int c = nameOf(a).compareTo(nameOf(b));

                return c != 0 ? c : a.compareTo(b);
            }
        });

        Map<String, Integer> ids = new HashMap<>();

        int files = paths.size();
        Tokens[] toks = new Tokens[files];
        int[] base = new int[files + 1];

        for (int f = 0; f < files; f++)
        {
            toks[f] = tokenize(sources.get(paths.get(f)), ids);
            base[f + 1] = base[f] + toks[f].id.length;
        }

        int[] fileOf = new int[base[files]];

        for (int f = 0; f < files; f++) Arrays.fill(fileOf, base[f], base[f + 1], f);

        Map<Long, int[]> buckets = new LinkedHashMap<>();

        long top = 1;

        for (int i = 0; i < MIN; i++) top *= 1000003L;

        for (int f = 0; f < files; f++)
        {
            int[] t = toks[f].id;

            long h = 0;

            for (int i = 0; i < t.length; i++)
            {
                h = h * 1000003L + t[i] + 1;

                if (i >= MIN) h -= (t[i - MIN] + 1) * top;

                if (i < MIN - 1) continue;

                int[] locs = buckets.get(h);

                buckets.put(h, add(locs, base[f] + i - MIN + 1));
            }
        }

        List<int[]> frags = new ArrayList<>();
        List<List<Integer>> fragsIn = new ArrayList<>();
        List<Integer> parent = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int f = 0; f < files; f++) fragsIn.add(new ArrayList<Integer>());

        for (int[] locs : buckets.values())
        {
            int count = locs[0];

            if (count < 2 || count > 40) continue;

            for (int a = 1; a <= count; a++)
            {
                for (int b = a + 1; b <= count; b++)
                {
                    int fa = fileOf[locs[a]], fb = fileOf[locs[b]];
                    int ia = locs[a] - base[fa], ib = locs[b] - base[fb];
                    int[] na = toks[fa].id, nb = toks[fb].id;

                    if (ia > 0 && ib > 0 && na[ia - 1] == nb[ib - 1]) continue;

                    if (fa == fb && Math.abs(ia - ib) < MIN) continue;

                    int k = 0;

                    while (ia + k < na.length && ib + k < nb.length && na[ia + k] == nb[ib + k])
                    {
                        if (fa == fb && ia + k >= ib) break;

                        k++;
                    }

                    if (k < MIN || !seen.add(fa + ":" + ia + ":" + fb + ":" + ib)) continue;

                    int x = fragment(frags, fragsIn, parent, fa, ia, k);
                    int y = fragment(frags, fragsIn, parent, fb, ib, k);

                    parent.set(find(parent, x), find(parent, y));
                }
            }
        }

        Map<Integer, List<Integer>> clusters = new LinkedHashMap<>();

        for (int i = 0; i < frags.size(); i++)
        {
            int root = find(parent, i);

            if (!clusters.containsKey(root)) clusters.put(root, new ArrayList<Integer>());

            clusters.get(root).add(i);
        }

        List<Group> out = new ArrayList<>();

        for (List<Integer> members : clusters.values())
        {
            if (members.size() < 2) continue;

            int tokens = 0;

            List<String> classes = new ArrayList<>();
            List<String> places = new ArrayList<>();

            for (int m : members)
            {
                int[] fr = frags.get(m);

                String name = nameOf(paths.get(fr[0]));

                tokens = Math.max(tokens, fr[2]);
                classes.add(name.substring(0, name.length() - 5));

                int[] line = toks[fr[0]].line;

                places.add(name + ":" + line[fr[1]] + "-" + line[Math.min(fr[1] + fr[2], line.length) - 1]);
            }

            Collections.sort(classes);
            Collections.sort(places);

            out.add(new Group(tokens, classes, places));
        }

        Collections.sort(out, new Comparator<Group>()
        {
            @Override
            public int compare(Group a, Group b)
            {
                int c = b.tokens * (b.classes.size() - 1) - a.tokens * (a.classes.size() - 1);

                return c != 0 ? c : a.entry().compareTo(b.entry());
            }
        });

        return out;
    }

    /** The copy of the same run that is already known, grown to cover this one, or a new one. */
    private static int fragment(List<int[]> frags, List<List<Integer>> fragsIn, List<Integer> parent, int f, int i,
        int k)
    {
        for (int idx : fragsIn.get(f))
        {
            int[] fr = frags.get(idx);

            int fi = fr[1], fk = fr[2];

            if (fi < i + k && i < fi + fk && Math.min(fi + fk, i + k) - Math.max(fi, i) >= 0.7 * Math.min(fk, k))
            {
                if (i + k > fi + fk || i < fi)
                {
                    fr[1] = Math.min(fi, i);
                    fr[2] = Math.max(fi + fk, i + k) - Math.min(fi, i);
                }

                return idx;
            }
        }

        frags.add(new int[]{f, i, k});
        parent.add(parent.size());
        fragsIn.get(f).add(frags.size() - 1);

        return frags.size() - 1;
    }

    private static int find(List<Integer> parent, int x)
    {
        while (parent.get(x) != x)
        {
            parent.set(x, parent.get(parent.get(x)));
            x = parent.get(x);
        }

        return x;
    }

    /** Appends to a list kept in an int array whose first element is its length. */
    private static int[] add(int[] list, int value)
    {
        if (list == null) list = new int[]{0, 0};

        if (list[0] + 1 >= list.length) list = Arrays.copyOf(list, list.length * 2);

        list[++list[0]] = value;

        return list;
    }

    private static String nameOf(String path)
    {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static void collect(File root, File dir, Map<String, String> out) throws IOException
    {
        File[] children = dir.listFiles();

        if (children == null) return;

        Arrays.sort(children);

        for (File child : children)
        {
            if (child.isDirectory()) collect(root, child, out);
            else if (child.getName().endsWith(".java"))
            {
                out.put(root.toURI().relativize(child.toURI()).getPath(),
                    new String(Files.readAllBytes(child.toPath()), StandardCharsets.UTF_8));
            }
        }
    }

    /** The token ids and lines of one file: names and literals as `I` and `L`, then imports and type noise dropped. */
    private static Tokens tokenize(String text, Map<String, Integer> ids)
    {
        List<String> norm = new ArrayList<>();
        List<Integer> lines = new ArrayList<>();

        int n = text.length(), line = 1;

        boolean generated = false;

        for (int i = 0; i < n; )
        {
            char c = text.charAt(i);

            if (c == '\n')
            {
                line++;
                i++;

                continue;
            }

            if (Character.isWhitespace(c))
            {
                i++;

                continue;
            }

            int end = -1;

            String token;

            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/')
            {
                end = text.indexOf('\n', i);

                end = end < 0 ? n : end;

                String comment = text.substring(i, end);

                if (comment.contains("GEN-BEGIN")) generated = true;
                else if (comment.contains("GEN-END")) generated = false;

                i = end;

                continue;
            }

            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*' && text.indexOf("*/", i + 2) >= 0)
            {
                end = text.indexOf("*/", i + 2) + 2;
                line += count(text, i, end);
                i = end;

                continue;
            }

            if (c == '"' || c == '\'')
            {
                end = quoted(text, i);
            }

            if (end > 0)
            {
                if (!generated)
                {
                    norm.add("L");
                    lines.add(line);
                }

                line += count(text, i, end);
                i = end;

                continue;
            }

            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_' || c == '$')
            {
                end = i + 1;

                while (end < n && (isWord(text.charAt(end)) || text.charAt(end) == '$')) end++;

                String word = text.substring(i, end);

                token = KEYWORDS.contains(word) ? word : "I";
            }
            else if (c >= '0' && c <= '9')
            {
                end = i + 1;

                while (end < n && (isWord(text.charAt(end)) || text.charAt(end) == '.')) end++;

                token = "L";
            }
            else
            {
                end = i + 1;
                token = String.valueOf(c);
            }

            if (!generated)
            {
                norm.add(token);
                lines.add(line);
            }

            i = end;
        }

        return noise(norm, lines, ids);
    }

    private static boolean isWord(char c)
    {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
    }

    private static int count(String text, int from, int to)
    {
        int lines = 0;

        for (int i = from; i < to; i++) if (text.charAt(i) == '\n') lines++;

        return lines;
    }

    /** The end of a string or character literal starting at `from`, or -1 when it is never closed. */
    private static int quoted(String text, int from)
    {
        char quote = text.charAt(from);

        for (int j = from + 1; j < text.length(); j++)
        {
            char c = text.charAt(j);

            if (c == '\\') j++;
            else if (c == quote) return j + 1;
        }

        return -1;
    }

    private static Tokens noise(List<String> raw, List<Integer> rawLines, Map<String, Integer> ids)
    {
        List<String> ts = new ArrayList<>();
        List<Integer> ls = new ArrayList<>();

        for (int i = 0; i < raw.size(); i++)
        {
            String t = raw.get(i);

            if ((t.equals("import") || t.equals("package"))
                && (i == 0 || raw.get(i - 1).equals(";") || raw.get(i - 1).equals("}")))
            {
                while (i < raw.size() && !raw.get(i).equals(";")) i++;

                continue;
            }

            ts.add(t);
            ls.add(rawLines.get(i));
        }

        List<Integer> out = new ArrayList<>();
        List<Integer> outLines = new ArrayList<>();
        List<String> kept = new ArrayList<>();

        for (int i = 0; i < ts.size(); )
        {
            String t = ts.get(i);

            String last = kept.isEmpty() ? null : kept.get(kept.size() - 1);

            if (t.equals("I") && "I".equals(last) && i > 0 && ts.get(i - 1).equals("."))
            {
                i++;

                continue;
            }

            if (t.equals(".") && "I".equals(last) && i + 1 < ts.size() && ts.get(i + 1).equals("I"))
            {
                i++;

                continue;
            }

            if (t.equals("<") && "I".equals(last))
            {
                int depth = 0, j = i;

                while (j < ts.size() && TYPE_ARGUMENT.contains(ts.get(j)))
                {
                    depth += ts.get(j).equals("<") ? 1 : ts.get(j).equals(">") ? -1 : 0;
                    j++;

                    if (depth == 0) break;
                }

                if (depth == 0 && j > i + 1)
                {
                    i = j;

                    continue;
                }
            }

            kept.add(t);
            outLines.add(ls.get(i));
            i++;
        }

        int[] id = new int[kept.size()];
        int[] line = new int[kept.size()];

        for (int i = 0; i < id.length; i++)
        {
            Integer known = ids.get(kept.get(i));

            if (known == null)
            {
                known = ids.size();
                ids.put(kept.get(i), known);
            }

            id[i] = known;
            line[i] = outLines.get(i);
        }

        return new Tokens(id, line);
    }

    /** Prints what dupscan.py prints first (files, tokens, clusters, duplicated tokens), then the baseline lines. */
    public static void main(String[] args) throws IOException
    {
        File src = new File(args.length > 0 ? args[0] : "src");

        List<Group> groups = scan(src);

        long dup = 0;

        for (Group g : groups) dup += (long) g.tokens * (g.classes.size() - 1);

        System.out.println(groups.size() + " clusters, " + dup + " duplicated tokens");

        for (Group g : groups) System.out.println("        \"" + g.entry() + "\",");
    }
}
