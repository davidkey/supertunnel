package jw.supertunnel.server;

public class ResponseException extends RuntimeException {
	private static final long serialVersionUID = -2401737391831632010L;

	public int code;

	public ResponseException(int code) {
		super("Code: " + code);
		this.code = code;
	}
}
