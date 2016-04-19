package fr.eimonku.json;

import static java.lang.Double.parseDouble;
import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static org.apache.logging.log4j.LogManager.getLogger;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;

public class JsonReader {
	private static final Logger logger = getLogger();

	private final Reader r;

	private int peekedChar = -1;
	private boolean lastCharCr = false;
	private int currentLine = 1, currentCol = 0;

	public JsonReader(Reader r) {
		this.r = r;
	}

	public Object readObject() throws IOException, InvalidJsonException {
		final char c = peekFirstTokenChar();
		switch (c) {
		case '"':
			return readString();
		case '{':
			return readMap();
		case '[':
			return readList();
		case 't':
		case 'f':
			return readBoolean();
		case 'n':
			return readNull();
		default:
			if (isNumberChar(c)) {
				return readNumber();
			} else {
				read();
				throw new InvalidJsonException(format("unexpected character \\u%04x", (int) c), this);
			}
		}
	}

	public String readString() throws IOException, InvalidJsonException {
		readToken("\"");

		final StringBuilder sb = new StringBuilder();

		char c;
		while ((c = read()) != '"') {
			sb.append(c != '\\' ? c : readEscapeSequence());
		}

		return sb.toString();
	}

	public Number readNumber() throws IOException, InvalidJsonException {
		final StringBuilder sb = new StringBuilder();
		while (isNumberChar(peekNoThrow())) {
			sb.append(read());
		}

		final String s = sb.toString();
		try {
			return parseLong(s);
		} catch (NumberFormatException e) {
		}
		final double d;
		try {
			d = parseDouble(s);
		} catch (NumberFormatException e) {
			throw new InvalidJsonException("invalid number", this);
		}

		if ((d % 1) == 0 && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
			return (long) d;
		} else {
			return d;
		}
	}

	public Map<String, Object> readMap() throws IOException, InvalidJsonException {
		final Map<String, Object> map = new LinkedHashMap<>();
		readMap(new JsonMapListener() {
			@Override
			public void newEntry(String key, Object value) {
				if (map.put(key, value) != null) {
					logger.warn("JSON: overriding value for duplicate key '{}'", key);
				}
			}
		});
		return map;
	}

	public void readMap(JsonMapListener listener) throws IOException, InvalidJsonException {
		readToken("{");

		String separator = "";
		while (peekFirstTokenChar() != '}') {
			readToken(separator);
			final String key = readString();
			readToken(":");
			listener.newEntry(key, readObject());
			separator = ",";
		}

		readToken("}");
	}

	public List<Object> readList() throws IOException, InvalidJsonException {
		final List<Object> list = new ArrayList<>();
		readList(new JsonListListener() {
			@Override
			public void newElement(Object element) {
				list.add(element);
			}
		});
		return list;
	}

	public void readList(JsonListListener listener) throws IOException, InvalidJsonException {
		readToken("[");

		String separator = "";
		while (peekFirstTokenChar() != ']') {
			readToken(separator);
			listener.newElement(readObject());
			separator = ",";
		}

		readToken("]");
	}

	public boolean readBoolean() throws IOException, InvalidJsonException {
		final boolean bool = (peekFirstTokenChar() == 't');
		readToken(bool ? "true" : "false");
		return bool;
	}

	public Object readNull() throws IOException, InvalidJsonException {
		readToken("null");
		return null;
	}

	private static boolean isNumberChar(int c) {
		return c == '-' || c == '+' || c == '.' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z')
		    || (c >= 'A' && c <= 'Z');
	}

	private char readEscapeSequence() throws IOException, InvalidJsonException {
		final char c = read();
		switch (c) {
		case '"':
		case '\\':
		case '/':
			return c;
		case 'b':
			return '\b';
		case 'f':
			return '\f';
		case 'n':
			return '\n';
		case 'r':
			return '\r';
		case 't':
			return '\t';
		case 'u':
			return readCodePoint();
		default:
			throw new InvalidJsonException(format("invalid escape sequence character \\u%04x", (int) c), this);
		}
	}

	private char readCodePoint() throws IOException, InvalidJsonException {
		char c = 0;
		for (int i = 0; i < 4; ++i) {
			c |= (readHexDigit() << (4 * (3 - i)));
		}
		return c;
	}

	private char readHexDigit() throws IOException, InvalidJsonException {
		final char c = read();
		if (c >= '0' && c <= '9') {
			return (char) (c - '0');
		} else if (c >= 'a' && c <= 'f') {
			return (char) (c - 'a' + 10);
		} else if (c >= 'A' && c <= 'F') {
			return (char) (c - 'A' + 10);
		}

		throw new InvalidJsonException(format("invalid unicode escape sequence character \\u%04x", (int) c), this);
	}

	private void readToken(String expectedString) throws IOException, InvalidJsonException {
		skipWhitespaces();

		for (int i = 0; i < expectedString.length(); ++i) {
			final char c = read(), expectedChar = expectedString.charAt(i);
			if (c != expectedChar) {
				throw new InvalidJsonException(format("expected '%c', got \\u%04x", expectedChar, (int) c), this);
			}
		}
	}

	private char peekFirstTokenChar() throws IOException, InvalidJsonException {
		skipWhitespaces();

		return peek();
	}

	private void skipWhitespaces() throws IOException, InvalidJsonException {
		while (isWhitespace(peek())) {
			read();
		}
	}

	private static boolean isWhitespace(char c) {
		return c == 0x9 || c == 0xa || c == 0xd || c == 0x20;
	}

	private char read() throws IOException, InvalidJsonException {
		final char c;
		if (peekedChar >= 0) {
			c = (char) peekedChar;
			peekedChar = -1;
		} else {
			c = forceRead();
		}

		final boolean cr = (c == '\r');
		if (cr || c == '\n') {
			if (cr || !lastCharCr) {
				++currentLine;
				currentCol = 0;
			}
			lastCharCr = cr;
		} else {
			++currentCol;
		}

		return c;
	}

	private char peek() throws IOException, InvalidJsonException {
		if (peekedChar < 0) {
			peekedChar = forceRead();
		}

		return (char) peekedChar;
	}

	private int peekNoThrow() throws IOException {
		if (peekedChar < 0) {
			peekedChar = r.read();
		}

		return peekedChar;
	}

	private char forceRead() throws IOException, InvalidJsonException {
		final int c = r.read();
		if (c < 0) {
			++currentCol;
			throw new InvalidJsonException("unexpected end of input", this);
		}
		return (char) c;
	}

	@SuppressWarnings("serial")
	public static class InvalidJsonException extends Exception {
		private InvalidJsonException(String message, JsonReader r) {
			super(format("%s at line %s, col %s", message, r.currentLine, r.currentCol));
		}
	}

	public static interface JsonMapListener {
		void newEntry(String key, Object value);
	}

	public static interface JsonListListener {
		void newElement(Object element);
	}
}
