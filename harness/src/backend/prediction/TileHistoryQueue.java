package backend.prediction;

import java.util.ArrayList;
import java.util.List;

import utils.UserRequest;

import backend.util.NiceTile;
import backend.util.TileKey;

/**
 * @author leibatt
 * Class for keeping track of the client request history
 */
public class TileHistoryQueue {
	private ArrayList<TileRecord> history;
	private ArrayList<TileRecord> trueHistory; // used to track ROI's
	protected List<TileKey> lastRoi;
	protected int lastZoomOut = -1;
	protected boolean newRoi = false;
	private int maxhist;
	
	public TileHistoryQueue(int maxhist) {
		history = new ArrayList<TileRecord>();
		trueHistory = new ArrayList<TileRecord>();
		lastRoi = new ArrayList<TileKey>();
		this.maxhist = maxhist;
	}
	
	public synchronized void clear() {
		history.clear();
		trueHistory.clear();
		lastRoi.clear();
	}
	
	// adds record to history for given tile
	// copies entire tile
	// maintains history of length maxhist
	public synchronized void addRecord(NiceTile Next) {
		history.add(new TileRecord(Next));
		trueHistory.add(new TileRecord(Next));
		updateROI();
		if(history.size() > maxhist) {
			history.remove(0);
		}
	}
	
	// returns a clone of the record at given index
	// will throw exception if index is outside bounds
	public synchronized final NiceTile getRecordTile(int index) {
		return history.get(index).MyTile;
	}
	
	// returns a clone of the tile id of the record at given index
	// will throw exception if index is outside bounds
	public synchronized final TileKey getRecordTileKey(int index) {
		return history.get(index).MyTile.id;
	}
	
	// returns the timestamp of the record at the given index
	public synchronized final long getRecordTimestamp(int index) {
		return history.get(index).timestamp;
	}
	
	// returns the length of the history
	public synchronized int getHistoryLength() {
		return history.size();
	}
	
	// returns history as user requests for directional models
	public synchronized List<UserRequest> getHistoryTrace() {
		List<UserRequest> myresult = new ArrayList<UserRequest>();
		for(int i = 0; i < history.size(); i++) {
			TileRecord tr = history.get(i);
			TileKey tk = tr.MyTile.id;
			UserRequest temp = new UserRequest(tk.buildTileString(),tk.zoom);
			myresult.add(temp);
		}
		return myresult;
	}
	
	public synchronized TileKey getLast() {
		if(history.size() == 0) return null;
		return history.get(history.size() - 1).MyTile.id;
	}
	
	// returns last k elements in history as user requests for directional models
	public synchronized List<TileKey> getHistoryTrace(int length) {
		List<TileKey> myresult = new ArrayList<TileKey>();
		int start = history.size() - length;
		if(start < 0) {
			start = 0;
		}
		for(int i = start; i < history.size(); i++) {
			TileRecord tr = history.get(i);
			myresult.add(tr.MyTile.id);
		}
		return myresult;
	}
	
	public synchronized List<TileKey> getLastRoi() {
		if(lastRoi.size() > 0) {
			return lastRoi;
		}
		
		// just return the last request, if there is no ROI yet
		List<TileKey> makeshiftRoi = new ArrayList<TileKey>();
		if(history.size() == 0) return makeshiftRoi;
		makeshiftRoi.add(history.get(history.size()-1).MyTile.id);
		return makeshiftRoi;
	}
	
	public synchronized boolean newRoi() {
		return newRoi;
	}
	
	// find the user's last region of interest!
	protected synchronized void updateROI() {
		int lastZoomOut = -1;
		int lastZoomIn = -1;
		int i = trueHistory.size() - 2;
		for(;i > this.lastZoomOut; i--) {
			TileKey lastKey = trueHistory.get(i+1).MyTile.id;
			TileKey nextLastKey = trueHistory.get(i).MyTile.id;
			if(lastKey.zoom < nextLastKey.zoom) { // found zoom out
					lastZoomOut = i;
					break;
			}
		}
		for(;i > this.lastZoomOut; i--) {
			TileKey lastKey = trueHistory.get(i+1).MyTile.id;
			TileKey nextLastKey = trueHistory.get(i).MyTile.id;
			if(lastKey.zoom > nextLastKey.zoom) { // found zoom in, new ROI!
				lastZoomIn = i;
				break;
			} else if (lastKey.zoom < nextLastKey.zoom) { // found another zoom out, abort!
				lastZoomOut = -1;
				break;
			}
		}
		
		if(lastZoomOut >= 0 && lastZoomIn >= 0) {
			this.lastZoomOut = lastZoomOut;
			lastRoi.clear();
			for(i = lastZoomIn; i <= lastZoomOut; i++) {
				lastRoi.add(history.get(i).MyTile.id);
			}
			newRoi = true;
		} else {
			newRoi = false;
		}
	}
	
	@Override
	public synchronized String toString() {
		StringBuilder myresult = new StringBuilder();
		myresult.append("{").append("\n");
		for(int i = 0; i < history.size(); i++) {
			TileRecord record = history.get(i);
			myresult.append("\t");
			myresult.append(record);
			myresult.append("\n");
		}
		myresult.append("}");
		return myresult.toString();
	}

	private class TileRecord {
		public long timestamp;
		public NiceTile MyTile;
		
		public TileRecord(NiceTile Next) {
			this.timestamp = System.currentTimeMillis() / 1000;
			this.MyTile = Next;
		}
		
		@Override
		public String toString() {
			StringBuilder myresult = new StringBuilder();
			myresult.append("{");
			myresult.append(this.timestamp);
			myresult.append(":");
			myresult.append(this.MyTile);
			myresult.append("}");
			return myresult.toString();
		}
	}
}
