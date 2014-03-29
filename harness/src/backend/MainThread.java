package backend;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import backend.disk.DiskTileBuffer;
import backend.disk.ScidbTileInterface;
import backend.memory.MemoryTileBuffer;
import backend.prediction.TileHistoryQueue;
import backend.prediction.TrainModels;
import backend.prediction.directional.HotspotDirectionalModel;
import backend.prediction.directional.MarkovDirectionalModel;
import backend.prediction.directional.MomentumDirectionalModel;
import backend.prediction.directional.RandomDirectionalModel;
import backend.util.Model;
import backend.util.Tile;
import backend.util.TileKey;
import utils.DBInterface;
import utils.UtilityFunctions;

public class MainThread {
	public static MemoryTileBuffer membuf;
	public static DiskTileBuffer diskbuf;
	public static ScidbTileInterface scidbapi;
	public static int histmax = 10;
	public static TileHistoryQueue hist;
	
	//server
	public static Server server;
	
	//accuracy
	public static int total_requests = 0;
	public static int cache_hits = 0;
	
	// General Model variables
	public static Model[] modellabels = {Model.MOMENTUM};
	public static String taskname = "task1";
	public static int[] user_ids = {28};
	public static int defaultpredictions = 3;
	
	// global model objects
	public static MarkovDirectionalModel mdm;
	public static RandomDirectionalModel rdm;
	public static HotspotDirectionalModel hdm;
	public static MomentumDirectionalModel momdm;
	
	public static void setupModels() {
		for(int i = 0; i < modellabels.length; i++) {
			Model label = modellabels[i];
			switch(label) {
				case MARKOV: mdm = new MarkovDirectionalModel(MarkovDirectionalModel.defaultlen,hist);
				break;
				case RANDOM: rdm = new RandomDirectionalModel(hist);
				break;
				case HOTSPOT: hdm = new HotspotDirectionalModel(hist,HotspotDirectionalModel.defaulthotspotlen);
				break;
				case MOMENTUM: momdm = new MomentumDirectionalModel(hist);
				break;
				default://do nothing
			}
		}
	}
	
	public static void trainModels() {
		for(int i = 0; i < modellabels.length; i++) {
			Model label = modellabels[i];
			switch(label) {
				case MARKOV: TrainModels.TrainMarkovDirectionalModel(user_ids, taskname, mdm);
				break;
				case HOTSPOT: TrainModels.TrainHotspotDirectionalModel(user_ids, taskname, hdm);
				default://do nothing
			}
		}
	}
	
	public static List<TileKey> getPredictions() {
		Map<TileKey,Double> predictions = new HashMap<TileKey,Double>();
		for(int i = 0; i < modellabels.length; i++) {
			Model label = modellabels[i];
			Double basevote = 1.0; // value of a vote from this model
			List<TileKey> toadd;
			switch(label) {
				case MARKOV: toadd = mdm.predictTiles(defaultpredictions);
				break;
				case RANDOM: toadd = rdm.predictTiles(defaultpredictions);
				break;
				case HOTSPOT: toadd = hdm.predictTiles(defaultpredictions);
				break;
				case MOMENTUM: toadd = momdm.predictTiles(defaultpredictions);
				break;
				default: toadd = null;
			}
			// count votes per prediction scheme
			if((toadd != null) && (toadd.size() > 0)) {
				// weight votes by ordering
				Double currvote = basevote;
				for(int kid = 0; kid < toadd.size(); kid++) {
					TileKey key = toadd.get(kid);
					System.out.println("key: "+key+",vote: "+currvote);
					Double count = predictions.get(key);
					if(count == null) {
						predictions.put(key,currvote);
					} else {
						//System.out.println("key: "+key+",vote: "+currvote);
						predictions.put(key,count+currvote);
					}
					currvote /= 2;
				}
			}
		}
		List<TileVote> votes = new ArrayList<TileVote>();
		for(TileKey candidate : predictions.keySet()) {
			votes.add(new TileVote(candidate,predictions.get(candidate)));
		}
		Collections.sort(votes,Collections.reverseOrder()); // sort by votes
		int end = defaultpredictions;
		if(end > votes.size()) {
			end = votes.size();
		}
		votes = votes.subList(0,end); // truncate to get the final list
		List<TileKey> output = new ArrayList<TileKey>();
		for(TileVote finalvote : votes) {
			output.add(finalvote.key);
			System.out.println("predicted: '"+finalvote.key+"' with votes: '"+finalvote.vote+"'");
		}
		return output;
	}
	
	public static void insertPredictions(List<TileKey> predictions) {
		if(predictions != null) {
			for(TileKey key: predictions) { // insert the predictions into the cache
				if(!membuf.peek(key)) { // not in memory
					Tile tile = diskbuf.getTile(key);
					if(tile == null) { // not on disk
						// get from database
						tile = scidbapi.getTile(key);
						//insert in disk cache
						diskbuf.insertTile(tile);
					} else { // found it on disk
						// update timestamp
						diskbuf.touchTile(key);
					}
					//insert in mem cache
					membuf.insertTile(tile);
				} else { // found it in memory
					// update timestamp
					membuf.touchTile(key);
				}
			}
		}
	}
	
	public static void setupServer() throws Exception {
		server = new Server(8080);
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/gettile");
		server.setHandler(context);
		context.addServlet(new ServletHolder(new FetchTileServlet()), "/*");
		server.start();
	}

