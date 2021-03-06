package com.energyxxer.trident.global;

import com.energyxxer.commodore.module.CommandModule;
import com.energyxxer.commodore.standard.StandardDefinitionPacks;
import com.energyxxer.enxlex.report.Report;
import com.energyxxer.enxlex.report.Reported;
import com.energyxxer.trident.global.temp.projects.Project;
import com.energyxxer.trident.global.temp.projects.ProjectManager;
import com.energyxxer.trident.main.window.TridentWindow;
import com.energyxxer.trident.ui.Tab;
import com.energyxxer.trident.ui.modules.FileModuleToken;
import com.energyxxer.trident.ui.modules.ModuleToken;
import com.energyxxer.trident.ui.theme.change.ThemeChangeListener;
import com.energyxxer.util.ImageManager;
import com.energyxxer.util.Lazy;
import com.energyxxer.util.logger.Debug;
import com.energyxxer.util.out.Console;
import com.energyxxer.util.processes.AbstractProcess;
import com.energyxxer.xswing.ScalableGraphics2D;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Commons {

    public static String DEFAULT_CARET_DISPLAY_TEXT = "-:-";

    public static String themeAssetsPath = "light_theme/";

    private static Lazy<CommandModule> defaultModule = new Lazy<> (() -> {
        CommandModule defaultModule = new CommandModule("Default Module");
        try {
            StandardDefinitionPacks.MINECRAFT_JAVA_LATEST_SNAPSHOT.load();
            defaultModule.importDefinitions(StandardDefinitionPacks.MINECRAFT_JAVA_LATEST_SNAPSHOT);
        } catch(IOException x) {
            Debug.log(x.toString(), Debug.MessageType.ERROR);
        }
        return defaultModule;
    });

    /*private static Lazy<BedrockModule> defaultBedrockModule = new Lazy<> (() -> {
        BedrockModule defaultModule = new BedrockModule("Default Module");
        try {
            StandardDefinitionPacks.MINECRAFT_BEDROCK_LATEST_RELEASE.load();
            defaultModule.importDefinitions(StandardDefinitionPacks.MINECRAFT_BEDROCK_LATEST_RELEASE);
        } catch(IOException x) {
            Debug.log(x.toString(), Debug.MessageType.ERROR);
        }
        return defaultModule;
    });*/

    static {
        ThemeChangeListener.addThemeChangeListener(t -> {
            themeAssetsPath = t.getString("Assets.path","default:light_theme/");
        }, true);
    }

    public static boolean isSpecialCharacter(char ch) {
        return "\b\r\n\t\f\u007F\u001B".contains("" + ch);
    }

    public static void showInExplorer(String path) {
        try {
            if(System.getProperty("os.name").startsWith("Windows")) {
                Runtime.getRuntime().exec("Explorer.exe /select," + path);
            } else if(Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                desktop.open(new File(path).getParentFile());
            } else {
                Debug.log("Couldn't show file '" + path + "': Desktop is not supported", Debug.MessageType.ERROR);
            }
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    public static void openInExplorer(String path) {
        try {
            if(System.getProperty("os.name").startsWith("Windows")) {
                Runtime.getRuntime().exec("Explorer.exe \"" + path + "\""); //can't believe you don't have to escape it
            } else if(Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                desktop.open(new File(path));
            } else {
                Debug.log("Couldn't open file '" + path + "': Desktop is not supported", Debug.MessageType.ERROR);
            }
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    private static String getIconPath(String name) {
        return "/assets/icons/" + themeAssetsPath + name + ".png";
    }

    public static BufferedImage getIcon(String name) {
        return ImageManager.load(getIconPath(name));
    }

    public static void updateActiveProject() {
        if(TridentWindow.toolbar != null && TridentWindow.projectExplorer != null)
            TridentWindow.toolbar.setActiveProject(getActiveProject());
    }

    public static File getActiveFile() {
        File activeFile = null;
        for(ModuleToken token : TridentWindow.projectExplorer.getSelectedTokens()) {
            if(token instanceof FileModuleToken) {
                activeFile = ((FileModuleToken) token).getFile();
                break;
            }
        }
        if(activeFile == null) {
            Tab selectedTab = TridentWindow.tabManager.getSelectedTab();
            if(selectedTab != null && selectedTab.token instanceof FileModuleToken) {
                activeFile = ((FileModuleToken) selectedTab.token).getFile();
            }
        }
        return activeFile;
    }

    public static Project getActiveProject() {
        Project selected = null;

        Tab selectedTab = TridentWindow.tabManager.getSelectedTab();

        List<ModuleToken> selectedTokens = TridentWindow.projectExplorer.getSelectedTokens();
        ArrayList<FileModuleToken> selectedFiles = new ArrayList<>();
        for(ModuleToken token : selectedTokens) {
            if(token instanceof FileModuleToken) selectedFiles.add((FileModuleToken) token);
        }

        if(selectedTab != null && selectedTab.token instanceof FileModuleToken) {
            selected = ProjectManager.getAssociatedProject(((FileModuleToken) selectedTab.token).getFile());
        } else if(selectedFiles.size() > 0) {
            selected = ProjectManager.getAssociatedProject(selectedFiles.get(0).getFile());
        }
        return selected;
    }

    public static void compileActive() {
        compile(Commons.getActiveProject());
    }

    public static void compile(Project project) {
        AbstractProcess process = project.createBuildProcess();
        process.addStartListener(p -> TridentWindow.consoleBoard.batchSubmitCommand(project.getPreActions()));
        if(process instanceof Reported) {
            Report report = ((Reported) process).getReport();
            process.addCompletionListener((p, success) -> {
                TridentWindow.noticeExplorer.setNotices(report.group());
                if (report.getTotal() > 0) TridentWindow.noticeBoard.open();
                report.getWarnings().forEach(Console.warn::println);
                report.getErrors().forEach(Console.err::println);
            });
        }
        process.addCompletionListener((p, success) -> {
            TridentWindow.consoleBoard.batchSubmitCommand(project.getPostActions());
        });


        ProcessManager.queueProcess(process);
    }

    public static void indexActive() {
        index(Commons.getActiveProject());
    }

    public static void index(Project project) {
        if(project != null) ProcessManager.queueProcess(new IndexingProcess(project));
    }

    public static CommandModule getDefaultModule() {
        return defaultModule.getValue();
    }

    /*public static BedrockModule getDefaultBedrockModule() {
        return defaultBedrockModule.getValue();
    }*/

    public static Image getProjectIcon() {
        return Commons.getIcon("project");
    }

    public static Image getScaledIcon(String icon, int width, int height) {
        width = (int) (width * ScalableGraphics2D.SCALE_FACTOR);
        height = (int) (height * ScalableGraphics2D.SCALE_FACTOR);
        Image image = getIcon(icon);
        int scaling = Image.SCALE_FAST;
        if(width < image.getWidth(null)) scaling = Image.SCALE_SMOOTH;
        return image.getScaledInstance(width, height, scaling);
    }
}
