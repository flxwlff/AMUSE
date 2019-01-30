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
 * Creation date: 16.01.2008
 */
package amuse.nodes.validator.methods;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.log4j.Level;

import weka.core.Attribute;
import weka.core.Instance;
import weka.core.converters.ArffLoader;
import amuse.data.ClassificationType;
import amuse.data.MeasureTable;
import amuse.interfaces.nodes.NodeException;
import amuse.interfaces.nodes.methods.AmuseTask;
import amuse.nodes.classifier.ClassificationConfiguration;
import amuse.nodes.classifier.ClassifierNodeScheduler;
import amuse.nodes.classifier.interfaces.ClassifiedSongPartitions;
import amuse.nodes.validator.ValidationConfiguration;
import amuse.nodes.validator.ValidatorNodeScheduler;
import amuse.nodes.validator.interfaces.ClassificationQualityMeasureCalculatorInterface;
import amuse.nodes.validator.interfaces.DataReductionMeasureCalculatorInterface;
import amuse.nodes.validator.interfaces.MeasureCalculatorInterface;
import amuse.nodes.validator.interfaces.ValidationMeasure;
import amuse.nodes.validator.interfaces.ValidationMeasureDouble;
import amuse.nodes.validator.interfaces.ValidatorInterface;
import amuse.preferences.AmusePreferences;
import amuse.preferences.KeysStringValue;
import amuse.util.AmuseLogger;

/**
 * Performs n-fold cross-validation
 * 
 * @author Igor Vatolkin
 * @version $Id: SingleEvaluator.java 243 2018-09-07 14:18:30Z frederik-h $
 */
public class SingleEvaluator extends AmuseTask implements ValidatorInterface {
	
	/** Measure calculator used by this validator */
	private ArrayList<MeasureCalculatorInterface> measureCalculators = new ArrayList<MeasureCalculatorInterface>();
	
	/** Ids of measures to calculate */
	private ArrayList<Integer> measureIds = new ArrayList<Integer>();
	
	/** Path to a single model file or folder with several models which should be evaluated */
	private String pathToModelFile = null;
	
	/**
	 * Performs validation of the given model(s)
	 */
	public void validate() throws NodeException {
		
		// --------------------------------
		// (I) Configure measure calculators
		// --------------------------------
		try {
			configureMeasureCalculators();
		} catch(NodeException e) {
			throw e;
		}
		
		// -----------------------
		// (II) Perform evaluation
		// -----------------------
		try {
			performEvaluation();
		} catch(NodeException e) {
			throw e;
		}
	}
	
