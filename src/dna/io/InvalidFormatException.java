package dna.io;

import java.io.IOException;

public class InvalidFormatException extends IOException {
	private static final long serialVersionUID = 1L;

	public InvalidFormatException(String message) {
		super(message);
	}
}
