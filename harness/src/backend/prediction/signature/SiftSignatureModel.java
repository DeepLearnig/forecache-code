package backend.prediction.signature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import edu.wlu.cs.levy.CG.KDTree;
import edu.wlu.cs.levy.CG.KeyDuplicateException;
import edu.wlu.cs.levy.CG.KeySizeException;

import utils.DBInterface;
import utils.UserRequest;
import utils.UtilityFunctions;
import backend.disk.DiskNiceTileBuffer;
import backend.disk.DiskTileBuffer;
import backend.disk.ScidbTileInterface;
import backend.memory.MemoryNiceTileBuffer;
import backend.memory.MemoryTileBuffer;
import backend.prediction.BasicModel;
import backend.prediction.DirectionPrediction;
import backend.prediction.TileHistoryQueue;
import backend.prediction.directional.MarkovDirectionalModel;
import backend.util.Direction;
import backend.util.NiceTile;
import backend.util.Params;
import backend.util.ParamsMap;
import backend.util.Signatures;
import backend.util.Tile;
import backend.util.TileKey;

public class SiftSignatureModel extends BasicModel {
	
	public static int defaultVocabSize = 100;
	public int vocabSize;
	protected boolean haveRealRoi = false;
	KDTree<Integer> vocab = null;
	protected Map<TileKey,double[]> histograms;

	public SiftSignatureModel(TileHistoryQueue ref, MemoryNiceTileBuffer membuf, DiskNiceTileBuffer diskbuf,ScidbTileInterface api, int len) {
		super(ref,membuf,diskbuf,api,len);
		this.histograms = new HashMap<TileKey,double[]>();
		this.vocabSize = defaultVocabSize;
	}
	
	@Override
	public List<DirectionPrediction> predictOrder(List<TileKey> htrace) {
		updateRoi(scidbapi); // make sure we're using the most recent ROI
		return super.predictOrder(htrace,false); // don't reverse the order here
	}
	
	@Override
	public double computeConfidence(Direction d, List<TileKey> trace) {
		TileKey prev = trace.get(trace.size() - 1);
		TileKey ckey = this.DirectionToTile(prev, d);
		if(ckey == null) {
			return defaultprob;
		}
		double confidence = 0.0;
		double[] vocabhist = buildSignature(ckey);
		for(TileKey roiKey : roi) {
			double[] roihist = histograms.get(roiKey);
			confidence += Signatures.chiSquaredDistance(vocabhist, roihist);
		}

		if(confidence < defaultprob) {
			confidence = defaultprob;
		}
		return confidence;
	}
	
	@Override
	public Double computeConfidence(TileKey id, List<TileKey> htrace) {
		return null;
	}
	
	@Override
	public Double computeDistance(TileKey id, List<TileKey> htrace) {
		double distance = 0.0;
		double[] vocabhist = buildSignature(id);
		for(TileKey roiKey : roi) {
			double[] roihist = histograms.get(roiKey);
			distance += Signatures.chiSquaredDistance(vocabhist, roihist);
		}

		if(distance < defaultprob) {
			distance = defaultprob;
		}
		return distance;
	}
	
	// stores the histograms
	public double[] buildSignature(TileKey id) {
		double[] signature = histograms.get(id);
		if(signature == null) {
			signature = buildSignatureFromKey(id);
			histograms.put(id, signature);
		}
		return signature;
	}
	
	//TODO: override these to implement a new image signature!
	public double[] buildSignatureFromMat(Mat d) {
		return Signatures.buildSiftSignature(d, vocab, vocabSize);
	}
	
	public double[] buildSignatureFromKey(TileKey id) {
		NiceTile tile = getTile(id);
		return Signatures.buildSiftSignature(tile, vocab, vocabSize);
	}
	
	@Override
	public void updateRoi() {
		updateRoi(scidbapi);
	}
	
