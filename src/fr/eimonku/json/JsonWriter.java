package fr.eimonku.json;

import static java.lang.Character.isISOControl;
import static java.lang.Integer.toHexString;
import static java.util.Arrays.asList;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class JsonWriter {
	private final Writer w;
	private final int indentLevel;

	public JsonWriter(Writer w) {
		this(w, -1);
	}

	public JsonWriter(Writer w, int indentLevel) {
		this.w = w;
		this.indentLevel = indentLevel;
	}

	public JsonWriter appendObject(final Object object) throws IOException {
		if (object == null) {
			return appendNull();
		} else if (object instanceof Number) {
			return appendNumber((Number) object);
		} else if (object instanceof Boolean) {
			return appendBoolean((Boolean) object);
		} else if (object instanceof Map) {
			return appendMap((Map<?, ?>) object);
		} else if (object instanceof Iterable) {
			return appendList((Iterable<?>) object);
		} else if (object instanceof Object[]) {
			return appendList(asList((Object[]) object));
		} else if (object.getClass().isArray()) {
			return appendList(new AbstractList<Object>() {
				final int size = Array.getLength(object);

				@Override
				public Object get(int index) {
					return Array.get(object, index);
				}

				@Override
				public int size() {
					return size;
				}
			});
		} else {
			return appendString(object.toString());
		}
	}

	public JsonWriter appendString(String string) throws IOException {
		append('"');

		for (int i = 0; i < string.length(); ++i) {
			final char c = string.charAt(i);
			switch (c) {
			case '"':
			case '\\':
				append('\\').append(c);
				break;
			case '\n':
				append("\\n");
				break;
			case '\r':
				append("\\r");
				break;
			case '\t':
				append("\\t");
				break;
			case '\b':
				append("\\b");
				break;
			case '\f':
				append("\\f");
				break;
			default:
				if (isISOControl(c)) {
					append("\\u").append(toHexString(c | 0x10000), 1, 5);
				} else {
					append(c);
				}
			}
		}

		return append('"');
	}

	public JsonWriter appendNumber(Number n) throws IOException {
		final double d = n.doubleValue();
		if ((d % 1) == 0 && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
			return append(Long.toString(n.longValue()));
		} else {
			return append(n.toString());
		}
	}

	public JsonWriter appendMap(Map<?, ?> map) throws IOException {
		return appendMap(map.entrySet().iterator());
	}

	public JsonWriter appendMap(Iterator<? extends Entry<?, ?>> iterator) throws IOException {
		if (!iterator.hasNext()) {
			return append("{}");
		}

		final JsonWriter indentedWriter = indentedWriter();
		char separator = '{';
		while (iterator.hasNext()) {
			final Entry<?, ?> entry = iterator.next();
			indentedWriter.append(separator).appendLfAndIndent().appendString(entry.getKey().toString()).append(':');
			if (indentLevel >= 0) {
				append(' ');
			}
			indentedWriter.appendObject(entry.getValue());
			separator = ',';
		}
		return appendLfAndIndent().append('}');
	}

	public JsonWriter appendList(Iterable<?> iterable) throws IOException {
		return appendList(iterable.iterator());
	}

	public JsonWriter appendList(Iterator<?> iterator) throws IOException {
		if (!iterator.hasNext()) {
			return append("[]");
		}

		final JsonWriter indentedWriter = indentedWriter();
		char separator = '[';
		while (iterator.hasNext()) {
			final Object element = iterator.next();
			indentedWriter.append(separator).appendLfAndIndent().appendObject(element);
			separator = ',';
		}
		return appendLfAndIndent().append(']');
	}

	public JsonWriter appendBoolean(boolean bool) throws IOException {
		return append(bool ? "true" : "false");
	}

	public JsonWriter appendNull() throws IOException {
		return append("null");
	}

	private JsonWriter indentedWriter() {
		return indentLevel >= 0 ? new JsonWriter(w, indentLevel + 1) : this;
	}

	private static final String INDENT = "  ";

	private JsonWriter appendLfAndIndent() throws IOException {
		if (indentLevel >= 0) {
			append('\n');

			for (int i = 0; i < indentLevel; ++i) {
				append(INDENT);
			}
		}

		return this;
	}

	private JsonWriter append(CharSequence csq) throws IOException {
		w.append(csq);
		return this;
	}

	private JsonWriter append(CharSequence csq, int start, int end) throws IOException {
		w.append(csq, start, end);
		return this;
	}

	private JsonWriter append(char c) throws IOException {
		w.append(c);
		return this;
	}
}
