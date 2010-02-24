package jw.supertunnel.server;

public class ResponseException extends RuntimeException
{
    public int code;
    
    public ResponseException(int code)
    {
        super("Code: " + code);
        this.code = code;
    }
}
