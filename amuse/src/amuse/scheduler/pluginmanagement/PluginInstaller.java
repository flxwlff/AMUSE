/** 
 * This file is part of AMUSE framework (Advanced MUsic Explorer).
 * 
 * Copyright 2006-2010 by code authors
 * 
 * Created at TU Dortmund, Chair of Algorithm Engineering
 * (Contact: <http://ls11-www.cs.tu-dortmund.de>) 
 *
 * AMUSE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AMUSE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with AMUSE. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Creation date: 08.12.2009
 */
package amuse.scheduler.pluginmanagement;

import amuse.data.io.ArffDataSet;
import amuse.data.io.DataSetAbstract;
import amuse.interfaces.plugins.PluginInstallerInterface;
import amuse.interfaces.scheduler.SchedulerException;

import amuse.preferences.AmusePreferences;
import amuse.preferences.KeysStringValue;
import amuse.util.AmuseLogger;
import amuse.util.FileOperations;
import amuse.util.JarClassLoader;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

import org.apache.log4j.Level;

/**
 * PluginInstaller installs AMUSE plugins
 * 
 * @author Igor Vatolkin
 * @version $Id: $
 */
public class PluginInstaller {
	
	/** Path to folder with plugin files */
	private String pathToPluginFolder = null;
	
	/** Plugin installation properties which must be in pathToPluginFolder/plugin.properties */
	private Properties installProperties = null;
	
	/** Different possible version states */
	public enum VersionState {
		ALPHA, BETA, RC, STABLE
	};
	
	/**
	 * Standard constructor
	 * @param pathToPluginFolder Path to folder with plugin files
	 */
	public PluginInstaller(String pathToPluginFolder) {
		this.pathToPluginFolder = new String(pathToPluginFolder);
		this.installProperties = new Properties();
	}
	
	/**
	 * Installs the plugin
	 */
	public void installPlugin() throws SchedulerException {
		
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"Starting plugin installation..........");
		
		// TODO Save the information about OS (e.g. if some plugins or tools can be run only under certain OS)
		
		try {
			FileInputStream propertiesInput = new FileInputStream(pathToPluginFolder + "/plugin.properties");
			this.installProperties.load(propertiesInput);
		} catch(IOException e) {
			throw new SchedulerException("Could not load the plugin properties: " + e.getMessage());
		}
		
		// -----------------------
		// (1) Check AMUSE version
		// -----------------------
		checkAmuseVersion();
		
		// ---------------------------------------------------------------
		// (2) Is this plugin already installed? (check pluginTable.arff). 
		//     If yes, throw SchedulerException ("please deinstall first")
		// ---------------------------------------------------------------
		checkPluginInstallationState();
		
		// -------------------------------------------------------------------------------
		// (3) If tools with the same version are there it's okay (will not be installed); 
		//     if with other version, not okay; if they are not there at all, ok (will be 
		//     installed)
		// -------------------------------------------------------------------------------
		checkToolVersions();
		
		// ------------------------------------------------------------------------------
		// (4) Copy plugin JAR (name given in plugin.properties) to AMUSEHOME/lib/plugins
		// ------------------------------------------------------------------------------
		copyPluginJar();
		
		// ----------------------------------------------------------
		// (5) Update toolTable.arff with list of current AMUSE tools
		// ----------------------------------------------------------
		updateTools();
		
		// ---------------------------------------------------------------------------
		// (6) Search in pathToPluginFolder for AMUSE config arffs (featureTable) etc.
		//     If any is found, update the corresponding AMUSE config arffs 
		// ---------------------------------------------------------------------------
		updateAlgorithmTables();
		
		// ------------------------------------------------------
		// (7) Update pluginTable.arff adding the new plugin data
		// ------------------------------------------------------
		updatePluginTable();
		
		// ------------------------------------------------------------------------------------
		// (8) Save some data for deinstallation routine (plugin.properties, installer.jar etc.
		//     The destination folder is AMUSEHOME/config/plugininfo/$PLUGIN_ID$
		saveDataForDeinstallation();
		
