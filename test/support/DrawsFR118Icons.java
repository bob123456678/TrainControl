package support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A claim about FR-118's station icons - the blocks and hexagons, their spill onto the squares beside a bend, white in the
 * autonomy editor - which stations are no longer drawn with (round 131; Adam, 2026-10-11: *"Keep the work on the new
 * icons so we can revisit later, and revert roughly to what we had before"*).  The class's `@BeforeMethod` draws a claim
 * marked with this with them (`TileAnnotation.StationIcons.SHAPES`), and every other claim with the classic icons, so
 * the kept work stays tested for when it is looked at again.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DrawsFR118Icons
{
}
