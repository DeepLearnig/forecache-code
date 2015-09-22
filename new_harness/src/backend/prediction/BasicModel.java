package backend.prediction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import abstraction.util.Model;
import abstraction.util.NewNewTileKey;
import abstraction.util.NewTileKey;

public class BasicModel {
	protected int len;
	protected TileHistoryQueue history = null;
	protected boolean useDistanceCorrection = true;
	public static final double defaultprob = .00000000001; // default assigned confidence value
	public List<NewNewTileKey> roi = null;
	protected Model m = null;
	

	public BasicModel(TileHistoryQueue ref, NiceTileBuffer membuf, 
			NiceTileBuffer diskbuf,TileInterface api, int len) {
		this.history = ref; // reference to (syncrhonized) global history object
		this.membuf = membuf;
		this.diskbuf = diskbuf;
		this.paramsMap = new ParamsMap(DBInterface.defaultparamsfile,DBInterface.defaultdelim);
		this.scidbapi = api;
		this.len = len;
	}
	
	public int getMaxLen() {
		return this.len;
	}
	
	public void computeSignaturesInParallel(List<NewTileKey> ids) {}
	
	public List<NewTileKey> orderCandidates(List<NewTileKey> candidates) {
		//long a = System.currentTimeMillis();
		updateRoi();
		//long b = System.currentTimeMillis();
		List<NewTileKey> htrace = history.getHistoryTrace(len);
		if(htrace.size() == 0) {
			return new ArrayList<NewTileKey>();
		}
		NewTileKey prev = htrace.get(htrace.size() - 1);
		List<NewTileKey> myresult = new ArrayList<NewTileKey>();
		List<TilePrediction> order = new ArrayList<TilePrediction>();
		computeSignaturesInParallel(candidates);
		// for each direction, compute confidence
		for(NewTileKey key : candidates) {
			TilePrediction tp = new TilePrediction(this.m);
			tp.id = key;
			tp.confidence = computeConfidence(key, htrace);
			tp.distance = computeDistance(key,htrace);
			tp.useDistance = useDistanceCorrection;
			if(tp.useDistance) {
				tp.physicalDistance = UtilityFunctions.manhattanDist(prev, key);
			}
			order.add(tp);
		}
		//long c = System.currentTimeMillis();
		Collections.sort(order);
		for(int i = 0; i < order.size(); i++) {
			TilePrediction tp = order.get(i);
			myresult.add(tp.id);
			//System.out.print(tp+" "+tp.physicalDistance+" ");
			//System.out.println(tp.id);
		}
		//System.out.println();
		//long d = System.currentTimeMillis();
		//System.out.println("roi:sort-"+(d-c)+",predict-"+(c-b)+",roi-"+(b-a));
		return myresult;
	}
	
	public void updateRoi() {
		roi = history.getLastRoi();
	}
	
	//TODO: override these to do ROI predictions
	public Double computeConfidence(NewTileKey id, List<NewTileKey> htrace) {
		return defaultprob;
	}
	
	public Double computeDistance(NewTileKey id, List<NewTileKey> htrace) {
		return null;
	}
	
	// gets ordering of directions by confidence and returns topk viable options
	public List<NewTileKey> predictTiles(int topk) {
		List<NewTileKey> myresult = new ArrayList<NewTileKey>();

		// do we have access to the last request?
		List<NewTileKey> htrace = history.getHistoryTrace(len);
		if(htrace.size() == 0) {
			return myresult;
		}
		List<DirectionPrediction> order = predictOrder(htrace);
		NewTileKey last = htrace.get(htrace.size()-1);
		for(int i = 0; i < order.size(); i++) {
			DirectionPrediction dp = order.get(i);
			NewTileKey val = this.DirectionToTile(last, dp.d);
			if(val != null) {
				myresult.add(val);
				//System.out.println(val);
			}
		}
		//System.out.println("viable options: "+myresult.size());
		if(topk >= myresult.size()) { // truncate if list is too long
			topk = myresult.size() - 1;
			if(topk < 0) {
				topk = 0;
			}
		}
		myresult = myresult.subList(0, topk);
		return myresult;
	}
	
	public List<DirectionPrediction> predictOrder(List<NewTileKey> htrace) {
		return predictOrder(htrace,false);
	}
	
