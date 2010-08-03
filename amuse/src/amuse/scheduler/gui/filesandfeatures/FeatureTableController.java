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
 * Creation date: 08.03.2009
 */
package amuse.scheduler.gui.filesandfeatures;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import amuse.data.FeatureTable;
import amuse.data.io.ArffDataSet;
import amuse.data.io.DataSetAbstract;
import amuse.data.io.attributes.NumericAttribute;
import amuse.scheduler.gui.dialogs.SelectArffFileChooser;

/**
 *
 * @author Clemens Waeltken
 */
public class FeatureTableController implements ActionListener {

    private FeatureTableModel model;
    private FeatureTableView view;

    private File featureFolder = new File("experiments/featurelists");

    public FeatureTableController(FeatureTableModel model, FeatureTableView view) {
        this.model = model;
        this.view = view;
        this.view.setModel(this.model);
        this.view.setController(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equals("check")) {
            checkSelected();
        } else if (e.getActionCommand().equals("uncheck")) {
            uncheckSelected();
        } else if (e.getActionCommand().equals("load")) {
            JFileChooser loadDialog = new SelectArffFileChooser("Feature List" , featureFolder);
            File file = null;
            while (true) {
                int option = loadDialog.showDialog(view.getView(), "Load from ARFF File");
                if (option == JFileChooser.CANCEL_OPTION) {
                    return;
                }
                file = loadDialog.getSelectedFile();
                if (file.exists()) {
                    break;
                } else {
                    JOptionPane.showMessageDialog(view.getView(), "Selected file does not exist!", "Missing File", JOptionPane.ERROR_MESSAGE);
                }
            }
            // 3. Load DataSet:
            loadFeatureTableSelection(file);
        } else if (e.getActionCommand().equals("save")) {
            JFileChooser loadDialog = new SelectArffFileChooser("Feature List" , featureFolder);
            File selectedFile = null;
            int option = loadDialog.showDialog(view.getView(), "Save to ARFF File");
            if (option == JFileChooser.CANCEL_OPTION) {
                return;
            }
            selectedFile = loadDialog.getSelectedFile();

            if (!selectedFile.getName().endsWith(".arff")) {
                selectedFile = new File(selectedFile.getAbsolutePath() + ".arff");
            }
            // 2. Existing file list?
            if (selectedFile.exists()) {
                int selected = JOptionPane.showConfirmDialog(view.getView(), "Do you want to override this file? " + selectedFile.getName(), "File already exists!", JOptionPane.YES_NO_OPTION);
                if (selected == JOptionPane.NO_OPTION) {
                    return;
                }
            }

            // 3. Create DataSet:
            ArffDataSet featureSet = model.getCurrentFeatureTable().getAccordingDataSet();
            // 4. Write file
            try {
                featureSet.saveToArffFile(selectedFile);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(view.getView(), ex, "Error writing FeatureList!", JOptionPane.ERROR_MESSAGE);
            }
            JOptionPane.showMessageDialog(view.getView(), "FileList successfully saved!", "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void checkSelected() {
        int[] selectedRows = view.getSelectedRows();
        for (int row : selectedRows) {
            model.setValueAt(true, row, model.getSelectForExtractionColumnIndex());
        }
    }

    private void uncheckSelected() {
        int[] selectedRows = view.getSelectedRows();
        for (int row : selectedRows) {
            model.setValueAt(false, row, model.getSelectForExtractionColumnIndex());
        }
    }

    protected void loadFeatureTableSelection(File file) {
        DataSetAbstract featureSet = null;
        try {
            featureSet = new ArffDataSet(file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(view.getView(), "Unable to Load FeatureList : " + ex.getMessage(), "Error Loading FeatureList", JOptionPane.ERROR_MESSAGE);
        }
        if (featureSet != null) {
            loadFeatureTableSelection(featureSet);
        } else {
            JOptionPane.showMessageDialog(view.getView(), "This file does not contain a FeatureList!", "Error Loading FeatureList", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadFeatureTableSelection(DataSetAbstract set) {
            List<Integer> ids = new ArrayList<Integer>(set.getValueCount());
            NumericAttribute idAttribute = (NumericAttribute) set.getAttribute("Id");
            for (Double id : idAttribute.getValues()) {
                ids.add(id.intValue());
            }
            model.selectFeaturesByID(ids);

    }

    void loadFeatureTableSelection(FeatureTable featureTable) {
        loadFeatureTableSelection(featureTable.getAccordingDataSet());
    }
}
