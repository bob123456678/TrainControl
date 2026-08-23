package regression;

import java.util.Collections;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.TileAnnotation;

/**
 * A square whose only annotation is "a train is standing here" is not a blank square.
 *
 * OB-007, 2026-08-22: "add a small white * on top of the station icons to show there is a train
 * there". The mark already existed - paintTrainMark draws exactly that, a small white six-armed star
 * with a dark edge under it - and had existed since it was written. It was invisible on precisely the
 * squares OB-007 is about.
 *
 * paint() opens with `if (isBlank()) return;`, and isBlank() lists every field that counts as content:
 * marks, length, selected, badge, ignored, traces, arrivals. It does not list `occupied`. So an
 * annotation carrying nothing but a train was blank, and the star was never painted.
 *
 * The field was added to equals and to hashCode - both name it - and missed here. That is the shape
 * worth remembering: a new field gets added to the methods somebody is thinking about at the time, and
 * the one that decides whether the object is worth drawing at all is not usually one of them.
 *
 * Which squares? The ones with nothing else on them. A station with a badge was never blank, so the
 * star appeared there and the bug looked like it did not exist - it only bit where the train was the
 * ONLY thing to say about a square, which is exactly the case the request was filed about.
 *
 * @author Adam
 */
public class testTrainMarkIsNotBlank
{
    /**
     * The whole bug.
     */
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
     * And withTrain is what the editor actually calls, on the annotation it actually returns.
     *
     * withTrain returns `this` rather than a copy, which is what lets the editor write
     * `if (locomotiveAt(tile) != null) annotation.withTrain();` and keep using `annotation`. If it
     * ever starts returning a copy, that call site silently stops marking anything - the return value
     * is discarded there.
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
     * A train and a badge together are still not blank, and neither is a train on an ignored square.
     *
     * The second one is not academic: `ignored` returns early in paint() before the train mark, which
     * is deliberate - an ignored square is one autonomy takes no notice of, so there is no train of
     * its to draw. What must not happen is the annotation being called blank, because then the greying
     * that says "ignored" would not be drawn either.
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

    /**
     * An annotation with nothing on it at all.
     */
    private TileAnnotation plain()
    {
        return new TileAnnotation(Collections.<TileAnnotation.Mark>emptyList(), -1, false);
    }
}
