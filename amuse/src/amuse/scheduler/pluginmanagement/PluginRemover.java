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
 * Creation date: 05.04.2010
 */
package amuse.scheduler.pluginmanagement;

import amuse.data.io.ArffDataSet;
import amuse.data.io.DataSetAbstract;
import amuse.interfaces.plugins.PluginInstallerInterface;
import amuse.interfaces.scheduler.SchedulerException;

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
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.log4j.Level;

/**
 * PluginRemover removes AMUSE plugins
 * 
 * @author Igor Vatolkin
 * @version $Id: $
 */
public class PluginRemover {
	
	/** Id of plugin to remove */
	private Integer pluginToRemoveId = -1;
	
	/** Plugin properties required for safe removal which must be in $AMUSEHOME$/config/plugininfo/$pluginToRemoveId$ */
	private Properties removeProperties = null;
	
	/** Different possible version states */
	public enum VersionState {
		ALPHA, BETA, RC, STABLE
	};
	
	/**
	 * Standard constructor
	 * @param pluginToRemoveId Id of the plugin which should be removed
	 */
	public PluginRemover(Integer pluginToRemoveId) {
		this.pluginToRemoveId = new Integer(pluginToRemoveId);
		this.removeProperties = new Properties();
	}
	
	/**
	 * Removes the plugin
	 */
	public void removePlugin() throws SchedulerException {
		
		AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"Starting plugin removal..........");
		
		try {
			FileInputStream propertiesInput = new FileInputStream(System.getenv("AMUSEHOME") + "/config/plugininfo/" +
					pluginToRemoveId.toString() + "/plugin.properties");
			this.removeProperties.load(propertiesInput);
		} catch(IOException e) {
			throw new SchedulerException("Could not load the plugin properties: " + e.getMessage());
		}
		
		// -------------------------------------------------------
		// (1) Is this plugin installed? (check pluginTable.arff). 
		//     If no, throw SchedulerException
		// -------------------------------------------------------
		checkPluginInstallationState();
		
		// ----------------------------------------------------
		// (2) Update pluginTable.arff removing the plugin data
		// ----------------------------------------------------
		updatePluginTable();
		
		// ----------------------------------------------------------------------------------
		// (3) Remove plugin JAR (name given in plugin.properties) from AMUSEHOME/lib/plugins
		// ----------------------------------------------------------------------------------
		removePluginJar();
		
		// -------------------------------------------------------------------
		// (4) Remove AMUSE tools which has been required for this plugin only
		// -------------------------------------------------------------------
		removeTools();
		
		// ---------------------------------------------------------------------------
		// (5) Search in pathToPluginFolder for AMUSE config arffs (featureTable) etc.
		//     If any is found, update the corresponding AMUSE config arffs 
		// ---------------------------------------------------------------------------
		updateAlgorithmTables();
		
		// -----------------------------------------------------------------------------------------
		// (6) Run any plugin-specific deinstallation routines if pluginManager.jar is in the folder
		// -----------------------------------------------------------------------------------------
		runFurtherRoutines();
		
		// -------------------------------------------------------------------------
		// (7) Remove the deinstallation data (plugin.properties, installer.jar etc.
		//     The destination folder is AMUSEHOME/config/plugininfo/$PLUGIN_ID$
		// -------------------------------------------------------------------------
		removeDataForDeinstallation();

		AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"..........plugin succesfully removed");
	}
	
	/**
	 * Checks if this plugin has been installed
	 */
	private void checkPluginInstallationState() throws SchedulerException {
		AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"Starting plugin state check...");
		
		DataSetAbstract installedPluginList;
		try {
			installedPluginList = new ArffDataSet(new File(System.getenv("AMUSEHOME") + "/config/pluginTable.arff"));
		} catch(IOException e) {
			throw new SchedulerException("Could not load the list with installed plugins: " + e.getMessage());
		}
		
		boolean found = false;
		for(int i=0;i<installedPluginList.getValueCount();i++) {
			if(installedPluginList.getAttribute("Id").getValueAt(i).equals(new Double(pluginToRemoveId))) {
				found = true; break;
			}
		}
		
		if(!found) {
			throw new SchedulerException("Plugin is not installed!");
		}
		
		AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"..check passed (plugin installed)");
	}
	
	/**
	 * Updates the Amuse plugin table
	 */
	private void updatePluginTable() throws SchedulerException {
		AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"Starting plugin list update...");
		
		DataSetAbstract installedPluginList;
		try {
			installedPluginList = new ArffDataSet(new File(System.getenv("AMUSEHOME") + "/config/pluginTable.arff"));
				
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
			
			// Go through all installed plugins (sorted due to their ids) and remove the corresponding line
			for(int i = 0;i<installedPluginList.getValueCount();i++) {
				int idOfInstalledPlugin = new Double(installedPluginList.getAttribute("Id").getValueAt(i).toString()).intValue();
				
				// Write the previously installed plugin data
				if(idOfInstalledPlugin != pluginToRemoveId) {
					values_writer.writeBytes(idOfInstalledPlugin + ", \"" + 
						installedPluginList.getAttribute("Name").getValueAt(i).toString() + "\", \"" + 
						installedPluginList.getAttribute("VersionDescription").getValueAt(i).toString() + "\"" + sep);
				}
			}
				
			values_writer.close();
			
			// Replace pluginTable with pluginTableUpdated
			FileOperations.move(new File(System.getenv("AMUSEHOME") + "/config/pluginTableUpdated.arff"), 
					new File(System.getenv("AMUSEHOME") + "/config/pluginTable.arff"));
				
		} catch(IOException e) {
			throw new SchedulerException("Could not update the list with installed plugins: " + e.getMessage());
		}
		
		AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"..update finished");
	}
	
	/**
	 * Remove plugin JAR (name given in plugin.properties) from AMUSEHOME/lib/plugins
	 */
	private void removePluginJar() throws SchedulerException {
		AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"Deleting the plugin jar...");
		
		File pathToPluginJar = new File(System.getenv("AMUSEHOME") + "/lib/plugins/" + removeProperties.getProperty("PLUGIN_JAR"));
		
		// Remove plugin jar
		if(pathToPluginJar.exists()) {
			boolean isRemoved = FileOperations.delete(pathToPluginJar, Level.INFO);
			
			if(!isRemoved) {
				throw new SchedulerException("Could not remove the plugin jar!");
			}
		}
		
		AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"..deleting finished");
	}
	
	/**
	 * Removes AMUSE tools which has been required for this plugin only and updates toolTable.arff
	 */
	private void removeTools() throws SchedulerException {
		AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"Starting tool removal and tool list update...");
		
		try {
			DataSetAbstract installedToolList = new ArffDataSet(new File(System.getenv("AMUSEHOME") + "/config/toolTable.arff"));
				
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
				
			for(int i=0;i<installedToolList.getValueCount();i++) {
				StringTokenizer pluginsForCurrentTool = new StringTokenizer(installedToolList.getAttribute("PluginList").getValueAt(i).toString());
				StringBuffer updatedPluginsForCurrentTool = new StringBuffer();
				
				while(pluginsForCurrentTool.hasMoreTokens()) {
					String pluginId = pluginsForCurrentTool.nextToken();
					
					// Add the plugin only if it not the current
					if(!new Integer(pluginId).equals(new Integer(removeProperties.getProperty("ID")))) {
						updatedPluginsForCurrentTool.append(pluginId + " ");
					}
				}
				
				// Does any other plugin exist which require this tool?
				if(updatedPluginsForCurrentTool.toString().length() > 0) {

					values_writer.writeBytes(new Double(installedToolList.getAttribute("Id").getValueAt(i).toString()).intValue() + ", \"" + 
							installedToolList.getAttribute("Name").getValueAt(i).toString() + "\", \"" + 
							installedToolList.getAttribute("Folder").getValueAt(i).toString() + "\", \"" +
							installedToolList.getAttribute("VersionDescription").getValueAt(i).toString() + "\", \"" + 
							// Omit the last " " in the plugin list
							updatedPluginsForCurrentTool.toString().substring(0,updatedPluginsForCurrentTool.toString().length()-1) + "\"" + sep); 
				} else {
					
					// Remove the tool folder with all contents
					FileOperations.delete(new File(System.getenv("AMUSEHOME") + "/tools/" + 
							installedToolList.getAttribute("Folder").getValueAt(i).toString()), true, Level.INFO);
				}
					
			}
			
			values_writer.close();
			
			// Replace toolTable with toolTableUpdated
			FileOperations.move(new File(System.getenv("AMUSEHOME") + "/config/toolTableUpdated.arff"), 
					new File(System.getenv("AMUSEHOME") + "/config/toolTable.arff"));
		} catch(IOException e) {
			throw new SchedulerException("Could not remove the tools: " + e.getMessage());
		}
		
		AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"..removal and update finished");
	}
	
	/**
	 * Updates algorithm tables from AMUSEHOME/config folder (features, processing and classification methods etc.)
	 * TODO Since currently available plugins are only for feature extraction, not all tables are updated!
	 */
	private void updateAlgorithmTables() throws SchedulerException {
		File[] files = new File(System.getenv("AMUSEHOME") + "/config/plugininfo/" + removeProperties.getProperty("ID")).listFiles();
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
		AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"Starting feature list update...");
		
		try {
		
			// Set with features to remove
			DataSetAbstract featureSet = new ArffDataSet(new File(System.getenv("AMUSEHOME") + "/config/plugininfo/" + removeProperties.getProperty("ID") + 
					"/featureTable.arff"));
			
			// List with ids of feature to remove
			ArrayList<Integer> sortedFeatureIds = new ArrayList<Integer>(featureSet.getValueCount());
			for(int i=0;i<featureSet.getValueCount();i++) {
				sortedFeatureIds.add(new Double(featureSet.getAttribute("Id").getValueAt(i).toString()).intValue());
			}
			Collections.sort(sortedFeatureIds);
			
			// Set with all installed features
			DataSetAbstract installedFeatureSet = new ArffDataSet(new File(System.getenv("AMUSEHOME") + "/config/featureTable.arff"));
			
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
			
			// For comments
			boolean harmonyFeaturesStarted = false;
			boolean tempoFeaturesStarted = false;
			boolean structureFeaturesStarted = false;
			
			// Go through all installed features 
			for(int i=0;i<installedFeatureSet.getValueCount();i++) {
				
				int idOfInstalledFeature = new Double(installedFeatureSet.getAttribute("Id").getValueAt(i).toString()).intValue();
				
				// Write some comments about feature groups
				if(idOfInstalledFeature >= 200 && !harmonyFeaturesStarted) {
					values_writer.writeBytes(sep + "% Harmony and melody features" + sep + sep);
					harmonyFeaturesStarted = true;
				}
				if(idOfInstalledFeature >= 400 && !tempoFeaturesStarted) {
					values_writer.writeBytes(sep + "% Tempo features" + sep + sep);
					tempoFeaturesStarted = true;
				}
				if(idOfInstalledFeature >= 600 && !structureFeaturesStarted) {
					values_writer.writeBytes(sep + "% Structural features" + sep + sep);
					structureFeaturesStarted = true;
				}
				
				// Save this feature only if it is not extracted by this plugin (which is being deleted)
				if(!sortedFeatureIds.contains(idOfInstalledFeature)) {
					Double extractorId = new Double(installedFeatureSet.getAttribute("ExtractorId").getValueAt(i).toString());
					String extractorIdString = extractorId.isNaN() ? "?" : new Integer(extractorId.intValue()).toString(); 
					Double windowSize = new Double(installedFeatureSet.getAttribute("WindowSize").getValueAt(i).toString());
					String windowSizeString = windowSize.isNaN() ? "?" : new Integer(windowSize.intValue()).toString();
					Double dimensions = new Double(installedFeatureSet.getAttribute("Dimensions").getValueAt(i).toString());
					String dimensionsString = dimensions.isNaN() ? "?" : new Integer(dimensions.intValue()).toString();
					Double isSuitableForProcessing = new Double(installedFeatureSet.getAttribute("IsSuitableForProcessing").getValueAt(i).toString());
					String isSuitableForProcessingString = isSuitableForProcessing.isNaN() ? "?" : new Integer(isSuitableForProcessing.intValue()).toString();
					values_writer.writeBytes(idOfInstalledFeature + ", \"" + 
							installedFeatureSet.getAttribute("Description").getValueAt(i).toString() + "\", " + 
						extractorIdString + ", " + windowSizeString + ", " + dimensionsString + ", " + isSuitableForProcessingString + sep);
				}
			}
			
			values_writer.close();
			
			// Replace featureTable with featureTableUpdated
			FileOperations.move(new File(System.getenv("AMUSEHOME") + "/config/featureTableUpdated.arff"), 
					new File(System.getenv("AMUSEHOME") + "/config/featureTable.arff"));
			
		} catch(IOException e) {
			throw new SchedulerException("Could not update the list with installed features: " + e.getMessage());
		}
		
		AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"..update finished");
	}
	
	/**
	 * Updates featureExtractorToolTable.arff with the sort order according to their ids
	 */
	private void updateFeatureExtractorToolTable() throws SchedulerException {
		AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"Starting feature extractor list update...");
		
		try {
		
			// Set with features to remove
			DataSetAbstract featureExtractorSet = new ArffDataSet(new File(System.getenv("AMUSEHOME") + "/config/plugininfo/" + 
					removeProperties.getProperty("ID") + "/featureExtractorToolTable.arff"));
			
			// List with ids of feature extractors to remove
			ArrayList<Integer> sortedExtractorIds = new ArrayList<Integer>(featureExtractorSet.getValueCount());
			for(int i=0;i<featureExtractorSet.getValueCount();i++) {
				sortedExtractorIds.add(new Double(featureExtractorSet.getAttribute("Id").getValueAt(i).toString()).intValue());
			}
			Collections.sort(sortedExtractorIds);
			
			// Set with all installed feature extractors
			DataSetAbstract installedExtractorSet = new ArffDataSet(new File(System.getenv("AMUSEHOME") + "/config/featureExtractorToolTable.arff"));
			
			// Overwrite the current AMUSE feature list with the new updated version
			// TODO Better way could be to create a corresponding data set (FeatureListSet) and add some functionality
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
			
			// Go through all installed feature extractors 
			for(int i=0;i<installedExtractorSet.getValueCount();i++) {
				
				int idOfInstalledExtractor = new Double(installedExtractorSet.getAttribute("Id").getValueAt(i).toString()).intValue();
				
				// Save this extractor only if it is not installed by this plugin (which is being deleted)
				if(!sortedExtractorIds.contains(idOfInstalledExtractor)) {
					values_writer.writeBytes(idOfInstalledExtractor + ", \"" + 
						installedExtractorSet.getAttribute("Name").getValueAt(i).toString() + "\", \"" + 
						installedExtractorSet.getAttribute("AdapterClass").getValueAt(i).toString() + "\", \"" +
						installedExtractorSet.getAttribute("HomeFolder").getValueAt(i).toString() + "\", \"" +
						installedExtractorSet.getAttribute("StartScript").getValueAt(i).toString() + "\", \"" +
						installedExtractorSet.getAttribute("InputBaseBatch").getValueAt(i).toString() + "\", \"" +
						installedExtractorSet.getAttribute("InputBatch").getValueAt(i).toString() + "\"" + sep);
				}
			}
			
			values_writer.close();
			
			// Replace featureExtractorTable with featureExtractorTableUpdated
			FileOperations.move(new File(System.getenv("AMUSEHOME") + "/config/featureExtractorToolTableUpdated.arff"), 
					new File(System.getenv("AMUSEHOME") + "/config/featureExtractorToolTable.arff"));
			
		} catch(IOException e) {
			throw new SchedulerException("Could not update the list with installed feature extractors: " + e.getMessage());
		}
		
		AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"..update finished");
	}

	/**
	 * Runs any further required routines which are required for the plugin. They must be implemented in the file
	 * pathToPluginFolder/pluginManager.jar
	 */
	private void runFurtherRoutines() throws SchedulerException {
		if(new File(System.getenv("AMUSEHOME") + "/config/plugininfo/" + removeProperties.getProperty("ID") + "/pluginManager.jar").exists()) {
 			AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"Starting plugin-specific deinstallation routines...");
		
			PluginInstallerInterface pluginInstaller = null;
			try {
				File remover = new File(System.getenv("AMUSEHOME") + "/config/plugininfo/" + removeProperties.getProperty("ID") + "/pluginManager.jar");
				URL path = remover.toURI().toURL();
				JarClassLoader loader = new JarClassLoader(path);
				Class<?> c = loader.loadClass(loader.getMainClassName());
				pluginInstaller = (PluginInstallerInterface)c.newInstance(); 
				pluginInstaller.runDeinstallationRoutines(System.getenv("AMUSEHOME") + "/config/plugininfo/" + removeProperties.getProperty("ID"));
			} catch(Exception e) {
				e.printStackTrace();
				throw new SchedulerException("Could not initialize plugin deinstaller: " + e.getMessage());
			}
			
			AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"..deinstallation routines finished");
		}
	}
	
	/**
	 * Remove the deinstallation data of this plugin
	 */
	private void removeDataForDeinstallation() throws SchedulerException {
		AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"Removing the plugin deinstallation data...");
		
		FileOperations.delete(new File(System.getenv("AMUSEHOME") + "/config/plugininfo/" + removeProperties.getProperty("ID")), true, Level.INFO);
		
		AmuseLogger.write(PluginRemover.class.getName(),Level.INFO,"..data removed");
	}
	


}
