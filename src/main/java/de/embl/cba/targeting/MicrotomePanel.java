package de.embl.cba.targeting;

import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.SliderPanel;
import bdv.tools.brightness.SliderPanelDouble;
import bdv.util.*;
import bdv.viewer.Interpolation;
import de.embl.cba.bdv.utils.BdvUtils;
import de.embl.cba.bdv.utils.BrightnessUpdateListener;
import de.embl.cba.bdv.utils.Logger;
import de.embl.cba.bdv.utils.sources.ARGBConvertedRealSource;
import de.embl.cba.bdv.utils.sources.Metadata;
import de.embl.cba.tables.FileAndUrlUtils;
import de.embl.cba.tables.FileUtils;
import de.embl.cba.tables.TableColumns;
import de.embl.cba.tables.color.ColorUtils;
import de.embl.cba.tables.color.LazyLabelsARGBConverter;
import de.embl.cba.tables.ij3d.UniverseUtils;
import de.embl.cba.tables.image.DefaultImageSourcesModel;
import de.embl.cba.tables.image.SourceAndMetadata;
import de.embl.cba.tables.tablerow.TableRowImageSegment;
import de.embl.cba.tables.view.Segments3dView;
import de.embl.cba.tables.view.SegmentsBdvView;
import de.embl.cba.tables.view.TableRowsTableView;
import de.embl.cba.tables.view.combined.SegmentsTableBdvAnd3dViews;
import ij3d.Content;
import ij3d.ContentConstants;
import ij3d.Image3DUniverse;
import net.imglib2.type.numeric.ARGBType;
import org.scijava.java3d.Transform3D;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.vecmath.*;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.util.*;
import java.util.List;


// similar to mobie source panel - https://github.com/mobie/mobie-viewer-fiji/blob/master/src/main/java/de/embl/cba/mobie/ui/viewer/SourcesPanel.java

public class MicrotomePanel extends JPanel {

    private final MicrotomeManager microtomeManager;
    private final BoundedValueDouble initialKnifeAngle;
    private final BoundedValueDouble initialTiltAngle;
    private final BoundedValueDouble knifeAngle;
    private final BoundedValueDouble tiltAngle;
    private final BoundedValueDouble rotationAngle;

    public MicrotomePanel(MicrotomeManager microtomeManager) {
        this.microtomeManager = microtomeManager;

        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Microtome Controls"),
                BorderFactory.createEmptyBorder(5,5,5,5)));

        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

        ActionListener blockListener = new blockListener();
        JButton initialiseBlock = new JButton("Initialise block");
        initialiseBlock.setActionCommand("initialise_block");
        initialiseBlock.addActionListener(blockListener);
        this.add(initialiseBlock);

        // Initial knife angle
        initialKnifeAngle =
                addSliderToPanel(this, "Initial Knife Angle", -30, 30, 0);

        // Initial tilt angle
        initialTiltAngle =
                addSliderToPanel(this, "Initial tilt angle", -20, 20, 0);

        // Knife Rotation
        MicrotomeListener knifeListener = new KnifeListener();
        knifeAngle = addSliderToPanel(this, "Knife Rotation",  -30, 30, 0, knifeListener);

        // Arc Tilt
        MicrotomeListener holderBackListener = new HolderBackListener();
        tiltAngle =
                addSliderToPanel(this, "Arc Tilt", -20, 20, 0, holderBackListener);

        // Holder Rotation
        MicrotomeListener holderFrontListener = new HolderFrontListener();
        rotationAngle =
                addSliderToPanel(this, "Holder rotation", -180, 180, 0, holderFrontListener);

//        Orientation of axes matches those in original blender file, object positions also match
//        Interactive transform setter in 3d viewer: https://github.com/fiji/3D_Viewer/blob/master/src/main/java/ij3d/gui/InteractiveTransformDialog.java
    }

    public BoundedValueDouble getKnifeAngle() {
        return knifeAngle;
    }

    public BoundedValueDouble getTiltAngle() {
        return tiltAngle;
    }

    public BoundedValueDouble getRotationAngle() {
        return rotationAngle;
    }

    private BoundedValueDouble addSliderToPanel(JPanel panel, String sliderName, double min, double max, double currentValue,
                                     MicrotomeListener updateListener) {

        final BoundedValueDouble sliderValue =
                new BoundedValueDouble(
                        min,
                        max,
                        currentValue);
        SliderPanelDouble slider = createSlider(panel, sliderName, sliderValue);
        updateListener.setValues(sliderValue, slider);
        sliderValue.setUpdateListener(updateListener);
        return sliderValue;
    }

    private BoundedValueDouble addSliderToPanel(JPanel panel, String sliderName, double min, double max, double currentValue) {

//                as here https://github.com/tischi/imagej-utils/blob/b7bdece786c1593969ec469916adf9737a7768bb/src/main/java/de/embl/cba/bdv/utils/BdvDialogs.java
        final BoundedValueDouble sliderValue =
                new BoundedValueDouble(
                        min,
                        max,
                        currentValue);
        createSlider(panel, sliderName, sliderValue);
        return sliderValue;
    }

    private SliderPanelDouble createSlider (JPanel panel, String sliderName, BoundedValueDouble sliderValue) {
        double spinnerStepSize = 1;
        JPanel sliderPanel = new JPanel();
        sliderPanel.add( new JLabel(sliderName));
        sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.PAGE_AXIS));
        final SliderPanelDouble s = new SliderPanelDouble(sliderName, sliderValue, spinnerStepSize);
        s.setNumColummns(7);
        s.setDecimalFormat("####.####");

        panel.add(s);
        refreshGui();
        return s;
    }

    private void refreshGui() {
        this.revalidate();
        this.repaint();
    }


    class blockListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            microtomeManager.initialiseMicrotome(initialKnifeAngle.getCurrentValue(), initialTiltAngle.getCurrentValue());
        }
    }

    abstract class MicrotomeListener implements BoundedValueDouble.UpdateListener {
        public void setValues(BoundedValueDouble value, SliderPanelDouble slider) {}
    }

    class KnifeListener extends MicrotomeListener {

        private BoundedValueDouble knifeAngle;
        private SliderPanelDouble knifeSlider;

        public KnifeListener() {}

        public void setValues(BoundedValueDouble knifeAngle, SliderPanelDouble knifeSlider) {
            this.knifeAngle = knifeAngle;
            this.knifeSlider = knifeSlider;
        }

        @Override
        public void update() {
            knifeSlider.update();
            microtomeManager.setKnifeAngle(knifeAngle.getCurrentValue());
        }
    }

    class HolderBackListener extends MicrotomeListener {

        private BoundedValueDouble tiltAngle;
        private SliderPanelDouble tiltSlider;

        public HolderBackListener() {}

        public void setValues(BoundedValueDouble tiltAngle, SliderPanelDouble tiltSlider) {
            this.tiltAngle = tiltAngle;
            this.tiltSlider = tiltSlider;
        }

        @Override
        public void update() {
            tiltSlider.update();
            microtomeManager.setTilt(tiltAngle.getCurrentValue());
        }
    }

    class HolderFrontListener extends MicrotomeListener {

        private BoundedValueDouble rotationAngle;
        private SliderPanelDouble rotationSlider;

        public HolderFrontListener() {}

        public void setValues(BoundedValueDouble rotationAngle, SliderPanelDouble rotationSlider) {
            this.rotationAngle = rotationAngle;
            this.rotationSlider = rotationSlider;
        }

        @Override
        public void update() {
            rotationSlider.update();
            microtomeManager.setRotation(rotationAngle.getCurrentValue());
        }
    }

}