	public void updateRoi(ScidbTileInterface scidbapi) {
		if(haveRealRoi && !history.newRoi()) return; // nothing to update
		else if (!haveRealRoi && history.newRoi()) haveRealRoi = true; // now we have a real ROI
		
		List<TileKey> roi = history.getLastRoi();
		List<TileKey> finalRois = new ArrayList<TileKey>();
		histograms.clear(); // these histograms are now obsolete
		this.roi = roi;
		List<Mat> all_descriptors = new ArrayList<Mat>();
		int rows = 0;
		int cols = 0;
		for(TileKey id : roi) { // for each tile in the ROI
			Mat d = Signatures.getSiftDescriptorsForImage(getTile(id));
			if(d.rows() > 0) {
				rows += d.rows();
				all_descriptors.add(d); // better have the same number of cols!
				finalRois.add(id);
			}
		}
		roi = finalRois;
		// merge into a single matrix
		if(all_descriptors.size() > 0) {
			cols = all_descriptors.get(0).cols();
			Mat finalMatrix = Mat.zeros(rows,cols, all_descriptors.get(0).type());
			//System.out.println("finalMatrix=("+rows+","+cols+")");
			int curr = 0;
			for(int i = 0; i < all_descriptors.size(); i++) {
				Mat d = all_descriptors.get(i);
				int r = d.rows();
				d.copyTo(finalMatrix.submat(curr,curr+r,0,cols));
				curr += r;
			}
			// these clusters are our visual words
			// each center represents the center of a word
			Mat centers = Signatures.getKmeansCenters(finalMatrix, defaultVocabSize);
			vocab = Signatures.buildKDTree(centers); // used to find nearest neighbor fast
			vocabSize = centers.rows();
/*
			if(vocabSize < defaultVocabSize) {
				for(int i = 0; i < roi.size(); i++) {
					TileKey rkey = roi.get(i);
					System.out.print(rkey.buildTileStringForFile()+ "-");
					System.out.print(all_descriptors.get(i).rows()+" ");
				}
				System.out.println();
			}
*/

			// now go back through and build histograms for each ROI
			for(int i = 0; i < all_descriptors.size(); i++) {
				Mat d = all_descriptors.get(i);
				TileKey id = roi.get(i);
				double[] signature = buildSignatureFromMat(d);
				histograms.put(id, signature); // save for later use
			}
		}
	}
	
	public static void main(String[] args) {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		String idstr = "[0, 0]";
		int zoom = 1;
		int[] tile_id = UtilityFunctions.parseTileIdInteger(idstr);
		TileKey id = new TileKey(tile_id,zoom);
		int vocabSize = defaultVocabSize;
		ScidbTileInterface scidbapi = new ScidbTileInterface(DBInterface.defaultparamsfile,DBInterface.defaultdelim);
		Mat descriptors = Signatures.getSiftDescriptorsForImage(scidbapi.getNiceTile(id));
		System.out.println("descriptors: "+descriptors.rows()+","+descriptors.cols());
		Mat centers = Signatures.getKmeansCenters(descriptors, vocabSize);
		System.out.println("centers dimensions: "+centers.dims()+", ("+centers.rows()+","+centers.cols()+")");
		//for(int i = 0; i < centers.cols(); i++) {
		//	System.out.print(centers.get(0, i)[0]+" ");
		//	System.out.println();
		//}
		
		KDTree<Integer> vocab = Signatures.buildKDTree(centers);
		double[] hist = Signatures.buildSiftSignature(descriptors, vocab, defaultVocabSize);
		for(int i = 0; i < hist.length; i++) {
			System.out.print(" "+hist[i]);
		}
		System.out.println();
		
		Signatures.writeMat(Signatures.denseSiftString,descriptors, id);
		descriptors.release();
		Mat test = Signatures.readMat(Signatures.siftString,id);
		System.out.println(test.get(0, 120)[0]+","+test.get(27,59)[0]);
		//System.out.println(descriptors.get(0, 120)[0]+","+descriptors.get(27,59)[0]);
	}
}
