package regression;

import java.util.Collections;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.TileAnnotation;

/**
 * A square whose only annotation is "a train is standing here" is not a blank square (OB-007: "add a small white * on
 * top of the station icons to show there is a train there").  `paint()` opens with `if (isBlank()) return;` and
 * `isBlank()` lists every field that counts as content except `occupied`, so an annotation carrying nothing but a train
 * was blank and the star was never painted.  The field was added to equals and hashCode and missed here; it only bit
 * where the train was the ONLY thing to say about a square.
 *
 * @author Adam
 */
public class testTrainMarkIsNotBlank
{
    /** The whole bug. */
    @Test
    public void testATrainIsWorthDrawing()
    {
        TileAnnotation nothing = plain();

        assertTrue(nothing.isBlank(),
            "an annotation with nothing on it should be blank - if this fails the test below proves "
            + "nothing, because everything is unblank");

        assertFalse(plain().withTrain().isBlank(),
            "a square with a train on it and nothing else was treated as blank, so paint() returned "
            + "before it could draw the train mark");
    }

    /**
     * And withTrain is what the editor actually calls: it returns `this`, so a call site that discards the return value
     * keeps working.
     */
    @Test
    public void testWithTrainMarksTheAnnotationItWasCalledOn()
    {
        TileAnnotation annotation = plain();

        TileAnnotation returned = annotation.withTrain();

        assertSame(returned, annotation,
            "withTrain returns a copy now, so the editor - which discards the return value - has "
            + "stopped marking trains at all");

        assertFalse(annotation.isBlank(), "the annotation it was called on was not marked");
    }

    /**
     * A train and a badge together are still not blank, and neither is a train on an ignored square: `ignored` returns
     * early in paint() before the train mark, deliberately, but the annotation must not be called blank or the graying
     * is lost.
     */
    @Test
    public void testTheOtherCombinationsAreStillDrawn()
    {
        assertFalse(plain().withTrain().isBlank(), "a train");

        TileAnnotation ignored = new TileAnnotation(
            Collections.<TileAnnotation.Mark>emptyList(), -1, false, null, true);

        assertFalse(ignored.isBlank(), "an ignored square is drawn - it is greyed and hatched");

        assertFalse(ignored.withTrain().isBlank(), "an ignored square with a train is still drawn");
    }

    /** An annotation with nothing on it at all. */
    private TileAnnotation plain()
    {
        return new TileAnnotation(Collections.<TileAnnotation.Mark>emptyList(), -1, false);
    }
}
