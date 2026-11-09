package support;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A declared member of the program, whatever its visibility, for a test to read, write or call.  Each throws exactly
 * what the `getDeclaredField` / `getDeclaredMethod` and `setAccessible(true)` it stands for throw, so a caller's catch
 * keeps its meaning.
 */
public final class Reflect
{
    private Reflect()
    {
    }

    /** The field `owner` declares, made accessible. */
    public static Field field(Class<?> owner, String name) throws NoSuchFieldException
    {
        Field field = owner.getDeclaredField(name);

        field.setAccessible(true);

        return field;
    }

    /** The value of the field `owner` declares, on `target` (null for a static field). */
    public static Object get(Class<?> owner, Object target, String name)
        throws NoSuchFieldException, IllegalAccessException
    {
        return field(owner, name).get(target);
    }

    /** Sets the field `owner` declares, on `target` (null for a static field). */
    public static void set(Class<?> owner, Object target, String name, Object value)
        throws NoSuchFieldException, IllegalAccessException
    {
        field(owner, name).set(target, value);
    }

    /** The method `owner` declares with these parameter types, made accessible. */
    public static Method method(Class<?> owner, String name, Class<?>... parameters) throws NoSuchMethodException
    {
        Method method = owner.getDeclaredMethod(name, parameters);

        method.setAccessible(true);

        return method;
    }
}
