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
 *  Creation date: 29.01.2009
 */ 
package amuse.nodes.validator.metrics.confusionmatrix.base;

import java.util.ArrayList;

import amuse.interfaces.nodes.NodeException;
import amuse.nodes.classifier.interfaces.ClassifiedSongPartitionsDescription;
import amuse.nodes.validator.interfaces.ClassificationQualityMetricCalculator;
import amuse.nodes.validator.interfaces.ValidationMetricDouble;
import amuse.nodes.validator.interfaces.ValidationMetricString;

/**
 * Returns the list with correctly predicted instances
 *  
 * @author Igor Vatolkin
 * @version $Id: $
 */
public class ListOfCorrectlyPredictedInstances extends ClassificationQualityMetricCalculator {

	/**
	 * @see amuse.nodes.validator.interfaces.ClassificationQualityMetricCalculatorInterface#setParameters(java.lang.String)
	 */
	public void setParameters(String parameterString) throws NodeException {
		// Does nothing
	}
	
	/**
	 * @see amuse.nodes.validator.interfaces.ClassificationQualityMetricCalculatorInterface#calculateMetric(java.util.ArrayList, java.util.ArrayList)
	 */
	public ValidationMetricString[] calculateMetric(ArrayList<Double> groundTruthRelationships, ArrayList<ClassifiedSongPartitionsDescription> predictedRelationships) throws NodeException {
		if(groundTruthRelationships.size() != predictedRelationships.size()) {
			throw new NodeException("The number of labeled instances must be equal to the number of predicted instances!");
		}
		
		ValidationMetricString[] metricOnSongLev = null;
		
		if(groundTruthRelationships.get(0) instanceof Double) {
			metricOnSongLev = calculateFuzzyMetricOnSongLevel(groundTruthRelationships, predictedRelationships);
		} else {
			return null;
		}
		
		// Return the corresponding number of metric values
		return metricOnSongLev;
	}

	/**
	 * @see amuse.nodes.validator.interfaces.ClassificationQualityMetricCalculatorInterface#calculateBinaryMetricOnSongLevel(java.util.ArrayList, java.util.ArrayList)
	 */
	public ValidationMetricDouble[] calculateBinaryMetricOnSongLevel(ArrayList<Boolean> groundTruthRelationships, ArrayList<ClassifiedSongPartitionsDescription> predictedRelationships) throws NodeException {
		return null;
	}

	/**
	 * @see amuse.nodes.validator.interfaces.ClassificationQualityMetricCalculatorInterface#calculateBinaryMetricOnPartitionLevel(java.util.ArrayList, java.util.ArrayList)
	 */
	public ValidationMetricDouble[] calculateBinaryMetricOnPartitionLevel(ArrayList<Boolean> groundTruthRelationships, ArrayList<ClassifiedSongPartitionsDescription> predictedRelationships) throws NodeException {
		return null;
	}

	
	/**
	 * @see amuse.nodes.validator.interfaces.ClassificationQualityMetricCalculatorInterface#calculateFuzzyMetricOnSongLevel(java.util.ArrayList, java.util.ArrayList)
	 */
	public ValidationMetricString[] calculateFuzzyMetricOnSongLevel(ArrayList<Double> groundTruthRelationships, ArrayList<ClassifiedSongPartitionsDescription> predictedRelationships) throws NodeException {
		ArrayList<Integer> listOfCorrectlyPredictedSongs = new ArrayList<Integer>();
		
		for(int i=0;i<groundTruthRelationships.size();i++) {
			
			// Calculate the predicted value for this song (averaging among all partitions)
			Double currentPredictedValue = 0.0d;
			for(int j=0;j<predictedRelationships.get(i).getRelationships().length;j++) {
				currentPredictedValue += predictedRelationships.get(i).getRelationships()[j];
			}
			currentPredictedValue /= predictedRelationships.get(i).getRelationships().length;
			
			if(currentPredictedValue >= 0.5) {
				currentPredictedValue = 1.0d;
			} else {
				currentPredictedValue = 0.0d;
			}
			
			// Round the ground truth value to a binary value
			Double currentGroundTruthValue = groundTruthRelationships.get(i);
			if(currentGroundTruthValue >= 0.5) {
				currentGroundTruthValue = 1.0d;
			} else {
				currentGroundTruthValue = 0.0d;
			}
			
			if(currentGroundTruthValue.doubleValue() == 1.0 && currentPredictedValue.doubleValue() == 1.0 ||
					currentGroundTruthValue.doubleValue() == 0.0 && currentPredictedValue.doubleValue() == 0.0) {
				listOfCorrectlyPredictedSongs.add(i);
			}
			
		}
		
		ValidationMetricString[] list = new ValidationMetricString[1];
		list[0] = new ValidationMetricString();
		StringBuffer buff = new StringBuffer();
		buff.append("\"");
		for(int i=0;i<listOfCorrectlyPredictedSongs.size();i++) {
			buff.append(listOfCorrectlyPredictedSongs.get(i) + " ");
		}
		buff.append("\"");
		list[0].setValue(buff.toString());
		list[0].setId(114);
		list[0].setName("List of correctly predicted instances on song level");
		
		return list;
	}

	/**
	 * @see amuse.nodes.validator.interfaces.ClassificationQualityMetricCalculatorInterface#calculateFuzzyMetricOnPartitionLevel(java.util.ArrayList, java.util.ArrayList)
	 */
	public ValidationMetricDouble[] calculateFuzzyMetricOnPartitionLevel(ArrayList<Double> groundTruthRelationships, ArrayList<ClassifiedSongPartitionsDescription> predictedRelationships) throws NodeException {
		// TODO -> partitions?
		return null;
	}

	
	/**
	 * @see amuse.nodes.validator.interfaces.ClassificationQualityMetricCalculatorInterface#calculateMulticlassMetricOnSongLevel(java.util.ArrayList, java.util.ArrayList)
	 */
	public ValidationMetricDouble[] calculateMulticlassMetricOnSongLevel(ArrayList<ArrayList<Double>> groundTruthRelationships, ArrayList<ArrayList<ClassifiedSongPartitionsDescription>> predictedRelationships) throws NodeException {
		return null;
	}

	/**
	 * @see amuse.nodes.validator.interfaces.ClassificationQualityMetricCalculatorInterface#calculateMulticlassMetricOnPartitionLevel(java.util.ArrayList, java.util.ArrayList)
	 */
	public ValidationMetricDouble[] calculateMulticlassMetricOnPartitionLevel(ArrayList<ArrayList<Double>> groundTruthRelationships, ArrayList<ArrayList<ClassifiedSongPartitionsDescription>> predictedRelationships) throws NodeException {
		return null;
	}


}
