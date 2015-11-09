package abstraction.tile;

import java.util.ArrayList;
import java.util.List;

public class LongColumn extends Column {
	public List<Long> columnVals;
	public Domain<Long> domain;
	
	public LongColumn() {
		this.columnVals = new ArrayList<Long>();
		this.domain = new Domain<Long>();
	}
	
	@Override
	public List<Long> getValues() {
		return this.columnVals;
	}
	
	@Override
	public boolean isNumeric() {
		return true;
	}
	
	@Override
	public int getSize() {
		return this.columnVals.size();
	}
	
	@Override
	public List<Long> getDomain() {
		return this.domain.getDomain();
		
	}
	
	public void add(Long item) {
		this.columnVals.add(item);
		this.domain.update(item);
	}
	
	public void add(String value) {
		Long item = Long.parseLong(value);
		add(item);
	}
	
	@Override
	public Object get(int i) {
		return this.columnVals.get(i);
	}
	
	public Long getTyped(int i) {
		return this.columnVals.get(i);
	}
	
	@Override
	public Class<Long> getColumnType() {
		return Long.class;
	}
}