package backend.memory;

//import java.util.ArrayList;
import java.util.HashMap;
//import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import backend.util.NiceTileBuffer;
import backend.util.NiceTile;
import backend.util.TileKey;
import backend.util.TimePair;

/**
 * @author leibatt
 * Class for managing the in-memory tile cache.
 */
public class MemoryNiceTileBuffer implements NiceTileBuffer {
	private Map<TileKey,NiceTile> storage; // for storing tiles
	private Map<TileKey,TimePair> timeMap; // for finding things in the queue
	private PriorityQueue<TimePair> lruQueue; // for identifying lru tiles in storage
	private int storagemax;
	//private int size;
	private final int DEFAULTMAX = 1; // default buffer size
	private final int initqueuesize = 50;
	
	public MemoryNiceTileBuffer() {
		// initialize storage
		this.storage = new HashMap<TileKey,NiceTile>();
		this.lruQueue = new PriorityQueue<TimePair>(this.initqueuesize,new TimePair.TPSort());
		timeMap = new HashMap<TileKey,TimePair>();
		//this.size = 0;
		this.storagemax = this.DEFAULTMAX;
	}
	
	public MemoryNiceTileBuffer(int storagemax) {
		// initialize storage
		this.storage = new HashMap<TileKey,NiceTile>();
		this.lruQueue = new PriorityQueue<TimePair>(this.initqueuesize,new TimePair.TPSort());
		timeMap = new HashMap<TileKey,TimePair>();
		//this.size = 0;
		this.storagemax = storagemax;
	}
	
	public synchronized void setStorageMax(int newmax) {
		this.clear();
		this.storagemax = newmax;
	}
	
	public synchronized int getStorageMax() {
		return storagemax;
	}
	
	public synchronized int freeSpace() {
		return storagemax - this.storage.size();
	}

	@Override
	public synchronized boolean peek(TileKey id) {
		return this.storage.containsKey(id);
	}

	@Override
	public synchronized NiceTile getTile(TileKey id) {
		return this.storage.get(id);
	}

	@Override
	public synchronized Set<TileKey> getAllTileKeys() {
		return this.storage.keySet();
	}
	
	@Override
	public synchronized int tileCount() {
		return this.storage.size();
	}
	
	@Override
	public synchronized void clear() {
		lruQueue.clear();
		timeMap.clear();
		this.storage.clear();
	}

	
	@Override
	public synchronized void insertTile(NiceTile tile) {
		if(this.storagemax == 0) return;
		if(!this.storage.containsKey(tile.id)) {
			//int tilesize = tile.getDataSize();
			// make room for new tile in storage
			//while((this.size + tilesize) > this.storagemax) {
			while(this.storage.size() >= this.storagemax) {
				this.remove_lru_tile();
			}
			// insert new tile into storage
			this.insert_tile(tile);
		} else { // tile already exists
			// update metadata
			this.update_time_pair(tile.id);
		}
	}

	@Override
	public synchronized void removeTile(TileKey id) {
		this.remove_tile(id);
	}
	
	@Override
	public synchronized void touchTile(TileKey id) {
		if(peek(id)) {
			// update metadata
			this.update_time_pair(id);
		}
	}
	
	// updates eviction metadata for existing tile id
	protected synchronized void update_time_pair(TileKey id) {
		TimePair tp;
		if(timeMap.containsKey(id)) {
			tp = timeMap.get(id);
			lruQueue.remove(tp);
			tp.updateTimestamp();
			lruQueue.add(tp);
		}
	}
	
	// adds new eviciton metadata for given tile id
	protected synchronized void insert_time_pair(TileKey id) {
		TimePair tp;
		if(!timeMap.containsKey(id)) {
			tp = new TimePair(id);
			lruQueue.add(tp);
			timeMap.put(id, tp);
		}
	}
	
	// removes eviction metadata for existing tile id
	protected synchronized void remove_time_pair(TileKey id) {
		TimePair tp;
		if(timeMap.containsKey(id)) {
			tp = timeMap.get(id);
			lruQueue.remove(tp);
			timeMap.remove(id);
		}
	}
	
	// inserts a specific tile into buffer
	protected synchronized void insert_tile(NiceTile tile) {
		//int tilesize = tile.getDataSize();
		this.storage.put(tile.id, tile);
		// add metadata for eviction purposes
		this.insert_time_pair(tile.id);
		//this.size += tilesize;
	}
	
	// checks priority queue and removes lru tile
	protected synchronized void remove_lru_tile() {
		// identify least recently used tile
		// will be removed from lru queue in remove function
		TimePair tp = lruQueue.peek();
		if(tp != null) {
			TileKey toremove = tp.getTileKey();
			//System.out.println("removing tile from in-memory cache: " + toremove);
			// remove tile from storage
			this.remove_tile(toremove);
		}
	}
	
	// removes a specific tile from buffer
	protected synchronized void remove_tile(TileKey id) {
		//int tilesize = storage.get(id).getDataSize();
		this.storage.remove(id);
		// remove metadata
		this.remove_time_pair(id);
		//this.size -= tilesize;
	}

}