	/**
	 * Configures measure calculators
	 * @throws NodeException
	 */
	private void configureMeasureCalculators() throws NodeException {
		
		// TODO Support measure calculators which use some parameters (like F-Measure) -> similar to algorithms 
		try {
			MeasureTable mt = ((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).getMeasures();
			for(int i=0;i<mt.size();i++) {
				
				// Set measure method properties
				Class<?> measureMethod = Class.forName(mt.get(i).getMeasureClass());
				MeasureCalculatorInterface vmc = (MeasureCalculatorInterface)measureMethod.newInstance();
				this.measureCalculators.add(vmc);
				this.measureIds.add(mt.get(i).getID());
				if(vmc instanceof ClassificationQualityMeasureCalculatorInterface) {
					if(mt.get(i).isPartitionLevelSelected()) {
						((ClassificationQualityMeasureCalculatorInterface)vmc).setPartitionLevel(true);
					} 
					if(mt.get(i).isSongLevelSelected()) {
						((ClassificationQualityMeasureCalculatorInterface)vmc).setSongLevel(true);
					}
				}
			}
		} catch(Exception e) {
			throw new NodeException("Configuration of measure method for validation failed: " + e.getMessage());
		}
		
		// Check if any measure calculators are loaded
		if(this.measureCalculators.size() == 0) {
			throw new NodeException("No measure method could be loaded for validation");
		}
	}
	
	/**
	 * Performs model evaluation
	 * @throws NodeException
	 */
	private void performEvaluation() throws NodeException {
		
		// List of all models to evaluate
		ArrayList<File> modelsToEvaluate = new ArrayList<File>();
		File modelFile = new File(pathToModelFile);
		
		// If a folder is given, search for all models
		if(modelFile.isDirectory()) {
			File[] files = modelFile.listFiles();
			
			// Go through all files in the given directory
			for(int i=0;i<files.length;i++) {
				if(files[i].isFile() && files[i].toString().endsWith(".mod")) {
					modelsToEvaluate.add(files[i]);
				}
			}
		}
		// If a single model file is given, load it to the list
		else {
			modelsToEvaluate.add(modelFile);
		}
		AmuseLogger.write(this.getClass().getName(), Level.INFO, modelsToEvaluate.size() + " model(s) will be evaluated");
		
		// Validation measures are saved in a list (for each run)
		ArrayList<ArrayList<ValidationMeasure>> measuresForEveryModel = new ArrayList<ArrayList<ValidationMeasure>>();
		
		// Go through all models which should be evaluated
		for(int i=0;i<modelsToEvaluate.size();i++) { 
			
			// Classify the music input with the current model
			ArrayList<ClassifiedSongPartitions> predictedSongs = new ArrayList<ClassifiedSongPartitions>();
			ClassificationConfiguration cConf = null;
			cConf = new ClassificationConfiguration(
				((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).getInputToValidate(),
				ClassificationConfiguration.InputSourceType.READY_INPUT,
				((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).getProcessedFeaturesModelName(), 
				((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).getClassificationAlgorithmDescription(),
				((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).getCategoriesToClassify(), ((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).getFeaturesToIgnore(), ((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).getClassificationType(), ((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).isFuzzy(), 0,
				this.correspondingScheduler.getHomeFolder() + File.separator + "input" + File.separator + "task_" + this.correspondingScheduler.getTaskId() + File.separator + "result.arff");
			cConf.setPathToInputModel(modelsToEvaluate.get(i).getAbsolutePath());
				
			ClassifierNodeScheduler cs = new ClassifierNodeScheduler(this.correspondingScheduler.getHomeFolder() + File.separator + "input" + File.separator + "task_" + this.correspondingScheduler.getTaskId());
			cs.setCleanInputFolder(false);
			cConf.setProcessedFeatureDatabase(((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).getProcessedFeatureDatabase());
			predictedSongs = cs.proceedTask(this.correspondingScheduler.getHomeFolder(), this.correspondingScheduler.getTaskId(), cConf, false);
			
			// Calculate the classifier evaluation measures for result
			try {
				ArrayList<ValidationMeasure> measuresOfThisRun = new ArrayList<ValidationMeasure>();
				for(int currentMeasure = 0; currentMeasure < this.measureCalculators.size(); currentMeasure++) {
					ValidationMeasure[] currMeas = null; 
					if(this.measureCalculators.get(currentMeasure) instanceof ClassificationQualityMeasureCalculatorInterface) {
						if(((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).getClassificationType() == ClassificationType.BINARY) {
							currMeas = ((ClassificationQualityMeasureCalculatorInterface)this.measureCalculators.get(currentMeasure)).calculateOneClassMeasure(
								((ValidatorNodeScheduler)this.getCorrespondingScheduler()).getLabeledAverageSongRelationships(), predictedSongs);
						} else if(((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).getClassificationType() == ClassificationType.MULTILABEL) {
							currMeas = ((ClassificationQualityMeasureCalculatorInterface)this.measureCalculators.get(currentMeasure)).calculateMultiLabelMeasure(
									((ValidatorNodeScheduler)this.getCorrespondingScheduler()).getLabeledSongRelationships(), predictedSongs);
						} else {
							currMeas = ((ClassificationQualityMeasureCalculatorInterface)this.measureCalculators.get(currentMeasure)).calculateMultiClassMeasure(
									((ValidatorNodeScheduler)this.getCorrespondingScheduler()).getLabeledSongRelationships(), predictedSongs);
						}
					} else if(this.measureCalculators.get(currentMeasure) instanceof DataReductionMeasureCalculatorInterface) {
						currMeas = ((DataReductionMeasureCalculatorInterface)this.measureCalculators.get(currentMeasure)).calculateMeasure(
								((ValidatorNodeScheduler)this.correspondingScheduler).getListOfAllProcessedFiles());
					} else {
						throw new NodeException("Unknown measure: " + this.measureCalculators.get(currentMeasure));
					}
					for(int k=0;k<currMeas.length;k++) {
						measuresOfThisRun.add(currMeas[k]);
					}
				}
				measuresForEveryModel.add(measuresOfThisRun);
			} catch (NodeException e) {
				e.printStackTrace();
				throw e;
			}
		}
		
		// Calculate the number of double measures
		int numberOfDoubleMeasures = 0;
		for(int i=0;i<measuresForEveryModel.get(0).size();i++) {
			if(measuresForEveryModel.get(0).get(i) instanceof ValidationMeasureDouble) {
				numberOfDoubleMeasures++;
			}
		}
		
		// Calculate the mean measure values only for double measures
		Double[] meanMeasures = new Double[numberOfDoubleMeasures];
		for(int i=0;i<meanMeasures.length;i++) 
			meanMeasures[i] = 0.0d;
		int currentIndexOfMeanMeasure = 0;
		for(int i=0;i<measuresForEveryModel.get(0).size();i++) {
			if(measuresForEveryModel.get(0).get(i) instanceof ValidationMeasureDouble) {
			
				// Go through all runs
				for(int j=0;j<measuresForEveryModel.size();j++) {
					meanMeasures[currentIndexOfMeanMeasure] += ((ValidationMeasureDouble)measuresForEveryModel.get(j).get(i)).getValue();
				}
				meanMeasures[currentIndexOfMeanMeasure] /= measuresForEveryModel.size();
				currentIndexOfMeanMeasure++;
			}
		}
		
		// Save the measure values to the list, going through all measures
		try {
			ArrayList<ValidationMeasure> measureList = new ArrayList<ValidationMeasure>();
			currentIndexOfMeanMeasure = 0;
			for(int i=0;i<measuresForEveryModel.get(0).size();i++) {
				
				// If only one model was evaluated, write it; if more models were evaluated,
				// save also the mean measure values across all models
				if(modelsToEvaluate.size() == 1) {
					Class<?> measureClass = Class.forName(measuresForEveryModel.get(0).get(i).getClass().getCanonicalName());
					ValidationMeasure m = (ValidationMeasure)measureClass.newInstance();
					m.setValue(measuresForEveryModel.get(0).get(i).getValue());
					m.setName(measuresForEveryModel.get(0).get(i).getName() + " for " + modelsToEvaluate.get(0).toString());
					m.setId(measuresForEveryModel.get(0).get(i).getId());
					if(m instanceof ValidationMeasureDouble) {
						((ValidationMeasureDouble)m).setForMinimizing(((ValidationMeasureDouble)measuresForEveryModel.get(0).get(i)).isForMinimizing());
					}
					measureList.add(m);
				} else {
					for(int j=0;j<measuresForEveryModel.size();j++) {
						Class<?> measureClass = Class.forName(measuresForEveryModel.get(0).get(i).getClass().getCanonicalName());
						ValidationMeasure m = (ValidationMeasure)measureClass.newInstance();
						m.setValue(measuresForEveryModel.get(j).get(i).getValue());
						m.setName(measuresForEveryModel.get(0).get(i).getName() + " for " + modelsToEvaluate.get(j).toString());
						m.setId(measuresForEveryModel.get(0).get(i).getId());
						if(m instanceof ValidationMeasureDouble) {
							((ValidationMeasureDouble)m).setForMinimizing(((ValidationMeasureDouble)
									measuresForEveryModel.get(0).get(i)).isForMinimizing());
						}
						measureList.add(m);
					}
					
					// Add the mean measure value over all models for double measures
					Class<?> measureClass = Class.forName(measuresForEveryModel.get(0).get(i).getClass().getCanonicalName());
					ValidationMeasure m = (ValidationMeasure)measureClass.newInstance();
					if(m instanceof ValidationMeasureDouble) {
						m.setValue(meanMeasures[currentIndexOfMeanMeasure]);
						m.setName("mean(" + measuresForEveryModel.get(0).get(i).getName() + ")");
						m.setId(measuresForEveryModel.get(0).get(i).getId());
						((ValidationMeasureDouble)m).setForMinimizing(((ValidationMeasureDouble)measuresForEveryModel.get(0).get(i)).isForMinimizing());
						measureList.add(m);
						currentIndexOfMeanMeasure++;
					}
				}
			}
			((ValidationConfiguration)this.getCorrespondingScheduler().getConfiguration()).setCalculatedMeasures(measureList);
		} catch(ClassNotFoundException e) {
			throw new NodeException("Could not find the appropriate measure class: " + e.getMessage());
		} catch(IllegalAccessException e) {
			throw new NodeException("Could not access the appropriate measure class: " + e.getMessage());
		} catch(InstantiationException e) {
			throw new NodeException("Could not instantiate the appropriate measure class: " + e.getMessage());
		}
	}

	/*
	 * (non-Javadoc)
	 * @see amuse.interfaces.AmuseTaskInterface#initialize()
	 */
	public void initialize() throws NodeException {
		// Do nothing, since initialization is not required		
	}

	/*
	 * (non-Javadoc)
	 * @see amuse.interfaces.AmuseTaskInterface#setParameters(java.lang.String)
	 */
	public void setParameters(String parameterString) throws NodeException {
		if(parameterString.startsWith("\"") || parameterString.startsWith("'") || parameterString.startsWith("|") && parameterString.endsWith("|")) {
			this.pathToModelFile = parameterString.substring(1,parameterString.length()-1);
		} else {
			this.pathToModelFile = parameterString;
		}
	}
	
	
	/*
	 * (non-Javadoc)
	 * @see amuse.nodes.validator.interfaces.ValidatorInterface#calculateListOfUsedProcessedFeatureFiles()
	 */
	public ArrayList<String> calculateListOfUsedProcessedFeatureFiles() throws NodeException {
		
		// If the ground truth file is not available, return null
		if(((ValidatorNodeScheduler)this.correspondingScheduler).getGroundTruthFile() == null) {
			return null;
		}

		ArrayList<String> listOfUsedProcessedFeatureFiles = new ArrayList<String>();
		try {
		
			int categoryIdForTrainingSet = 0;
			
			// Get the id for training category
			String pathToModel = new String(this.pathToModelFile);
			pathToModel = pathToModel.substring(
					((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).getModelDatabase().length()+1,pathToModel.length());
			pathToModel = pathToModel.substring(0,pathToModel.indexOf("-"));
			categoryIdForTrainingSet = new Integer(pathToModel);
			
			// Load the file list with training tracks
			String categoryForTraining = new String();
			ArffLoader categoryDescriptionLoader = new ArffLoader();
			Instance currentInstance;
			categoryDescriptionLoader.setFile(new File(AmusePreferences.getMultipleTracksAnnotationTablePath()));
			Attribute idAttribute = categoryDescriptionLoader.getStructure().attribute("Id");
			Attribute fileNameAttribute = categoryDescriptionLoader.getStructure().attribute("Path");
			currentInstance = categoryDescriptionLoader.getNextInstance(categoryDescriptionLoader.getStructure());
			while(currentInstance != null) {
				if(categoryIdForTrainingSet == currentInstance.value(idAttribute)) {
					categoryForTraining = currentInstance.stringValue(fileNameAttribute);
					break;
				}
				currentInstance = categoryDescriptionLoader.getNextInstance(categoryDescriptionLoader.getStructure());
			}
			
			// Load the data about initially and finally used time windows from the processed feature files of training tracks
			ArffLoader inputDescriptionLoader = new ArffLoader();
			inputDescriptionLoader.setFile(new File(categoryForTraining));
			Attribute musicFileNameAttribute = inputDescriptionLoader.getStructure().attribute("Path");
			currentInstance = inputDescriptionLoader.getNextInstance(inputDescriptionLoader.getStructure());
			while(currentInstance != null) {
				
				String musicFile = currentInstance.stringValue(musicFileNameAttribute);
				if(musicFile.startsWith(AmusePreferences.get(KeysStringValue.MUSIC_DATABASE))) {
					musicFile = musicFile.substring(AmusePreferences.get(KeysStringValue.MUSIC_DATABASE).length(),musicFile.length());
				}
				String absoluteName = musicFile.substring(musicFile.lastIndexOf(File.separator)+1,musicFile.lastIndexOf("."));
				String pathToFile = musicFile.substring(0,musicFile.lastIndexOf(File.separator));
				musicFile = 
						((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).getProcessedFeatureDatabase() + pathToFile + File.separator +
						absoluteName + File.separator + absoluteName + "_" + ((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).getProcessedFeaturesModelName() +
						".arff";
				listOfUsedProcessedFeatureFiles.add(musicFile);
				currentInstance = inputDescriptionLoader.getNextInstance(inputDescriptionLoader.getStructure());
			}
			
			// Load the data about initially and finally used time windows from the processed feature files of test tracks
			inputDescriptionLoader = new ArffLoader();
			inputDescriptionLoader.setFile(((ValidatorNodeScheduler)this.correspondingScheduler).getGroundTruthFile());
			musicFileNameAttribute = inputDescriptionLoader.getStructure().attribute("Path");
			currentInstance = inputDescriptionLoader.getNextInstance(inputDescriptionLoader.getStructure());
			while(currentInstance != null) {
				
				String musicFile = currentInstance.stringValue(musicFileNameAttribute);
				if(musicFile.startsWith(AmusePreferences.get(KeysStringValue.MUSIC_DATABASE))) {
					musicFile = musicFile.substring(AmusePreferences.get(KeysStringValue.MUSIC_DATABASE).length(),musicFile.length());
				}
				String absoluteName = musicFile.substring(musicFile.lastIndexOf(File.separator)+1,musicFile.lastIndexOf("."));
				String pathToFile = musicFile.substring(0,musicFile.lastIndexOf(File.separator));
				musicFile = 
					((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).getProcessedFeatureDatabase() + pathToFile + File.separator +
						absoluteName + File.separator + absoluteName + "_" + ((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).getProcessedFeaturesModelName() +
						".arff";
				listOfUsedProcessedFeatureFiles.add(musicFile);
				currentInstance = inputDescriptionLoader.getNextInstance(inputDescriptionLoader.getStructure());
			}
		} catch(IOException e) {
			throw new NodeException(e.getMessage());
		}
		
		return listOfUsedProcessedFeatureFiles;
	}
	
	/*private String listCorrectSongs(ArrayList<Double> groundTruthRelationships, ArrayList<ClassifiedSongPartitionsDescription> predictedRelationships) throws NodeException {
		amuse.nodes.validator.measures.confusionmatrix.base.ListOfCorrectlyPredictedInstances mc = new amuse.nodes.validator.measures.confusionmatrix.base.ListOfCorrectlyPredictedInstances();
		mc.setSongLevel(true);
		ValidationMeasureDouble[] list = mc.calculateMeasure(groundTruthRelationships, predictedRelationships);
		StringBuffer b = new StringBuffer();
		for(int i=0;i<list.length;i++) {
			b.append(list[i].getValue().intValue() + " ");
		}
		return b.toString().substring(0,b.length()-1);
	}*/
	
}