	public List<DirectionPrediction> predictOrder(List<NewTileKey> htrace, boolean reverse) {
		List<DirectionPrediction> order = new ArrayList<DirectionPrediction>();
		//long start = System.currentTimeMillis();
		// for each direction, compute confidence
		for(Direction d : Direction.values()) {
			DirectionPrediction dp = new DirectionPrediction();
			
			dp.d = d;
			dp.confidence = computeConfidence(d, htrace);
			order.add(dp);
		}
		if(!reverse) {
			Collections.sort(order);
		} else {
			Collections.sort(order,Collections.reverseOrder()); // smaller numbers are better here
		}
		//longpend = System.currentTimeMillis();
		/*
		for(DirectionPrediction dp : order) {
			System.out.println(dp);
		}*/
		//System.out.println("time to predict order: "+(end-start)+"ms");
		return order;
	}
	
	public double computeConfidence(Direction d, List<NewTileKey> htrace) {
		return defaultprob;
	}
	
	public NiceTile getTile(NewTileKey key) {
		NiceTile t = membuf.getTile(key);
		if(t == null) {
			t = diskbuf.getTile(key);
			if(t == null) {
				t = this.scidbapi.getNiceTile(key);
			}
		}
		return t;
	}
	
	public NewTileKey DirectionToTile(NewTileKey prev, Direction d) {
		int[] tile_id = prev.id.clone();
		int x = tile_id[0];
		int y = tile_id[1];
		int zoom = prev.zoom;
		
		// if zooming in, update values
		if((d == Direction.IN1) || (d == Direction.IN2) || (d == Direction.IN3) || (d == Direction.IN0)) {
			zoom++;
			x *= 2;
			y *=2;
			tile_id[0] = x;
			tile_id[1] = y;
		}
		
		switch(d) {
		case UP:
			tile_id[1] =y+1;
			break;
		case DOWN:
			tile_id[1] =y-1;
			break;
		case LEFT:
			tile_id[0] =x-1;
			break;
		case RIGHT:
			tile_id[0] =x+1;
			break;
		case OUT:
			zoom -= 1;
			x /= 2;
			y /= 2;
			tile_id[0] =x;
			tile_id[1] =y;
			break;
		case IN0: // handled above
			break;
		case IN1:
			tile_id[1] =y+1;
			break;
		case IN3:
			tile_id[0] =x+1;
			break;
		case IN2:
			tile_id[0] =x+1;
			tile_id[1] =y+1;
			break;
		}
		NewTileKey key = new NewTileKey(tile_id,zoom);
		//System.out.println("last access: ("+prev.tile_id+", "+prev.zoom+")");
		if(!this.paramsMap.allKeys.containsKey(key)) {
			return null;
		}
		//System.out.println("recommendation: "+key);
		return key;
	}
	
	public String buildDirectionStringFromString(List<UserRequest> trace) {
		if(trace.size() < 2) {
			return "";
		}
		String dirstring = "";
		int i = 1;
		UserRequest n = trace.get(0);
		while(i < trace.size()) {
			UserRequest p = n;
			n = trace.get(i);
			Direction d = UtilityFunctions.getDirection(p,n);
			if(d != null) {
				dirstring += d;
			}
			i++;
		}
		return dirstring;
	}
	
	public String buildDirectionStringFromKey(List<NewTileKey> trace) {
		if(trace.size() < 2) {
			return "";
		}
		String dirstring = "";
		int i = 1;
		NewTileKey n = trace.get(0);
		while(i < trace.size()) {
			NewTileKey p = n;
			n = trace.get(i);
			Direction d = UtilityFunctions.getDirection(p,n);
			if(d != null) {
				dirstring += d;
			}
			i++;
		}
		return dirstring;
	}
	
	public static NewTileKey getKeyFromRequest(UserRequest request) {
		int[] id = UtilityFunctions.parseTileIdInteger(request.tile_id);
		return new NewTileKey(id,request.zoom);
	}
	
	public List<NewTileKey> getCandidates(double maxDist) {
		NewTileKey last = this.history.getLast();
		if(last == null) return new ArrayList<NewTileKey>();
		return getCandidates(last,maxDist);
	}
	
	public List<NewTileKey> getCandidates(NewTileKey current, double maxDist) {
		List<NewTileKey> candidates = new ArrayList<NewTileKey>();
		for(NewTileKey pcand : this.paramsMap.allKeysSet) {
			double dist = UtilityFunctions.manhattanDist(pcand, current);
			if((dist <= maxDist) && (dist > 0)) { // don't include the tile itself
				candidates.add(pcand);
			}
		}
		
		// include everything that is already in the cache
		//candidates.addAll(this.membuf.getAllNewTileKeys());

		return candidates;
	}
}
