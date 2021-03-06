package com.energyxxer.trident.main.window.sections;

import com.energyxxer.trident.global.Preferences;
import com.energyxxer.trident.global.temp.projects.TridentProject;
import com.energyxxer.trident.main.window.TridentWindow;
import com.energyxxer.trident.ui.ToolbarButton;
import com.energyxxer.trident.ui.dialogs.file_dialogs.ProjectDialog;
import com.energyxxer.trident.ui.dialogs.settings.Settings;
import com.energyxxer.trident.ui.misc.TipScreen;
import com.energyxxer.trident.ui.theme.change.ThemeListenerManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class WelcomePane extends JPanel {

    public final TipScreen tipScreen;

    ThemeListenerManager tlm = new ThemeListenerManager();

    private JPanel tipPanel = new JPanel(new BorderLayout());
    private JPanel contentPanel = new JPanel(new BorderLayout());
    private JPanel buttonPanel = new JPanel(new GridLayout(3,2));

    public WelcomePane() {
        super(new GridBagLayout());
        this.setOpaque(false);
        contentPanel.setOpaque(false);
        tipPanel.setOpaque(false);
        buttonPanel.setOpaque(false);

        this.add(contentPanel);
        contentPanel.add(tipPanel, BorderLayout.NORTH);
        contentPanel.add(buttonPanel, BorderLayout.CENTER);

        tipScreen = new TipScreen();
        tipPanel.add(tipScreen, BorderLayout.CENTER);

        tlm.addThemeChangeListener(t -> {
            tipScreen.setForeground(t.getColor("TipScreen.foreground", "General.foreground"));
            tipScreen.setFont(t.getFont("TipScreen","General"));
        });

        {
            ToolbarButton button = new ToolbarButton("project", tlm);
            button.setText("New Project");
            button.setHintText("Create a new Trident Project");
            button.addActionListener(e -> ProjectDialog.create(TridentProject.PROJECT_TYPE));
            JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            wrapper.setOpaque(false);
            wrapper.add(button);
            buttonPanel.add(wrapper);
        }
        {
            ToolbarButton button = new ToolbarButton("trident_file", tlm);
            button.setText("Trident Website");
            button.setHintText("Go to the official Trident Website");
            button.addActionListener(e -> {
                try {
                    Desktop.getDesktop().browse(new URI("http://energyxxer.com/trident"));
                } catch (IOException | URISyntaxException ex) {
                    ex.printStackTrace();
                }
            });
            JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT));
            wrapper.setOpaque(false);
            wrapper.add(button);
            buttonPanel.add(wrapper);
        }
        {
            ToolbarButton button = new ToolbarButton(null, tlm);
            button.setText("Select Workspace");
            button.setHintText("Choose a location to keep your projects");
            button.addActionListener(e -> Preferences.promptWorkspace());
            JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            wrapper.setOpaque(false);
            wrapper.add(button);
            buttonPanel.add(wrapper);
        }
        {
            ToolbarButton button = new ToolbarButton("documentation", tlm);
            button.setText("Documentation");
            button.setHintText("Read the language docs");
            button.addActionListener(e -> {
                try {
                    Desktop.getDesktop().browse(new URI("https://docs.google.com/document/d/1w_3ILt8-8s1VG-qv7cLLdIrTJTtbQvj2klh2xTnxQVw/edit?usp=sharing"));
                } catch (IOException | URISyntaxException ex) {
                    ex.printStackTrace();
                }
            });
            JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT));
            wrapper.setOpaque(false);
            wrapper.add(button);
            buttonPanel.add(wrapper);
        }
        {
            ToolbarButton button = new ToolbarButton("cog", tlm);
            button.setText("Settings");
            button.setHintText("Manage settings");
            button.addActionListener(e -> {
                TridentWindow.toolbar.hint.dismiss();
                Settings.show();
            });
            JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            wrapper.setOpaque(false);
            wrapper.add(button);
            buttonPanel.add(wrapper);
        }
        {
            ToolbarButton button = new ToolbarButton("info", tlm);
            button.setText("About");
            button.setHintText("Learn about this build of Trident");
            button.addActionListener(e -> AboutPane.INSTANCE.setVisible(true));
            JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT));
            wrapper.setOpaque(false);
            wrapper.add(button);
            buttonPanel.add(wrapper);
        }
        //buttonPanel.add(new ToolbarButton("cog", tlm));
        //buttonPanel.add(new ToolbarButton("file", tlm));
        //buttonPanel.add(new ToolbarButton("model", tlm));
    }
}
