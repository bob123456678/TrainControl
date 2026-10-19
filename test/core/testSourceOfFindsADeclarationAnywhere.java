package core;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import support.SourceOf;

/**
 * `support.SourceOf`, the one way a claim reads the program's source as text, finds a declaration in whichever file
 * holds it, once, and fails loudly when it is missing or ambiguous (FR-136).
 *
 * The source-reading claims are converted to it so that moving code between files cannot turn them vacuous; if the
 * helper itself quietly found nothing, or found the first of two, every converted claim would inherit that.
 */
public class testSourceOfFindsADeclarationAnywhere
{
    /** A private rule two tooltip claims ask by name; any declaration that exists once would do. */
    private static final String ONE_LINE_RULE = "private static boolean fitsOnOneLine(String text)";

    /**
     * Found without naming its file: the declaration, from its signature to its closing brace, inside the file that
     * holds it.
     */
    @Test
    public void testADeclarationIsFoundWhereverItIs()
    {
        String declaration = SourceOf.declaration(ONE_LINE_RULE);

        assertTrue(declaration.startsWith(ONE_LINE_RULE), "the declaration does not start at its signature: "
            + declaration);
        assertTrue(declaration.endsWith("}"), "the declaration does not end at its closing brace: " + declaration);
        assertTrue(declaration.contains("return"), "the body was not included: " + declaration);
        assertTrue(SourceOf.fileDeclaring(ONE_LINE_RULE).contains(declaration),
            "the file said to declare it does not hold it");
    }

    /**
     * The body leaves the signature out, so "the body calls fitsOnOneLine" cannot be met by the declaration's name.
     */
    @Test
    public void testTheBodyIsBetweenTheBraces()
    {
        String declaration = SourceOf.declaration(ONE_LINE_RULE);

        String body = SourceOf.body(ONE_LINE_RULE);

        assertFalse(body.contains("fitsOnOneLine"), "the body holds the signature: " + body);
        assertTrue(declaration.endsWith("{" + body + "}"), "the body is not what the braces hold");
    }

    /**
     * A signature that is nowhere fails, rather than handing back an empty text a negative claim would pass over.
     */
    @Test
    public void testAMissingDeclarationFails()
    {
        AssertionError missed = expectFailure(() -> SourceOf.declaration("private void noSuchDeclarationAnywhere("));

        assertTrue(missed.getMessage().contains("nowhere"), missed.getMessage());
    }

    /**
     * A signature in two places fails and names both, rather than answering with whichever file came first.
     */
    @Test
    public void testADeclarationInTwoPlacesFails()
    {
        AssertionError twice = expectFailure(() -> SourceOf.declaration("public String toString()"));

        assertTrue(twice.getMessage().contains(" times, at "), twice.getMessage());
        assertTrue(twice.getMessage().split("\\.java:").length > 2, "fewer than two places named: "
            + twice.getMessage());

        expectFailure(() -> SourceOf.declarationIn("void a() {}\nvoid a() {}", "void a("));
    }

    /**
     * A comment that quotes a signature is not a second declaration, and braces in strings, characters and comments
     * do not end a body early.
     */
    @Test
    public void testCommentsAndLiteralsDoNotConfuseTheCut()
    {
        String text = "/** Calls `void go(int` - see below. } */\n"
            + "    void go(int speed)\n"
            + "    {\n"
            + "        String s = \"}\"; // a } here\n"
            + "        char c = '{';\n"
            + "        /* { */ run(s, c);\n"
            + "    }\n"
            + "    void stop() {}\n";

        String declaration = SourceOf.declarationIn(text, "void go(int");

        assertTrue(declaration.startsWith("void go(int speed)"), declaration);
        assertTrue(declaration.endsWith("run(s, c);\n    }"), "the cut ended in the wrong place: " + declaration);
        assertFalse(declaration.contains("stop"), declaration);
    }

    /**
     * Line endings are not part of a rule: a declaration written with CRLF is found by a signature written with `\n`,
     * and a field ends at its `;`.
     */
    @Test
    public void testLineEndingsAndFieldsAreCutAlike()
    {
        String text = "    private int x = f(1,\r\n        2);\r\n    void g()\r\n    {\r\n    }\r\n";

        assertEquals(SourceOf.declarationIn(text, "private int x"), "private int x = f(1,\n        2);");
        assertEquals(SourceOf.declarationIn(text, "void g()\n"), "void g()\n    {\n    }");
    }

    /**
     * Comments are blanked offset for offset: the line breaks, the strings and the length stay.
     */
    @Test
    public void testWithoutCommentsKeepsEveryOffset()
    {
        String text = "a(); // gone\n/* gone\n too */ b(\"// kept\", '/');\n";

        String code = SourceOf.withoutComments(text);

        assertEquals(code.length(), text.length());
        assertEquals(code.split("\n", -1).length, text.split("\n", -1).length);
        assertFalse(code.contains("gone") || code.contains("too"), code);
        assertTrue(code.contains("b(\"// kept\", '/');"), code);
    }

    /**
     * The whole program, with and without its comments, for negative claims: the same length, the comments gone, and
     * a counted call counted in the code only.
     */
    @Test
    public void testTheWholeProgramIsReadAndCounted()
    {
        assertEquals(SourceOf.allCode().length(), SourceOf.all().length());
        assertTrue(SourceOf.all().contains("/**") && !SourceOf.allCode().contains("/**"),
            "the comments were not blanked across the program");
        assertTrue(SourceOf.allCode().contains(ONE_LINE_RULE));
        assertTrue(SourceOf.occurrences("fitsOnOneLine(") >= 2, "the rule and a caller of it were not both counted");
        assertEquals(SourceOf.occurrences("private void noSuchDeclarationAnywhere("), 0);
    }

    private static AssertionError expectFailure(Runnable claim)
    {
        try
        {
            claim.run();
        }
        catch (AssertionError expected)
        {
            return expected;
        }

        fail("no failure");

        return null;
    }
}