	public static void main(String[] args) throws Exception {
		// get models to use
		if(args.length > 0) {
			String[] modelstrs = args[0].split(",");
			Model[] argmodels = new Model[modelstrs.length];
			for(int i = 0; i < modelstrs.length; i++) {
				System.out.println("modelstrs["+i+"] = '"+modelstrs[i]+"'");
				if(modelstrs[i].equals("markov")) {
					argmodels[i] = Model.MARKOV;
				} else if(modelstrs[i].equals("random")) {
					argmodels[i] = Model.RANDOM;
				} else if(modelstrs[i].equals("hotspot")) {
					argmodels[i] = Model.HOTSPOT;
				} else if(modelstrs[i].equals("momentum")) {
					argmodels[i] = Model.MOMENTUM;
				}
			}
			modellabels = argmodels;
		}
		// get user ids to train on
		if(args.length > 1) {
			String[] userstrs = args[1].split(",");
			int[] argusers = new int[userstrs.length];
			for(int i = 0; i < userstrs.length; i++) {
				System.out.println("userstrs["+i+"] = '"+userstrs[i]+"'");
				argusers[i] = Integer.parseInt(userstrs[i]);
			}
			user_ids = argusers;
		}
		
		// get taskname
		if(args.length > 2) {
			taskname = args[2];
			System.out.println("taskname: "+taskname);
		}
		
		// get num predictions
		if(args.length > 3) {
			defaultpredictions = Integer.parseInt(args[3]);
			System.out.println("predictions: "+defaultpredictions);
		}
		
		// initialize cache managers
		membuf = new MemoryTileBuffer();
		diskbuf = new DiskTileBuffer(DBInterface.cache_root_dir,DBInterface.hashed_query,DBInterface.threshold);
		scidbapi = new ScidbTileInterface(DBInterface.defaultparamsfile,DBInterface.defaultdelim);
		hist = new TileHistoryQueue(histmax);
		
		//setup models for prediction
		setupModels();
		trainModels();
		
		//start the server
		setupServer();
	}
	
	private static class TileVote implements Comparable<TileVote>{
		TileKey key;
		double vote;
		
		public TileVote(TileKey key, double vote) {
			this.key = key;
			this.vote = vote;
		}
		
		@Override
		public int compareTo(TileVote other) {
			double diff = this.vote - other.vote;
			if(diff < 0) {
				return -1;
			} else if(diff > 0) {
				return 1;
			} else {
				return 0;
			}
		}
	}
	
	/**
	 * Java requires a serial version ID for the class.
	 * Has something to do with it being serializable?
	 */
	public static class FetchTileServlet extends HttpServlet {

		private static final long serialVersionUID = 6537664694070363096L;
		private static final String greeting = "Hello World";

		protected void doGet(HttpServletRequest request,
				HttpServletResponse response) throws ServletException, IOException {
			
			// get fetch parameters
			//String hashed_query = request.getParameter("hashed_query");
			String end = request.getParameter("end");
			if((end != null)) { // stop server
				System.out.println("end: "+end);
				try {
					server.stop();
					return;
				} catch (Exception e) {
					// TODO Auto-generated catch block
					System.out.println("could not stop server");
					e.printStackTrace();
				}
			}
			String zoom = request.getParameter("zoom");
			String tile_id = request.getParameter("tile_id");
			String threshold = request.getParameter("threshold");
			//System.out.println("hashed query: " + hashed_query);
			System.out.println("zoom: " + zoom);
			System.out.println("tile id: " + tile_id);
			System.out.println("threshold: " + threshold);
			Tile t = fetchTile(tile_id,zoom,threshold);
			response.setContentType("text/html");
			response.setStatus(HttpServletResponse.SC_OK);
			if(t == null) {
				response.getWriter().println(greeting);
			} else {
				response.getWriter().println(t.encodeData());
			}
		}
		
		// fetches tiles from
		protected Tile fetchTile(String tile_id, String zoom, String threshold) {
			String reverse = UtilityFunctions.unurlify(tile_id); // undo urlify
			List<Integer> id = UtilityFunctions.parseTileIdInteger(reverse);
			int z = Integer.parseInt(zoom);
			TileKey key = new TileKey(id,z);
			List<TileKey> predictions = null;
			
			boolean found = false;
			long start = System.currentTimeMillis();
			Tile t = membuf.getTile(key);
			if(t == null) { // not cached
				System.out.println("tile is not in mem-based cache");
				// go find the tile on disk
				t = diskbuf.getTile(key);
				if(t == null) { // not in memory
					System.out.println("tile is not in disk-based cache. computing...");
					t = scidbapi.getTile(key);
					diskbuf.insertTile(t);
				} else { // found on disk
					System.out.println("found tile in disk-based cache");
					System.out.println("data size: " + t.getDataSize());
					// update timestamp
					diskbuf.touchTile(key);
				}
				// put the tile in the cache
				membuf.insertTile(t);
			} else { // found in memory
				cache_hits++;
				System.out.println("found tile in mem-based cache");
				System.out.println("data size: " + t.getDataSize());
				// update timestamp
				membuf.touchTile(key);
				found = true;
			}
			total_requests++;
			hist.addRecord(t);
			//System.out.println("history length: " + hist.getHistoryLength());
			System.out.println("history:");
			System.out.println(hist);
			long end = System.currentTimeMillis();
			// get predictions for next request
			predictions = getPredictions();
			insertPredictions(predictions);
			long end2 = System.currentTimeMillis();
			System.out.println("time to retrieve requested tile: " + ((end - start)/1000)+"s");
			System.out.println("time to insert predictions: " + ((end2 - end)/1000)+"s");
			System.out.println("cache miss for tile "+key+"?: "+(!found));
			System.out.println("current accuracy: "+ (1.0 * cache_hits / total_requests));
			System.out.println("cache size: "+membuf.tileCount()+" tiles");
			return t;
		}

	}

}
