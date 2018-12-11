package util;

/**
 * Class enabling "anonymous functions" with a parameter
 * @author Adam
 * @param <T> 
 */
public class Exec<T> extends Thread
{
    protected T data;
    
    public Exec(T data)
    {
       this.data = data;
    }
    
    @Override
    public void run(){}
 }