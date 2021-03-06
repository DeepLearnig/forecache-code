package implementation.mimic;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import abstraction.enums.DBConnector;
import abstraction.structures.View;
import abstraction.structures.ViewMap;
import abstraction.util.DBInterface;

import configurations.Config;
import configurations.VMConfig;



/**
 * @author leibatt
 * This class can be used to generate forecache views for the given mimic waveform id
 */
public class MimicWaveformViewFactory {
	public static String nameTemplate = "FC_WF?";
	public static String queryTemplate = "apply(filter("+
			"slice(waveform_signal_table,RecordName,?)"+
			",not(is_nan(signal)))"+
			",msec2,msec)";
	public static String[] attributeNames = new String[] {
		"signal",
		"msec2"
	};
	public static String[] summaryNames = new String[] {
		"avg_"+attributeNames[0],
		attributeNames[1]};
	public static String[] summaries = new String[]{
			"avg("+attributeNames[0]+") as "+summaryNames[0],
			"min("+attributeNames[1]+") as "+summaryNames[1]};
	public static DBConnector connectionType = DBConnector.BIGDAWG;
	
	public String viewsFolder;
	public ViewMap mimicViews;
	
	public MimicWaveformViewFactory() {
		this.viewsFolder = DBInterface.mimic_views_folder;;
		File modisDir = new File(this.viewsFolder);
		if(!modisDir.exists()) {
			try {
				modisDir.mkdir();
			} catch (SecurityException e) {
				System.err.println("could not create directory "+modisDir);
			}
		}
		this.mimicViews = new ViewMap(viewsFolder);
	}
	
	public View getMimicWaveformView(String recordName) throws Exception {
		String viewName = nameTemplate.replace("?", recordName);
		String query = queryTemplate.replace("?", recordName);
		List<String> attributes = Arrays.asList(attributeNames);
		List<String> summaryFunctions = Arrays.asList(summaries);
		List<String> summaryLabels = Arrays.asList(summaryNames);
		View v = this.mimicViews.getView(viewName, query, attributes, summaryFunctions, summaryLabels, connectionType);
		this.mimicViews.saveView(v); // save it on disk so we don't have to make this view again
		return v;
	}
	
	public static void main(String[] args) {
		Config config = new VMConfig(); // use vm-specific configurations
		config.setConfig();
		
		MimicWaveformViewFactory factory = new MimicWaveformViewFactory();
		try {
			View v = factory.getMimicWaveformView("325553800011");
			System.out.println(v.getName());
			System.out.println(v.getQuery());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
