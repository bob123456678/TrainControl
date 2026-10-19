package support;

import java.awt.Color;

/**
 * The static constants of the program, asked for by name, whatever their visibility.
 *
 * A test that restates a number or a colour of the program holds its own copy of a rule: change the
 * program and the test goes red looking like a regression, or stays green for the wrong reason.  This reads
 * the program's own constant instead.  A constant that has been renamed or moved fails here, loudly and
 * naming the class and the field, rather than being swallowed.
 *
 * Reflection reads the field, not the number the compiler copied into the callers, so it is the declaration
 * that is asked.
 */
public final class ProgramConstants
{
    private ProgramConstants()
    {
    }

    /**
     * @param owner the class that declares the static field
     * @param name the field's name
     * @return its value
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
     * @param owner the class that declares the static field
     * @param name the field's name
     * @return its value as a whole number
     */
    public static int integer(Class<?> owner, String name)
    {
        return ((Number) get(owner, name)).intValue();
    }

    /**
     * @param owner the class that declares the static field
     * @param name the field's name
     * @return its value as a decimal number
     */
    public static double real(Class<?> owner, String name)
    {
        return ((Number) get(owner, name)).doubleValue();
    }

    /**
     * @param owner the class that declares the static field
     * @param name the field's name
     * @return its value as a colour
     */
    public static Color colour(Class<?> owner, String name)
    {
        return (Color) get(owner, name);
    }
}
