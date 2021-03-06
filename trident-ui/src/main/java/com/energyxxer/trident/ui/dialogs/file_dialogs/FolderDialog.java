package com.energyxxer.trident.ui.dialogs.file_dialogs;

import com.energyxxer.commodore.functionlogic.functions.Function;
import com.energyxxer.trident.files.FileType;
import com.energyxxer.trident.global.temp.projects.Project;
import com.energyxxer.trident.global.temp.projects.ProjectManager;
import com.energyxxer.trident.main.window.TridentWindow;
import com.energyxxer.trident.ui.styledcomponents.StyledButton;
import com.energyxxer.trident.ui.styledcomponents.StyledIcon;
import com.energyxxer.trident.ui.styledcomponents.StyledLabel;
import com.energyxxer.trident.ui.styledcomponents.StyledTextField;
import com.energyxxer.trident.ui.theme.change.ThemeListenerManager;
import com.energyxxer.trident.util.FileCommons;
import com.energyxxer.xswing.Padding;
import com.energyxxer.xswing.ScalableDimension;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Paths;

/**
 * Created by User on 2/10/2017.
 */
public class FolderDialog {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 115;
    private static final int HEIGHT_ERR = 140;

    private static JDialog dialog = new JDialog(TridentWindow.jframe);
    private static JPanel pane;

    private static StyledTextField nameField;

    private static JPanel errorPanel;
    private static StyledLabel errorLabel;

    private static StyledButton okButton;

    private static boolean valid = false;

    private static String destination;

    private static ThemeListenerManager tlm = new ThemeListenerManager();