		// ----------------------------------------------------------------------------------
		// (9) Run any plugin-specific installation routines (e.g. asking for MATLABHOME) if 
		//      pluginManager.jar is in the folder
		// ----------------------------------------------------------------------------------
		runFurtherRoutines();
		
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"..........plugin succesfully installed");
	}
	
	/**
	 * Checks the AMUSE version; if it is older than required, throws an exception
	 */
	private void checkAmuseVersion() throws SchedulerException {
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"AMUSE version check...");
		
		double requiredVersionNumber = Double.POSITIVE_INFINITY;
		VersionState requiredVersionState = null;
		String requiredVersionDescription;
		double currentVersionNumber = Double.NEGATIVE_INFINITY;
		VersionState currentVersionState = null;
		String currentVersionDescription;
		
		// Get the required AMUSE version
		requiredVersionDescription = installProperties.getProperty("REQUIRED_AMUSE_VERSION");
		if(requiredVersionDescription.indexOf(" ") == -1) {
			requiredVersionNumber = new Double(requiredVersionDescription);
			requiredVersionState = VersionState.STABLE;
		} else {
			requiredVersionNumber = new Double(requiredVersionDescription.substring(0,requiredVersionDescription.indexOf(" ")));
			String state = new String(requiredVersionDescription.substring(requiredVersionDescription.indexOf(" ") + 1,
				requiredVersionDescription.length()));
			if(state.toLowerCase().equals("alpha")) {
				requiredVersionState = VersionState.ALPHA;
			} else if(state.toLowerCase().equals("beta")) {
				requiredVersionState = VersionState.BETA;
			} else if(state.toLowerCase().equals("rc")) {
				requiredVersionState = VersionState.RC;
			} else {
				requiredVersionState = VersionState.STABLE;
			}
		}
			
		// Get the current AMUSE version
	    currentVersionDescription = AmusePreferences.get(KeysStringValue.AMUSE_VERSION);
		if(currentVersionDescription.indexOf(" ") == -1) {
			currentVersionNumber = new Double(currentVersionDescription);
			currentVersionState = VersionState.STABLE;
		} else {
			currentVersionNumber = new Double(currentVersionDescription.substring(0,currentVersionDescription.indexOf(" ")));
			String state = new String(currentVersionDescription.substring(currentVersionDescription.indexOf(" ") + 1,
					currentVersionDescription.length()));
			if(state.toLowerCase().equals("alpha")) {
				currentVersionState = VersionState.ALPHA;
			} else if(state.toLowerCase().equals("beta")) {
				currentVersionState = VersionState.BETA;
			} else if(state.toLowerCase().equals("rc")) {
				currentVersionState = VersionState.RC;
			} else {
				currentVersionState = VersionState.STABLE;
			}
		}
		
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"Required version: " + requiredVersionDescription + 
				"; current version: " + currentVersionDescription);
		
		if(requiredVersionNumber > currentVersionNumber ||
			(requiredVersionNumber == currentVersionNumber) && (requiredVersionState.ordinal() > currentVersionState.ordinal())) {
			throw new SchedulerException("Could not install plugin");
		}
		
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"..check passed");
	}
	
	/**
	 * Checks if this plugin has been already installed (in that case throws an exception)
	 */
	private void checkPluginInstallationState() throws SchedulerException {
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"Starting plugin state check...");
		
		DataSetAbstract installedPluginList;
		try {
			installedPluginList = new ArffDataSet(new File(System.getenv("AMUSEHOME") + "/config/pluginTable.arff"));
		} catch(IOException e) {
			throw new SchedulerException("Could not load the list with installed plugins: " + e.getMessage());
		}
		
		for(int i=0;i<installedPluginList.getValueCount();i++) {
			if(installedPluginList.getAttribute("Id").getValueAt(i).equals(new Double(installProperties.getProperty("ID")))) {
				throw new SchedulerException("Plugin is already installed, please deinstall first!");
			}
		}
		
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"..check passed (plugin not installed)");
	}
	
	/**
	 * Checks if the required tools are installed. If any tool is already installed and the version of installed and required
	 * tools are not the same, throws an exception 
	 */
	private void checkToolVersions() throws SchedulerException {
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"Starting tool state check...");
		
		File toolList = new File(pathToPluginFolder + "/toolTable.arff");
		
		// Are any tools required?
		if(toolList.exists()) {
			
			DataSetAbstract installedToolList;
			DataSetAbstract requiredToolList;
			try {
				requiredToolList = new ArffDataSet(toolList);
				installedToolList = new ArffDataSet(new File(System.getenv("AMUSEHOME") + "/config/toolTable.arff"));
				
				// Go through all required tools and check if they are already installed
				for(int i=0;i<requiredToolList.getValueCount();i++) {
					Integer currentId = new Double(requiredToolList.getAttribute("Id").getValueAt(i).toString()).intValue();
					String currentVersionDescription = requiredToolList.getAttribute("VersionDescription").getValueAt(i).toString();
					
					// Go through all required tools and check if they are already installed
					for(int j=0;j<installedToolList.getValueCount();j++) {
						
						// Tool found!
						if(new Integer(new Double(installedToolList.getAttribute("Id").getValueAt(j).toString()).intValue()).equals(currentId)) {
							if(!installedToolList.getAttribute("VersionDescription").getValueAt(j).toString().equals(currentVersionDescription)) {
								throw new SchedulerException("Tool " + installedToolList.getAttribute("Name").getValueAt(j).toString() + 
									" is already installed with version '" + 
									installedToolList.getAttribute("VersionDescription").getValueAt(j).toString() + 
									"'; plugin version is '" + currentVersionDescription + "'");
							}
						}
					}
				}
				
			} catch(IOException e) {
				throw new SchedulerException("Could not load the list with installed tools: " + e.getMessage());
			}
		}
		
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"..check passed");
	}
	
	/**
	 * Copies the plugin jar to AMUSE lib folder
	 */
	private void copyPluginJar() throws SchedulerException {
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"Starting copying of plugin jar...");
		
		File pathToPluginJar = new File(pathToPluginFolder + "/" + installProperties.getProperty("PLUGIN_JAR"));
		File pluginFolder = new File(System.getenv("AMUSEHOME") + "/lib/plugins/");
		File destination = new File(System.getenv("AMUSEHOME") + "/lib/plugins/" + installProperties.getProperty("PLUGIN_JAR"));
		
		// Create folder for plugin jars if it does not exist
		if(!pluginFolder.exists()) {
			pluginFolder.mkdirs();
		}
		
		// Copy plugin JAR
		try {
			FileOperations.copy(pathToPluginJar,destination, Level.INFO);
		} catch (IOException e) {
			throw new SchedulerException("Could not copy plugin jar to '" + destination + "': " + e.getMessage());
		}
		
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"..copying finished");
	}
	
	/**
	 * Updates toolTable.arff with list of current AMUSE tools with the sort order according to tool ids and copies new tools.
	 */
	private void updateTools() throws SchedulerException {
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"Starting tool list update...");
		
		File toolList = new File(pathToPluginFolder + "/toolTable.arff");
		
		// Are any tools required?
		if(toolList.exists()) {
			
			DataSetAbstract newToolList;
			DataSetAbstract installedToolList;
			ArrayList<Integer> listOfToolsWhichAreAlreadyInstalledAndAreRequiredByThisPlugin = new ArrayList<Integer>();
			
			// Key: tool id; value: position in the DataSet
			HashMap<Integer,Integer> newToolsMap = new HashMap<Integer,Integer>();
			HashMap<Integer,Integer> installedToolsMap = new HashMap<Integer,Integer>();
			try {
				newToolList = new ArffDataSet(toolList);
				installedToolList = new ArffDataSet(new File(System.getenv("AMUSEHOME") + "/config/toolTable.arff"));
				
				// Go through all installed tools
				for(int j=0;j<installedToolList.getValueCount();j++) {
					installedToolsMap.put(new Double(installedToolList.getAttribute("Id").getValueAt(j).toString()).intValue(), j);
				}
					
				// Go through all new tools
				for(int i=0;i<newToolList.getValueCount();i++) {
					Integer currentId = new Double(newToolList.getAttribute("Id").getValueAt(i).toString()).intValue();
					
					// Go through all installed tools and check if they are equal to new tools
					boolean found = false;
					for(int j=0;j<installedToolList.getValueCount();j++) {
						
						// Only if tool is not already found, it must be added to the file
						if(new Integer(new Double(installedToolList.getAttribute("Id").getValueAt(j).toString()).intValue()).equals(currentId)) {
							found = true;
							listOfToolsWhichAreAlreadyInstalledAndAreRequiredByThisPlugin.add(currentId);
						}
					}
					if(!found) {
						newToolsMap.put(currentId, i);
						
						// Copy tool files to AMUSE tool folder
						AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"Installing " + 
								newToolList.getAttribute("Name").getValueAt(currentId).toString() + "...");
						
						String toolFolder = newToolList.getAttribute("Folder").getValueAt(currentId).toString();
						File destination = new File(System.getenv("AMUSEHOME") + "/tools/"+ toolFolder);
						if(destination.exists()) {
							throw new SchedulerException("Tool folder " + destination.getAbsolutePath() + 
								" exists; please remove corresponding tool/plugins at first!");
						} else {
							destination.mkdirs();
						}
						
						FileOperations.copy(new File(pathToPluginFolder + "/" + toolFolder),destination,Level.INFO);
						
						AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,".." +  
								newToolList.getAttribute("Name").getValueAt(currentId).toString() + " is successfully installed");
					}
				}
				
				// Overwrite the current AMUSE tool list with the new updated version
				// TODO Better way could be to create a corresponding data set (ToolListSet) and add some functionality
				// e.g. comments for attributes etc. which will be written also!
				DataOutputStream values_writer = new DataOutputStream(new FileOutputStream(new File(System.getenv("AMUSEHOME") + 
						"/config/toolTableUpdated.arff")));
				String sep = System.getProperty("line.separator");	
				values_writer.writeBytes("% Table with installed tools" + sep);
				values_writer.writeBytes("@RELATION tools" + sep + sep);
				values_writer.writeBytes("% Unique tool Id" + sep);
				values_writer.writeBytes("@ATTRIBUTE Id NUMERIC" + sep);
				values_writer.writeBytes("% Tool name" + sep);
				values_writer.writeBytes("@ATTRIBUTE Name STRING" + sep);
				values_writer.writeBytes("% Tool folder" + sep);
				values_writer.writeBytes("@ATTRIBUTE Folder STRING" + sep);
				values_writer.writeBytes("% Installed version description" + sep);
				values_writer.writeBytes("@ATTRIBUTE VersionDescription STRING" + sep);
				values_writer.writeBytes("% Plugins which require this tool" + sep);
				values_writer.writeBytes("@ATTRIBUTE PluginList STRING" + sep + sep);
				values_writer.writeBytes("@DATA" + sep);
				
				ArrayList<Integer> sortedInstalledToolIds = new ArrayList<Integer>(installedToolsMap.keySet());
				Collections.sort(sortedInstalledToolIds);
				ArrayList<Integer> sortedNewToolIds = new ArrayList<Integer>(newToolsMap.keySet());
				Collections.sort(sortedNewToolIds);
				int posNew = 0;
				
				// Go through all installed tools (sorted due to their ids) and check if a line with the new tool description
				// or a line with the previously installed tool description must be written
				for(int posInstalled = 0;posInstalled<sortedInstalledToolIds.size();posInstalled++) {
					
					int idOfInstalledTool = sortedInstalledToolIds.get(posInstalled);
					int idOfNewTool;
					if(posNew >= sortedNewToolIds.size()) {
						idOfNewTool = Integer.MAX_VALUE;
					} else {
						idOfNewTool = sortedNewToolIds.get(posNew);
					}
					
					// Write the previously installed tool data
					if(idOfInstalledTool < idOfNewTool) {
						values_writer.writeBytes(idOfInstalledTool + ", \"" + 
							installedToolList.getAttribute("Name").getValueAt(installedToolsMap.get(idOfInstalledTool)).toString() + "\", \"" + 
							installedToolList.getAttribute("Folder").getValueAt(installedToolsMap.get(idOfInstalledTool)).toString() + "\", \"" +
							installedToolList.getAttribute("VersionDescription").getValueAt(installedToolsMap.get(idOfInstalledTool)).toString() + "\", \""); 
						if(listOfToolsWhichAreAlreadyInstalledAndAreRequiredByThisPlugin.contains(idOfInstalledTool)) {
							values_writer.writeBytes(
									installedToolList.getAttribute("PluginList").getValueAt(installedToolsMap.get(idOfInstalledTool)).toString() + 
									" " + installProperties.getProperty("ID") + "\"" + sep);
						} else {
							values_writer.writeBytes(
									installedToolList.getAttribute("PluginList").getValueAt(installedToolsMap.get(idOfInstalledTool)).toString() + "\"" + sep);
						}
					}
					
					// Write the data for the new tool
					else {
						values_writer.writeBytes(idOfNewTool + ", \"" + 
								newToolList.getAttribute("Name").getValueAt(newToolsMap.get(idOfNewTool)).toString() + "\", \"" + 
								newToolList.getAttribute("Folder").getValueAt(newToolsMap.get(idOfNewTool)).toString() + "\", \"" +
								newToolList.getAttribute("VersionDescription").getValueAt(newToolsMap.get(idOfNewTool)).toString() + "\", \"" + 
								installProperties.getProperty("ID") + "\"" + sep);
						posNew++;
						posInstalled--; // This position remains
					}
				}
				
				// Write the data for the remaining new tools which have higher ids than ids of previously installed tools
				for(;posNew<sortedNewToolIds.size();posNew++) {
					int idOfNewTool = sortedNewToolIds.get(posNew);
					values_writer.writeBytes(idOfNewTool + ", \"" + 
							newToolList.getAttribute("Name").getValueAt(newToolsMap.get(idOfNewTool)).toString() + "\", \"" + 
							newToolList.getAttribute("Folder").getValueAt(newToolsMap.get(idOfNewTool)).toString() + "\", \"" +
							newToolList.getAttribute("VersionDescription").getValueAt(newToolsMap.get(idOfNewTool)).toString() + "\", \"" + 
							installProperties.getProperty("ID") + "\"" + sep);
				}
				
				values_writer.close();
				
				// Replace toolTable with toolTableUpdated
				FileOperations.move(new File(System.getenv("AMUSEHOME") + "/config/toolTableUpdated.arff"), 
						new File(System.getenv("AMUSEHOME") + "/config/toolTable.arff"));
				
			} catch(IOException e) {
				throw new SchedulerException("Could not update the list with installed tools: " + e.getMessage());
			}
		}
		
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"..update finished");
	}
	
	/**
	 * Updates algorithm tables from AMUSEHOME/config folder (features, processing and classification methods etc.)
	 * TODO Since currently available plugins are only for feature extraction, not all tables are updated!
	 */
	private void updateAlgorithmTables() throws SchedulerException {
		File[] files = new File(pathToPluginFolder).listFiles();
		for(int i=0;i<files.length;i++) {
			if(files[i].getName().equals("featureTable.arff")) {
				updateFeatureTable();
			} else if(files[i].getName().equals("featureExtractorToolTable.arff")) {
				updateFeatureExtractorToolTable();
			} else if(files[i].getName().equals("classifierAlgorithmTable.arff")) {
				// TODO updateClassifierAlgorithmTable();
			} else if(files[i].getName().equals("classifierPreprocessingAlgorithmTable.arff")) {
				// TODO updateClassifierPreprocessingAlgorithmTable();
			} else if(files[i].getName().equals("metricTable.arff")) {
				// TODO updateMetricTable();
			} else if(files[i].getName().equals("processorAlgorithmTable.arff")) {
				// TODO updateProcessorAlgorithmTable();
			} else if(files[i].getName().equals("processorConversionAlgorithmTable.arff")) {
				// TODO updateProcessorConversionAlgorithmTable();
			} else if(files[i].getName().equals("validationAlgorithmTable.arff")) {
				// TODO updateValidationAlgorithmTable();
			} else if(files[i].getName().equals("optimizerAlgorithmTable.arff")) {
				// TODO updateOptimizerAlgorithmTable();
			} 
		}
	}
	
	/**
	 * Updates featureTable.arff with the sort order according to their ids
	 */
	private void updateFeatureTable() throws SchedulerException {
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"Starting feature list update...");
		
		File featureList = new File(pathToPluginFolder + "/featureTable.arff");
		
		DataSetAbstract newFeatureList;
		DataSetAbstract installedFeatureList;
			
		// Key: feature id; value: position in the DataSet
		HashMap<Integer,Integer> newFeatureMap = new HashMap<Integer,Integer>();
		HashMap<Integer,Integer> installedFeatureMap = new HashMap<Integer,Integer>();
		try {
			newFeatureList = new ArffDataSet(featureList);
			installedFeatureList = new ArffDataSet(new File(System.getenv("AMUSEHOME") + "/config/featureTable.arff"));
				
			// Go through all installed features
			for(int j=0;j<installedFeatureList.getValueCount();j++) {
				installedFeatureMap.put(new Double(installedFeatureList.getAttribute("Id").getValueAt(j).toString()).intValue(), j);
			}	
				
			// Go through all new features
			for(int i=0;i<newFeatureList.getValueCount();i++) {
				Integer currentId = new Double(newFeatureList.getAttribute("Id").getValueAt(i).toString()).intValue();
					
				// Go through all installed features and check if they are equal to new features
				boolean found = false;
				for(int j=0;j<installedFeatureList.getValueCount();j++) {
						
					// Only if feature is not already found, it must be added to the file
					if(new Integer(new Double(installedFeatureList.getAttribute("Id").getValueAt(j).toString()).intValue()).equals(currentId)) {
						found = true;
					}
				}
				if(!found) {
					newFeatureMap.put(currentId, i);
				}
 			}
				
			// Overwrite the current AMUSE feature list with the new updated version
			// TODO Better way could be to create a corresponding data set (FeatureListSet) and add some functionality
			// e.g. comments for attributes etc. which will be written also!
			DataOutputStream values_writer = new DataOutputStream(new FileOutputStream(new File(System.getenv("AMUSEHOME") + 
					"/config/featureTableUpdated.arff")));
			String sep = System.getProperty("line.separator");
			values_writer.writeBytes("% Table with all audio signal features available" + sep);
			values_writer.writeBytes("% for computation in Amuse. If you wish to use" + sep);
			values_writer.writeBytes("% some subset of features, please create a copy" + sep);
			values_writer.writeBytes("% of this file and leave only the features you" + sep);
			values_writer.writeBytes("% want to extract." + sep + sep);
			values_writer.writeBytes("@RELATION features" + sep + sep);
			values_writer.writeBytes("% Unique feature ID" + sep);
			values_writer.writeBytes("@ATTRIBUTE Id NUMERIC" + sep);
			values_writer.writeBytes("% Feature description" + sep);
			values_writer.writeBytes("@ATTRIBUTE Description STRING" + sep);
			values_writer.writeBytes("% ID of tool to extract the feature (see extractorTable.arff)" + sep);
			values_writer.writeBytes("@ATTRIBUTE ExtractorId NUMERIC" + sep);
			values_writer.writeBytes("% Window size in samples" + sep);
			values_writer.writeBytes("@ATTRIBUTE WindowSize NUMERIC" + sep);
			values_writer.writeBytes("% Number of feature dimensions" + sep);
			values_writer.writeBytes("@ATTRIBUTE Dimensions NUMERIC" + sep);
			values_writer.writeBytes("% Indicates if the Attribute is suitable for Processing. (1 = True, 0 = False)" + sep);
			values_writer.writeBytes("@ATTRIBUTE IsSuitableForProcessing NUMERIC" + sep + sep);
			values_writer.writeBytes("@DATA" + sep + sep);
			values_writer.writeBytes("% Timbre features" + sep + sep);
						
			ArrayList<Integer> sortedInstalledFeatureIds = new ArrayList<Integer>(installedFeatureMap.keySet());
			Collections.sort(sortedInstalledFeatureIds);
			ArrayList<Integer> sortedNewFeatureIds = new ArrayList<Integer>(newFeatureMap.keySet());
			Collections.sort(sortedNewFeatureIds);
			int posRequired = 0;
			
			// For comments
			boolean harmonyFeaturesStarted = false;
			boolean tempoFeaturesStarted = false;
			boolean structureFeaturesStarted = false;
			
			// Go through all installed features (sorted due to their ids) and check if a line with the new feature description
			// or a line with the previously installed feature description must be written
			for(int posInstalled = 0;posInstalled<sortedInstalledFeatureIds.size();posInstalled++) {
				
				int idOfInstalledFeature = sortedInstalledFeatureIds.get(posInstalled);
				int idOfNewFeature;
				if(posRequired >= sortedNewFeatureIds.size()) {
					idOfNewFeature = Integer.MAX_VALUE;
				} else {
					idOfNewFeature = sortedNewFeatureIds.get(posRequired);
				}
				
				// Write some comments about feature groups
				if(idOfInstalledFeature >= 200 && idOfNewFeature >= 200 && !harmonyFeaturesStarted) {
					values_writer.writeBytes(sep + "% Harmony and melody features" + sep + sep);
					harmonyFeaturesStarted = true;
				}
				if(idOfInstalledFeature >= 400 && idOfNewFeature >= 400 && !tempoFeaturesStarted) {
					values_writer.writeBytes(sep + "% Tempo features" + sep + sep);
					tempoFeaturesStarted = true;
				}
				if(idOfInstalledFeature >= 600 && idOfNewFeature >= 600 && !structureFeaturesStarted) {
					values_writer.writeBytes(sep + "% Structural features" + sep + sep);
					structureFeaturesStarted = true;
				}
				
				// Write the previously installed feature data
				if(idOfInstalledFeature < idOfNewFeature) {
					Double extractorId = new Double(installedFeatureList.getAttribute("ExtractorId").getValueAt(installedFeatureMap.get(idOfInstalledFeature)).toString());
					String extractorIdString = extractorId.isNaN() ? "?" : new Integer(extractorId.intValue()).toString(); 
					Double windowSize = new Double(installedFeatureList.getAttribute("WindowSize").getValueAt(installedFeatureMap.get(idOfInstalledFeature)).toString());
					String windowSizeString = windowSize.isNaN() ? "?" : new Integer(windowSize.intValue()).toString();
					Double dimensions = new Double(installedFeatureList.getAttribute("Dimensions").getValueAt(installedFeatureMap.get(idOfInstalledFeature)).toString());
					String dimensionsString = dimensions.isNaN() ? "?" : new Integer(dimensions.intValue()).toString();
					Double isSuitableForProcessing = new Double(installedFeatureList.getAttribute("IsSuitableForProcessing").getValueAt(installedFeatureMap.get(idOfInstalledFeature)).toString());
					String isSuitableForProcessingString = isSuitableForProcessing.isNaN() ? "?" : new Integer(isSuitableForProcessing.intValue()).toString();
					
					values_writer.writeBytes(idOfInstalledFeature + ", \"" + 
						installedFeatureList.getAttribute("Description").getValueAt(installedFeatureMap.get(idOfInstalledFeature)).toString() + "\", " + 
						extractorIdString + ", " + windowSizeString + ", " + dimensionsString + ", " + isSuitableForProcessingString + sep);
				}
				
				// Write the data for the new feature
				else {
					Double extractorId = new Double(newFeatureList.getAttribute("ExtractorId").getValueAt(newFeatureMap.get(idOfNewFeature)).toString());
					String extractorIdString = extractorId.isNaN() ? "?" : new Integer(extractorId.intValue()).toString(); 
					Double windowSize = new Double(newFeatureList.getAttribute("WindowSize").getValueAt(newFeatureMap.get(idOfNewFeature)).toString());
					String windowSizeString = windowSize.isNaN() ? "?" : new Integer(windowSize.intValue()).toString();
					Double dimensions = new Double(newFeatureList.getAttribute("Dimensions").getValueAt(newFeatureMap.get(idOfNewFeature)).toString());
					String dimensionsString = dimensions.isNaN() ? "?" : new Integer(dimensions.intValue()).toString();
					Double isSuitableForProcessing = new Double(newFeatureList.getAttribute("IsSuitableForProcessing").getValueAt(newFeatureMap.get(idOfNewFeature)).toString());
					String isSuitableForProcessingString = isSuitableForProcessing.isNaN() ? "?" : new Integer(isSuitableForProcessing.intValue()).toString();
					
					values_writer.writeBytes(idOfNewFeature + ", \"" + 
						newFeatureList.getAttribute("Description").getValueAt(newFeatureMap.get(idOfNewFeature)).toString() + "\", " + 
						extractorIdString + ", " + windowSizeString + ", " + dimensionsString + ", " + isSuitableForProcessingString + sep);
					posRequired++;
					posInstalled--; // This position remains
				}
			}
			
			// Write the data for the remaining new features which have higher ids than ids of previously installed features
			for(;posRequired<sortedNewFeatureIds.size();posRequired++) {
				int idOfNewFeature = sortedNewFeatureIds.get(posRequired);
				Double extractorId = new Double(newFeatureList.getAttribute("ExtractorId").getValueAt(newFeatureMap.get(idOfNewFeature)).toString());
				String extractorIdString = extractorId.isNaN() ? "?" : new Integer(extractorId.intValue()).toString(); 
				Double windowSize = new Double(newFeatureList.getAttribute("WindowSize").getValueAt(newFeatureMap.get(idOfNewFeature)).toString());
				String windowSizeString = windowSize.isNaN() ? "?" : new Integer(windowSize.intValue()).toString();
				Double dimensions = new Double(newFeatureList.getAttribute("Dimensions").getValueAt(newFeatureMap.get(idOfNewFeature)).toString());
				String dimensionsString = dimensions.isNaN() ? "?" : new Integer(dimensions.intValue()).toString();
				Double isSuitableForProcessing = new Double(newFeatureList.getAttribute("IsSuitableForProcessing").getValueAt(newFeatureMap.get(idOfNewFeature)).toString());
				String isSuitableForProcessingString = isSuitableForProcessing.isNaN() ? "?" : new Integer(isSuitableForProcessing.intValue()).toString();
				values_writer.writeBytes(idOfNewFeature + ", \"" + 
					newFeatureList.getAttribute("Description").getValueAt(newFeatureMap.get(idOfNewFeature)).toString() + "\", " + 
					extractorIdString + ", " + windowSizeString + ", " + dimensionsString + ", " + isSuitableForProcessingString + sep);
			}
			
			values_writer.close();
			
			// Replace featureTable with featureTableUpdated
			FileOperations.move(new File(System.getenv("AMUSEHOME") + "/config/featureTableUpdated.arff"), 
					new File(System.getenv("AMUSEHOME") + "/config/featureTable.arff"));
			
		} catch(IOException e) {
			throw new SchedulerException("Could not update the list with installed features: " + e.getMessage());
		}
		
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"..update finished");
	}
	
	/**
	 * Updates featureExtractorToolTable.arff with the sort order according to their ids
	 */
	private void updateFeatureExtractorToolTable() throws SchedulerException {
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"Starting feature extractors list update...");
		
		File featureExtractorList = new File(pathToPluginFolder + "/featureExtractorToolTable.arff");
		
		DataSetAbstract newFeatureExtractorList;
		DataSetAbstract installedFeatureExtractorList;
			
		// Key: feature extractor tool id; value: position in the DataSet
		HashMap<Integer,Integer> newFeatureExtractorMap = new HashMap<Integer,Integer>();
		HashMap<Integer,Integer> installedFeatureExtractorMap = new HashMap<Integer,Integer>();
		try {
			newFeatureExtractorList = new ArffDataSet(featureExtractorList);
			installedFeatureExtractorList = new ArffDataSet(new File(System.getenv("AMUSEHOME") + "/config/featureExtractorToolTable.arff"));
				
			// Go through all installed feature extractor tools
			for(int j=0;j<installedFeatureExtractorList.getValueCount();j++) {
				installedFeatureExtractorMap.put(new Double(installedFeatureExtractorList.getAttribute("Id").getValueAt(j).toString()).intValue(), j);
			}
			
			// Go through all new feature extractor tools
			for(int i=0;i<newFeatureExtractorList.getValueCount();i++) {
				Integer currentId = new Double(newFeatureExtractorList.getAttribute("Id").getValueAt(i).toString()).intValue();
					
				// Go through all installed feature extractors and check if they are equal to new feature extractors
				boolean found = false;
				for(int j=0;j<installedFeatureExtractorList.getValueCount();j++) {
						
					// Only if feature extractor is not already found, it must be added to the file
					if(new Integer(new Double(installedFeatureExtractorList.getAttribute("Id").getValueAt(j).toString()).intValue()).equals(currentId)) {
						found = true;
					}
				}
				if(!found) {
					newFeatureExtractorMap.put(currentId, i);
				}
			}
				
			// Overwrite the current AMUSE feature extractor list with the new updated version
			// TODO Better way could be to create a corresponding data set (FeatureExtractorListSet) and add some functionality
			// e.g. comments for attributes etc. which will be written also!
			DataOutputStream values_writer = new DataOutputStream(new FileOutputStream(new File(System.getenv("AMUSEHOME") + 
					"/config/featureExtractorToolTableUpdated.arff")));
			String sep = System.getProperty("line.separator");
			values_writer.writeBytes("% Feature extractors table" + sep);
			values_writer.writeBytes("@RELATION extractors" + sep + sep);
			values_writer.writeBytes("% Unique extractor ID" + sep);
			values_writer.writeBytes("@ATTRIBUTE Id NUMERIC" + sep);
			values_writer.writeBytes("% Extractor name" + sep);
			values_writer.writeBytes("@ATTRIBUTE Name STRING" + sep);
			values_writer.writeBytes("% Java class which runs extractor" + sep);
			values_writer.writeBytes("@ATTRIBUTE AdapterClass STRING" + sep);
			values_writer.writeBytes("% Extractor home folder (e.g. if an external tool is used)" + sep);
			values_writer.writeBytes("@ATTRIBUTE HomeFolder STRING" + sep);
			values_writer.writeBytes("% Extractor start script for adapter only if external tool is used (otherwise please set to -1)" + sep);
			values_writer.writeBytes("@ATTRIBUTE StartScript STRING" + sep);
			values_writer.writeBytes("% Base script for feature extraction" + sep);
			values_writer.writeBytes("@ATTRIBUTE InputBaseBatch STRING" + sep);
			values_writer.writeBytes("% Script for feature extraction (after the parameters / options were saved to base script)" + sep);
			values_writer.writeBytes("@ATTRIBUTE InputBatch STRING" + sep + sep);
			values_writer.writeBytes("@DATA" + sep);
							
			ArrayList<Integer> sortedInstalledFeatureExtractorIds = new ArrayList<Integer>(installedFeatureExtractorMap.keySet());
			Collections.sort(sortedInstalledFeatureExtractorIds);
			ArrayList<Integer> sortedNewFeatureExtractorIds = new ArrayList<Integer>(newFeatureExtractorMap.keySet());
			Collections.sort(sortedNewFeatureExtractorIds);
			int posRequired = 0;
			
			// Go through all installed feature extractors (sorted due to their ids) and check if a line with the new feature 
			// extractor description or a line with the previously installed feature extractor description must be written
			for(int posInstalled = 0;posInstalled<sortedInstalledFeatureExtractorIds.size();posInstalled++) {
				
				int idOfInstalledFeatureExtractor = sortedInstalledFeatureExtractorIds.get(posInstalled);
				int idOfNewFeatureExtractor;
				if(posRequired >= sortedNewFeatureExtractorIds.size()) {
					idOfNewFeatureExtractor = Integer.MAX_VALUE;
				} else {
					idOfNewFeatureExtractor = sortedNewFeatureExtractorIds.get(posRequired);
				}
				
				// Write the previously installed feature extractor data
				if(idOfInstalledFeatureExtractor < idOfNewFeatureExtractor) {
					values_writer.writeBytes(idOfInstalledFeatureExtractor + ", \"" + 
						installedFeatureExtractorList.getAttribute("Name").getValueAt(installedFeatureExtractorMap.get(idOfInstalledFeatureExtractor)).toString() + "\", \"" + 
						installedFeatureExtractorList.getAttribute("AdapterClass").getValueAt(installedFeatureExtractorMap.get(idOfInstalledFeatureExtractor)).toString() + "\", \"" +
						installedFeatureExtractorList.getAttribute("HomeFolder").getValueAt(installedFeatureExtractorMap.get(idOfInstalledFeatureExtractor)).toString() + "\", \"" +
						installedFeatureExtractorList.getAttribute("StartScript").getValueAt(installedFeatureExtractorMap.get(idOfInstalledFeatureExtractor)).toString() + "\", \"" +
						installedFeatureExtractorList.getAttribute("InputBaseBatch").getValueAt(installedFeatureExtractorMap.get(idOfInstalledFeatureExtractor)).toString() + "\", \"" +
						installedFeatureExtractorList.getAttribute("InputBatch").getValueAt(installedFeatureExtractorMap.get(idOfInstalledFeatureExtractor)).toString() + "\"" + sep);
				}
				
				// Write the data for the new feature
				else {
					values_writer.writeBytes(idOfNewFeatureExtractor + ", \"" + 
						newFeatureExtractorList.getAttribute("Name").getValueAt(newFeatureExtractorMap.get(idOfNewFeatureExtractor)).toString() + "\", \"" + 
						newFeatureExtractorList.getAttribute("AdapterClass").getValueAt(newFeatureExtractorMap.get(idOfNewFeatureExtractor)).toString() + "\", \"" +
						newFeatureExtractorList.getAttribute("HomeFolder").getValueAt(newFeatureExtractorMap.get(idOfNewFeatureExtractor)).toString() + "\", \"" +
						newFeatureExtractorList.getAttribute("StartScript").getValueAt(newFeatureExtractorMap.get(idOfNewFeatureExtractor)).toString() + "\", \"" +
						newFeatureExtractorList.getAttribute("InputBaseBatch").getValueAt(newFeatureExtractorMap.get(idOfNewFeatureExtractor)).toString() + "\", \"" +
						newFeatureExtractorList.getAttribute("InputBatch").getValueAt(newFeatureExtractorMap.get(idOfNewFeatureExtractor)).toString() + "\"" + sep);
					posRequired++;
					posInstalled--; // This position remains
				}
			}
			
			// Write the data for the remaining new features which have higher ids than ids of previously installed features
			for(;posRequired<sortedNewFeatureExtractorIds.size();posRequired++) {
				int idOfNewFeatureExtractor = sortedNewFeatureExtractorIds.get(posRequired);
				values_writer.writeBytes(idOfNewFeatureExtractor + ", \"" + 
						newFeatureExtractorList.getAttribute("Name").getValueAt(newFeatureExtractorMap.get(idOfNewFeatureExtractor)).toString() + "\", \"" + 
						newFeatureExtractorList.getAttribute("AdapterClass").getValueAt(newFeatureExtractorMap.get(idOfNewFeatureExtractor)).toString() + "\", \"" +
						newFeatureExtractorList.getAttribute("HomeFolder").getValueAt(newFeatureExtractorMap.get(idOfNewFeatureExtractor)).toString() + "\", \"" +
						newFeatureExtractorList.getAttribute("StartScript").getValueAt(newFeatureExtractorMap.get(idOfNewFeatureExtractor)).toString() + "\", \"" +
						newFeatureExtractorList.getAttribute("InputBaseBatch").getValueAt(newFeatureExtractorMap.get(idOfNewFeatureExtractor)).toString() + "\", \"" +
						newFeatureExtractorList.getAttribute("InputBatch").getValueAt(newFeatureExtractorMap.get(idOfNewFeatureExtractor)).toString() + "\"" + sep);
			}
			
			values_writer.close();
			
			// Replace featureExtractorTable with featureExtractorTableUpdated
			FileOperations.move(new File(System.getenv("AMUSEHOME") + "/config/featureExtractorToolTableUpdated.arff"), 
					new File(System.getenv("AMUSEHOME") + "/config/featureExtractorToolTable.arff"));
		} catch(IOException e) {
			throw new SchedulerException("Could not update the list with feature extractors: " + e.getMessage());
		}
		
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"..update finished");
	}
	
	/**
	 * Updates the Amuse plugin table
	 */
	private void updatePluginTable() throws SchedulerException {
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"Starting plugin list update...");
		
		DataSetAbstract installedPluginList;
			
		// Key: plugin id; value: position in the DataSet
		HashMap<Integer,Integer> installedPluginMap = new HashMap<Integer,Integer>();
		try {
			installedPluginList = new ArffDataSet(new File(System.getenv("AMUSEHOME") + "/config/pluginTable.arff"));
				
			// Go through all installed plugins
			for(int j=0;j<installedPluginList.getValueCount();j++) {
				installedPluginMap.put(new Double(installedPluginList.getAttribute("Id").getValueAt(j).toString()).intValue(), j);
			}
					
			// Overwrite the current AMUSE plugin list with the new updated version
			// TODO Better way could be to create a corresponding data set (ToolListSet) and add some functionality
			// e.g. comments for attributes etc. which will be written also!
			DataOutputStream values_writer = new DataOutputStream(new FileOutputStream(new File(System.getenv("AMUSEHOME") + 
					"/config/pluginTableUpdated.arff")));
			String sep = System.getProperty("line.separator");	
			values_writer.writeBytes("% Table with installed plugins" + sep);
			values_writer.writeBytes("@RELATION plugins" + sep + sep);
			values_writer.writeBytes("% Unique plugin Id" + sep);
			values_writer.writeBytes("@ATTRIBUTE Id NUMERIC" + sep);
			values_writer.writeBytes("% Plugin name" + sep);
			values_writer.writeBytes("@ATTRIBUTE Name STRING" + sep);
			values_writer.writeBytes("% Installed version description" + sep);
			values_writer.writeBytes("@ATTRIBUTE VersionDescription STRING" + sep + sep);
			values_writer.writeBytes("@DATA" + sep);
			
			ArrayList<Integer> sortedInstalledPluginIds = new ArrayList<Integer>(installedPluginMap.keySet());
			Collections.sort(sortedInstalledPluginIds);
			boolean newPluginAdded=false;
			int idOfNewPlugin = new Integer(installProperties.getProperty("ID"));
				
			// Go through all installed plugins (sorted due to their ids) and check if a line with the new tool description
			// or a line with the previously installed tool description must be written
			for(int posInstalled = 0;posInstalled<sortedInstalledPluginIds.size();posInstalled++) {
				int idOfInstalledPlugin = sortedInstalledPluginIds.get(posInstalled);
				
				// Write the previously installed plugin data
				if(idOfInstalledPlugin < idOfNewPlugin || newPluginAdded) {
					values_writer.writeBytes(idOfInstalledPlugin + ", \"" + 
						installedPluginList.getAttribute("Name").getValueAt(installedPluginMap.get(idOfInstalledPlugin)).toString() + "\", \"" + 
						installedPluginList.getAttribute("VersionDescription").getValueAt(installedPluginMap.get(idOfInstalledPlugin)).toString() + "\"" + sep);
				}
					
				// Write the data for the new plugin
				else if(!newPluginAdded){
					values_writer.writeBytes(idOfNewPlugin + ", \"" + installProperties.getProperty("NAME") + "\", \"" + 
						installProperties.getProperty("VERSION") + "\"" + sep);
					newPluginAdded = true;
					posInstalled--; // This position remains
				}
			}
				
			// Write the data for the new plugin if its id is higher than ids of previously installed plugins
			if(!newPluginAdded) {
				values_writer.writeBytes(idOfNewPlugin + ", \"" + installProperties.getProperty("NAME") + "\", \"" + 
						installProperties.getProperty("VERSION") + "\"" + sep);
			}
			values_writer.close();
			
			// Replace pluginTable with pluginTableUpdated
			FileOperations.move(new File(System.getenv("AMUSEHOME") + "/config/pluginTableUpdated.arff"), 
					new File(System.getenv("AMUSEHOME") + "/config/pluginTable.arff"));
				
		} catch(IOException e) {
			throw new SchedulerException("Could not update the list with installed plugins: " + e.getMessage());
		}
		
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"..update finished");
	}
	
	/**
	 * Saves some data for deinstallation of this plugin
	 */
	private void saveDataForDeinstallation() throws SchedulerException {
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"Saving data for deinstallation...");
		
		File destinationFolder = new File(System.getenv("AMUSEHOME") + "/config/plugininfo/" + installProperties.getProperty("ID"));
		if(!destinationFolder.exists()) {
			destinationFolder.mkdirs();
		}
		
		try {
			
			// Save the plugin properties
			FileOperations.copy(new File(pathToPluginFolder + "/plugin.properties"), new File(destinationFolder.getAbsolutePath() + "/plugin.properties"),Level.INFO);
			
			// Save the pluginManager.jar if it exists
			File fileToCopy = new File(pathToPluginFolder + "/pluginManager.jar");
			if(fileToCopy.exists()) {
				FileOperations.copy(fileToCopy,new File(destinationFolder.getAbsolutePath() + "/pluginManager.jar"),Level.INFO);
			}
			
			// Save the algorithm tables if they exist
			fileToCopy = new File(pathToPluginFolder + "/featureTable.arff");
			if(fileToCopy.exists()) {
				FileOperations.copy(fileToCopy,new File(destinationFolder.getAbsolutePath() + "/featureTable.arff"),Level.INFO);
			}
			fileToCopy = new File(pathToPluginFolder + "/featureExtractorToolTable.arff");
			if(fileToCopy.exists()) {
				FileOperations.copy(fileToCopy,new File(destinationFolder.getAbsolutePath() + "/featureExtractorToolTable.arff"),Level.INFO);
			}
			fileToCopy = new File(pathToPluginFolder + "/classifierAlgorithmTable.arff");
			if(fileToCopy.exists()) {
				FileOperations.copy(fileToCopy,new File(destinationFolder.getAbsolutePath() + "/classifierAlgorithmTable.arff"),Level.INFO);
			}
			fileToCopy = new File(pathToPluginFolder + "/metricTable.arff");
			if(fileToCopy.exists()) {
				FileOperations.copy(fileToCopy,new File(destinationFolder.getAbsolutePath() + "/metricTable.arff"),Level.INFO);
			}
			fileToCopy = new File(pathToPluginFolder + "/processorAlgorithmTable.arff");
			if(fileToCopy.exists()) {
				FileOperations.copy(fileToCopy,new File(destinationFolder.getAbsolutePath() + "/processorAlgorithmTable.arff"),Level.INFO);
			}
			fileToCopy = new File(pathToPluginFolder + "/processorConversionAlgorithmTable.arff");
			if(fileToCopy.exists()) {
				FileOperations.copy(fileToCopy,new File(destinationFolder.getAbsolutePath() + "/processorConversionAlgorithmTable.arff"),Level.INFO);
			}
			fileToCopy = new File(pathToPluginFolder + "/validationAlgorithmTable.arff");
			if(fileToCopy.exists()) {
				FileOperations.copy(fileToCopy,new File(destinationFolder.getAbsolutePath() + "/validationAlgorithmTable.arff"),Level.INFO);
			}
			fileToCopy = new File(pathToPluginFolder + "/optimizerAlgorithmTable.arff");
			if(fileToCopy.exists()) {
				FileOperations.copy(fileToCopy,new File(destinationFolder.getAbsolutePath() + "/optimizerAlgorithmTable.arff"),Level.INFO);
			}
		} catch(IOException e) {
			throw new SchedulerException("Could not save data for deinstallation: " + e.getMessage());
		}
		
		AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"..data saved");
	}
	
	/**
	 * Runs any further required routines which are required for the plugin. They must be implemented in the file
	 * pathToPluginFolder/pluginManager.jar
	 */
	private void runFurtherRoutines() throws SchedulerException {
		if(new File(pathToPluginFolder + "/pluginManager.jar").exists()) {
 			AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"Starting plugin-specific installation routines...");
		
			PluginInstallerInterface pluginInstaller = null;
			try {
				File installer = new File(pathToPluginFolder + "/pluginManager.jar");
				URL path = installer.toURI().toURL();
				JarClassLoader loader = new JarClassLoader(path);
				Class<?> c = loader.loadClass(loader.getMainClassName());
				pluginInstaller = (PluginInstallerInterface)c.newInstance(); 
				pluginInstaller.runInstallationRoutines(installProperties);
			} catch(Exception e) {
				e.printStackTrace();
				throw new SchedulerException("Could not initialize plugin installer: " + e.getMessage());
			}
			
			AmuseLogger.write(PluginInstaller.class.getName(),Level.INFO,"..installation routines finished");
		}
	}
 	
}
