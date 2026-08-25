package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.marklin.file.CS2File;

/**
 * A CS3 that answers "Not Found" in the BODY is recognised as not having it.
 *
 * TA-B6, from the test suite audit inside [OB-089]. `isNotFoundError` has two branches and the suite
 * only ever exercised one: the mock Central Station returns real HTTP 404s, which land in the
 * `FileNotFoundException` catch. The audit demonstrated the gap by replacing the whole method with
 * `return false` and watching everything stay green.
 *
 * The untested branch is the one that matters on real hardware. A CS3 running firmware before 2.6.0
 * answers an unknown endpoint with **HTTP 200 and a JSON object** `{"error": "Not Found"}` rather than
 * a 404, which is exactly why the method reads the body at all instead of trusting the status. So the
 * half that was covered is the half that only newer firmware produces, and the half that decides
 * whether TrainControl talks to Adam's own station correctly was never run.
 *
 * **No model, no layout, no network.** `fetchURL` reads any URL the JVM understands, including `file:`
 * - which the application itself relies on when a local layout override is set. So the responses can
 * be written to disk and pointed at, and this test needs nothing running.
 */
public class testCS3NotFoundDetection
{
    private static CS2File file;
    private static File folder;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // No control station attached: this method never touches one, and several existing tests
        // build CS2File the same way.
        file = new CS2File("file:///", null);

        folder = File.createTempFile("tc-cs3", "");

        assertTrue(folder.delete(), "making room for a directory of the same name");
        assertTrue(folder.mkdirs(), "could not make the working directory");

        folder.deleteOnExit();
    }

    /**
     * The pre-2.6.0 answer: a 200, with the refusal in the body.
     *
     * This is the assertion the audit showed was missing. With it, `return false` no longer passes.
     */
    @Test
    public void testAnErrorObjectInTheBodyCountsAsNotFound() throws Exception
    {
        assertTrue(file.isNotFoundError(urlOf("error.json", "{\"error\": \"Not Found\"}")),
            "a CS3 answering 200 with {\"error\": \"Not Found\"} was read as HAVING the endpoint.  "
            + "That is what firmware before 2.6.0 sends instead of a 404, and reading the body rather "
            + "than the status is the whole reason this method exists (TA-B6)");
    }

    /**
     * And the same thing however the station capitalises it.
     *
     * The comparison is deliberately case-insensitive in the code; that is worth holding, because a
     * firmware that says "not found" would otherwise be read as success.
     */
    @Test
    public void testTheErrorTextIsMatchedWhateverItsCase() throws Exception
    {
        assertTrue(file.isNotFoundError(urlOf("lower.json", "{\"error\": \"not found\"}")),
            "the same refusal in lower case was not recognised");
    }

    /**
     * A real answer is not a refusal - which is the half that decides anything is fetched at all.
     */
    @Test
    public void testAnArrayIsNeverNotFound() throws Exception
    {
        assertFalse(file.isNotFoundError(urlOf("locos.json", "[{\"name\": \"BR 89\"}]")),
            "a CS3 returning a list of locomotives was read as not having the endpoint, so nothing "
            + "would be fetched from a station that answered perfectly well");
    }

    /**
     * An object that is not an error is not a refusal either.
     *
     * Worth its own case: the branch returns on `error` being absent, and "any object means trouble"
     * is an easy way to write this wrongly.
     */
    @Test
    public void testAnOrdinaryObjectIsNotAnError() throws Exception
    {
        assertFalse(file.isNotFoundError(urlOf("ok.json", "{\"session\": {\"id\": 3}}")),
            "an ordinary JSON object was treated as a refusal");
    }

    /**
     * And a genuine 404, which is what newer firmware sends - the branch that was already covered.
     *
     * Kept so that the two answers stay tested together: a change that fixed one by breaking the
     * other would otherwise look like a pass.
     */
    @Test
    public void testAMissingResourceIsNotFound() throws Exception
    {
        File missing = new File(folder, "there-is-no-such-file.json");

        assertFalse(missing.exists(), "the fixture must not exist for this to mean anything");

        assertTrue(file.isNotFoundError(missing.toURI().toString()),
            "a resource that is not there was not read as not found");
    }

    private static String urlOf(String name, String body) throws Exception
    {
        File written = new File(folder, name);

        Files.write(written.toPath(), body.getBytes(StandardCharsets.UTF_8));

        written.deleteOnExit();

        return written.toURI().toString();
    }
}
