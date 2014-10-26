package backend.prediction.directional;

import java.util.List;

import edu.berkeley.nlp.lm.map.NgramMap;
import edu.berkeley.nlp.lm.values.ProbBackoffPair;

import backend.disk.DiskTileBuffer;
import backend.disk.ScidbTileInterface;
import backend.memory.MemoryTileBuffer;
import backend.prediction.TileHistoryQueue;
import backend.util.Direction;
import utils.UserRequest;

public class MarkovChainDirectionalModel extends NGramDirectionalModel {
	public MarkovChainDirectionalModel(TileHistoryQueue ref, MemoryTileBuffer membuf, DiskTileBuffer diskbuf,ScidbTileInterface api, int len) {
		super(ref,membuf,diskbuf,api,len);
	}
	
	@Override
	public double computeConfidence(Direction d, List<UserRequest> trace) {
		String p = buildDirectionString(trace);
		p += " " + d.getWord();
		//System.out.println("dirstring: \""+p +"\"");
		String[] prefix = p.split(" ");
		int[] ngram = new int[prefix.length];
		for(int i = 0; i < prefix.length; i++) {
			ngram[i] = wordIndexer.getIndexPossiblyUnk(prefix[i]);
		}
		NgramMap<ProbBackoffPair> x = lm.getNgramMap();
		ProbBackoffPair pbp = x.get(ngram, 0, ngram.length); // end index must be 1 past the index you want to include
		
		// this gives me the very basic Markov stuff
		if(pbp != null) {
			return pbp.prob;
		} else {
			return defaultprob;
		}
	}
}
