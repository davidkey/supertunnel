package jw.supertunnel.server;

public class ResponseException extends RuntimeException
{
    public int code;
    public ResponseException(int code)
    {
        this.code = code;
    }
}