    static {
        pane = new JPanel(new BorderLayout());
        pane.setPreferredSize(new ScalableDimension(WIDTH, HEIGHT));
        tlm.addThemeChangeListener(t ->
                pane.setBackground(t.getColor(new Color(235, 235, 235), "NewPackageDialog.background"))
        );

        //<editor-fold desc="Icon">
        JPanel iconPanel = new JPanel(new BorderLayout());
        iconPanel.setOpaque(false);
        iconPanel.setPreferredSize(new ScalableDimension(73, 48));
        iconPanel.add(new Padding(25), BorderLayout.WEST);
        iconPanel.setBorder(new EmptyBorder(0, 0, 0, 2));
        iconPanel.add(new StyledIcon("folder", 48, 48, Image.SCALE_SMOOTH, tlm));
        pane.add(iconPanel, BorderLayout.WEST);
        //</editor-fold>

        //<editor-fold desc="Inner Margin">
        pane.add(new Padding(15), BorderLayout.NORTH);
        pane.add(new Padding(25), BorderLayout.EAST);
        //</editor-fold>

        //<editor-fold desc="Content Components">
        JPanel content = new JPanel();
        content.setOpaque(false);

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        {
            JPanel entry = new JPanel(new BorderLayout());
            entry.setOpaque(false);
            entry.setMaximumSize(new ScalableDimension(Integer.MAX_VALUE, 25));

            StyledLabel instructionsLabel = new StyledLabel("Enter new folder name:", "NewPackageDialog", tlm);
            instructionsLabel.setStyle(Font.PLAIN);
            instructionsLabel.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            instructionsLabel.setHorizontalTextPosition(JLabel.LEFT);
            entry.add(instructionsLabel, BorderLayout.CENTER);

            content.add(entry);
        }
        {
            JPanel entry = new JPanel(new BorderLayout());
            entry.setOpaque(false);
            entry.setMaximumSize(new ScalableDimension(Integer.MAX_VALUE, 25));

            nameField = new StyledTextField("", "NewPackageDialog", tlm);
            nameField.getDocument().addUndoableEditListener(e -> validateInput());

            entry.add(nameField, BorderLayout.CENTER);

            content.add(entry);
        }

        {
            errorPanel = new JPanel();
            errorPanel.setOpaque(false);
            errorPanel.setMaximumSize(new ScalableDimension(Integer.MAX_VALUE, 0));

            errorLabel = new StyledLabel("", "NewPackageDialog.error", tlm);
            errorLabel.setStyle(Font.BOLD);
            errorLabel.setMaximumSize(new ScalableDimension(Integer.MAX_VALUE, errorLabel.getPreferredSize().height));
            errorPanel.add(errorLabel);

            content.add(errorPanel);
        }

        content.add(new Padding(5));

        {
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttons.setOpaque(false);
            buttons.setMaximumSize(new ScalableDimension(Integer.MAX_VALUE, 30));

            okButton = new StyledButton("OK", tlm);
            okButton.addActionListener(e -> submit());
            buttons.add(okButton);
            StyledButton cancelButton = new StyledButton("Cancel", tlm);
            cancelButton.addActionListener(e -> {
                cancel();
            });

            buttons.add(cancelButton);
            content.add(buttons);
        }

        pane.add(content, BorderLayout.CENTER);

        //</editor-fold>

        //<editor-fold desc="Enter key event">
        pane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "submit");
        pane.getActionMap().put("submit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submit();
            }
        });
        pane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        pane.getActionMap().put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancel();
            }
        });
        //</editor-fold>

        dialog.setContentPane(pane);
        dialog.pack();
        dialog.setResizable(false);

        dialog.setTitle("Create New Folder");

        dialog.setModalityType(Dialog.ModalityType.DOCUMENT_MODAL);
    }

    private static void submit() {
        if(!valid) return;
        String name = nameField.getText().trim();

        String path = destination + File.separator + name;

        new File(path.replace('/',File.separatorChar)).mkdirs();
        TridentWindow.projectExplorer.refresh();

        dialog.setVisible(false);
    }

    private static void cancel() {
        dialog.setVisible(false);
    }

    public static void create(FileType type, String destination) {
        FolderDialog.destination = destination;
        nameField.setText("");
        validateInput();

        Point center = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
        center.x -= dialog.getWidth()/2;
        center.y -= dialog.getHeight()/2;

        dialog.setLocation(center);

        dialog.setVisible(true);
    }

    private static void validateInput() {
        String str = nameField.getText().trim();

        if(str.length() <= 0) {
            valid = false;
            okButton.setEnabled(false);
            displayError(null);
            return;
        }

        //Check if package name is a valid filename
        valid = FileCommons.validatePath(str);
        if(!valid) {
            displayError("Error: Not a valid file name");
        }

        File file = new File(destination + File.separator + str);

        //Check if package exists
        if(valid) {
            valid = !file.exists();
            if (!valid) displayError("Error: Folder '" + str + "' already exists at the destination");
        }

        //Check if folder name is suited for data packs
        if(valid) {
            Project project = ProjectManager.getAssociatedProject(file);
            String projectDir = (project != null) ? project.getRootDirectory().getPath() + File.separator : null;

            if(projectDir != null && file.toPath().startsWith(Paths.get(projectDir).resolve("datapack"))) {
                valid = str.replace(File.separator,"/").matches(Function.ALLOWED_PATH_REGEX);
            }

            if(!valid) {
                displayError("Error: Not a valid path in data pack");
            }
        }

        if(valid) displayError(null);
        okButton.setEnabled(valid);
    }

    private static void displayError(String message) {
        if(message == null) {
            pane.setPreferredSize(new ScalableDimension(WIDTH, HEIGHT));
            errorPanel.setMaximumSize(new ScalableDimension(Integer.MAX_VALUE, 0));
            errorLabel.setText("");
            dialog.pack();
        } else {
            pane.setPreferredSize(new ScalableDimension(WIDTH, HEIGHT_ERR));
            errorPanel.setMaximumSize(new ScalableDimension(Integer.MAX_VALUE, 30));
            errorLabel.setText(message);
            errorLabel.revalidate();
            dialog.pack();
        }
    }
}
