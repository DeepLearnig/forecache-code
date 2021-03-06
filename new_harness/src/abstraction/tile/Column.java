package abstraction.tile;

import java.nio.charset.Charset;
import java.util.List;
import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class Column implements java.io.Serializable {
	private static final long serialVersionUID = -5580879891349835855L;
	public static int doubleSize = 8;
	public static Charset defaultStringEncoding = UTF_8;
	public abstract void add(String string);
	public abstract Object get(int i);
	public abstract Class<?> getColumnType();
	public abstract List<?> getDomain();
	public abstract int getSize();
	public abstract List<?> getValues();
	public abstract boolean isNumeric();
	public abstract byte[] getBytes(); // encodes column as bytes
	public abstract int readBytes(byte[] data, int offset); // decodes column as bytes
}
