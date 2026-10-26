package support;

import java.awt.Color;

/**
 * The static constants of the program, asked for by name, whatever their visibility.
 *
 * A test that restates a number or a color of the program holds its own copy of a rule: change the program and the test
 * goes red looking like a regression, or stays green for the wrong reason.  This reads the program's own constant
 * instead, through reflection on the declaration (not the number the compiler copied into the callers), so a constant
 * renamed or moved fails here, loudly, naming the class and the field.
 */
public final class ProgramConstants
{
    private ProgramConstants()
    {
    }

    /**
     * The static field's value, whatever its visibility.
     */
    public static Object get(Class<?> owner, String name)
    {
        try
        {
            java.lang.reflect.Field field = owner.getDeclaredField(name);

            field.setAccessible(true);

            return field.get(null);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(owner.getName() + "." + name + " is not there to be asked: " + e, e);
        }
    }

    /**
     * Its value as a whole number.
     */
    public static int integer(Class<?> owner, String name)
    {
        return ((Number) get(owner, name)).intValue();
    }

    /**
     * Its value as a decimal number.
     */
    public static double real(Class<?> owner, String name)
    {
        return ((Number) get(owner, name)).doubleValue();
    }

    /**
     * Its value as a color.
     */
    public static Color colour(Class<?> owner, String name)
    {
        return (Color) get(owner, name);
    }
}
