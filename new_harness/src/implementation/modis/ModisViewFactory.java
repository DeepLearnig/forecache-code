package implementation.modis;

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
 * schema:
 * [("ndsi_agg_7_18_2013<ndsi:double NULL DEFAULT null,ndsi_count:uint64 NULL DEFAULT null,
 * land_sea_mask:uint8 NULL DEFAULT null> [longitude_e4ndsi_06_03_2013=0:44444,3000,0,
 * latitude_e4ndsi_06_03_2013=0:22222,3000,0]")]
 */
public class ModisViewFactory {
	public static String name = "FC_Modis";
	public static String array_name = "ndsi_agg_7_18_2013";
	public static String query = array_name;
	public static String scanQuery = "scan("+ array_name+")";
	public static String[] attributeNames = new String[] {
		"ndsi",
		"land_sea_mask",
		"ndsi_count"
	};
	public static String[] summaryNames = new String[] {
		"avg_"+attributeNames[0],
		"min_"+attributeNames[1],
		attributeNames[2]};
	public static String[] summaries = new String[]{
		"avg("+attributeNames[0]+") as "+summaryNames[0],
		"min("+attributeNames[1]+") as "+summaryNames[1],
		"sum("+attributeNames[2]+") as "+summaryNames[2]};
	public static DBConnector connectionType = DBConnector.SCIDB;
	
	public String viewsFolder;
	public ViewMap modisViews;
	
	public ModisViewFactory() {
		this.viewsFolder = DBInterface.modis_views_folder;
		File modisDir = new File(this.viewsFolder);
		if(!modisDir.exists()) {
			try {
				modisDir.mkdir();
			} catch (SecurityException e) {
				System.err.println("could not create directory "+modisDir);
			}
		}
		this.modisViews = new ViewMap(viewsFolder);
	}
	
	public View getModisView() throws Exception {
		List<String> attributes = Arrays.asList(attributeNames);
		List<String> summaryFunctions = Arrays.asList(summaries);
		List<String> summaryLabels = Arrays.asList(summaryNames);
		View v = this.modisViews.getView(name, scanQuery, attributes, summaryFunctions, summaryLabels, connectionType);
		this.modisViews.saveView(v); // save it on disk so we don't have to make this view again
		return v;
	}
	
	public static void main(String[] args) {
		Config config = new VMConfig(); // use vm-specific configurations
		config.setConfig();
		
		ModisViewFactory factory = new ModisViewFactory();
		try {
			View v = factory.getModisView();
			System.out.println(v.getName());
			System.out.println(v.getQuery());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
