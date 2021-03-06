package com.energyxxer.trident.global.temp.projects;

import com.energyxxer.commodore.module.Namespace;
import com.energyxxer.commodore.versioning.JavaEditionVersion;
import com.energyxxer.commodore.versioning.compatibility.VersionFeatureManager;
import com.energyxxer.enxlex.lexical_analysis.summary.ProjectSummarizer;
import com.energyxxer.enxlex.lexical_analysis.summary.ProjectSummary;
import com.energyxxer.enxlex.pattern_matching.ParsingSignature;
import com.energyxxer.enxlex.pattern_matching.matching.TokenPatternMatch;
import com.energyxxer.nbtmapper.NBTTypeMapPack;
import com.energyxxer.trident.compiler.TridentBuildConfiguration;
import com.energyxxer.trident.compiler.TridentCompiler;
import com.energyxxer.trident.compiler.TridentProjectWorker;
import com.energyxxer.trident.compiler.lexer.TridentProductions;
import com.energyxxer.trident.compiler.util.JsonTraverser;
import com.energyxxer.trident.compiler.util.TridentProjectSummarizer;
import com.energyxxer.trident.compiler.util.TridentProjectSummary;
import com.energyxxer.trident.global.Commons;
import com.energyxxer.trident.langinterface.ProjectType;
import com.energyxxer.trident.main.TridentUI;
import com.energyxxer.trident.main.window.TridentWindow;
import com.energyxxer.trident.ui.commodoreresources.DefinitionPacks;
import com.energyxxer.trident.ui.commodoreresources.TridentPlugins;
import com.energyxxer.trident.ui.commodoreresources.TypeMaps;
import com.energyxxer.trident.ui.dialogs.OptionDialog;
import com.energyxxer.trident.ui.dialogs.project_properties.ProjectProperties;
import com.energyxxer.trident.ui.modules.FileModuleToken;
import com.energyxxer.util.Lazy;
import com.energyxxer.util.StringUtil;
import com.energyxxer.util.logger.Debug;
import com.energyxxer.util.processes.AbstractProcess;
import com.google.gson.*;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class TridentProject implements Project {
    public static final ProjectType PROJECT_TYPE = new ProjectType("TRIDENT", "Trident Project") {
        @Override
        public boolean isProjectRoot(File file) {
            return file.toPath().resolve(TridentCompiler.PROJECT_FILE_NAME).toFile().exists();
        }

        @Override
        public Image getIconForRoot(File file) {
            return Commons.getIcon("project");
        }

        @Override
        public String getDefaultProjectIconName() {return "project";}

        @Override
        public Project createProjectFromRoot(File file) {
            return new TridentProject(file);
        }

        @Override
        public Project createNew(Path rootPath) {
            return new TridentProject(rootPath).createNew();
        }

        @Override
        public void showProjectPropertiesDialog(Project project) {
            ProjectProperties.show((TridentProject) project);
        }
    };

    private File rootDirectory;
    public final long instantiationTime;
    private File datapackRoot;
    private File resourceRoot;
    private final File resourceCacheFile;
    private String name;

    private final Lazy<TridentProductions> productions = new Lazy<>(() -> {
        try {
            TridentBuildConfiguration buildConfig = getBuildConfig();

            TridentProjectWorker worker = new TridentProjectWorker(rootDirectory);
            worker.setup.setupProductions = true;
            worker.setBuildConfig(buildConfig);
            worker.work();
            return worker.output.productions;
        } catch(IOException x) {
            Debug.log("Exception while creating module: " + x.toString(), Debug.MessageType.ERROR);
            x.printStackTrace();
            return null;
        }
    });

    private ArrayList<String> preActions;
    private ArrayList<String> postActions;

    private final Lazy<TridentBuildConfiguration> buildConfig = new Lazy<>(() -> {
        TridentBuildConfiguration buildConfig = new TridentBuildConfiguration();
        buildConfig.defaultDefinitionPacks = DefinitionPacks.pickPacksForVersion(this.getTargetVersion());
        buildConfig.definitionPackAliases = DefinitionPacks.getAliasMap();
        buildConfig.featureMap = VersionFeatureManager.getFeaturesForVersion(this.getTargetVersion());
        buildConfig.pluginAliases = TridentPlugins.getAliasMap();
        buildConfig.typeMapPacks = new NBTTypeMapPack[] {TypeMaps.pickTypeMapsForVersion(this.getTargetVersion())};

        JsonObject rawBuildConfig = null;
        if(ensureBuildDataExists()) {
            try {
                rawBuildConfig = buildConfig.populateFromProjectRoot(getRootDirectory());
            } catch (Exception x) {
                x.printStackTrace();
            }
        }

        JsonTraverser traverser = new JsonTraverser(rawBuildConfig);

        preActions = new ArrayList<>();
        for(JsonElement rawCommand : traverser.reset().get("trident-ui").get("actions").get("pre").iterateAsArray()) {
            if(rawCommand.isJsonPrimitive() && rawCommand.getAsJsonPrimitive().isString()) {
                String command = rawCommand.getAsString();
                if(!command.isEmpty()) {
                    preActions.add(command);
                }
            }
        }

        postActions = new ArrayList<>();
        for(JsonElement rawCommand : traverser.reset().get("trident-ui").get("actions").get("post").iterateAsArray()) {
            if(rawCommand.isJsonPrimitive() && rawCommand.getAsJsonPrimitive().isString()) {
                String command = rawCommand.getAsString();
                if(!command.isEmpty()) {
                    postActions.add(command);
                }
            }
        }

        return buildConfig;
    });

    private JsonObject projectConfigJson;
    private JsonObject buildConfigJson;
    private JavaEditionVersion targetVersion = null;
    private HashMap<String, ParsingSignature> resourceCache = new HashMap<>();

    private HashMap<String, ParsingSignature> sourceCache = new HashMap<>();

    private TridentProjectSummary summary = null;

    public TridentProject(Path rootPath) {
        instantiationTime = System.currentTimeMillis();
        this.rootDirectory = rootPath.toFile();

        datapackRoot = rootPath.resolve("datapack").toFile();
        resourceRoot = rootPath.resolve("resources").toFile();
        resourceCacheFile = rootDirectory.toPath().resolve(".tdnui").resolve("resource_cache").toFile();

        this.name = rootPath.getFileName().toString();
        //this.prefix = StringUtil.getInitials(name).toLowerCase(Locale.ENGLISH);

        Path outFolder = rootPath.resolve("out");

        JavaEditionVersion latestVersion = DefinitionPacks.getLatestKnownJavaVersion();
        if(latestVersion == null) latestVersion = new JavaEditionVersion(1, 13, 0);
        JsonArray latestVersionArr = new JsonArray(3);
        latestVersionArr.add(latestVersion.getMajor());
        latestVersionArr.add(latestVersion.getMinor());
        latestVersionArr.add(latestVersion.getPatch());

        projectConfigJson = new JsonObject();
        projectConfigJson.add("target-version", latestVersionArr);
        projectConfigJson.addProperty("default-namespace", StringUtil.getInitials(name).toLowerCase(Locale.ENGLISH));
        projectConfigJson.addProperty("language-level", 1);

        //these belong in .tdnbuild, will be updated and removed later
        projectConfigJson.addProperty("datapack-output", outFolder.resolve(name).toString());
        projectConfigJson.addProperty("resources-output", outFolder.resolve(name + "-resources.zip").toString());
        projectConfigJson.addProperty("clear-datapack-output", false);
        projectConfigJson.addProperty("clear-resources-output", false);
        projectConfigJson.addProperty("export-comments", true);
        projectConfigJson.addProperty("export-gamelog", true);

        projectConfigJson.addProperty("strict-nbt", false);
        projectConfigJson.addProperty("strict-text-components", false);
        projectConfigJson.addProperty("anonymous-function-name", "_anonymous*");
        projectConfigJson.addProperty("using-all-plugins", false);
        JsonObject loggerObj = new JsonObject();
        loggerObj.addProperty("compact", false);
        loggerObj.addProperty("timestamp-enabled", true);
        loggerObj.addProperty("line-number-enabled", false);
        loggerObj.addProperty("pos-enabled", true);
        projectConfigJson.add("game-logger", loggerObj);

        createBuildDataFromTDNProj();

        projectConfigJson.remove("datapack-output");
        projectConfigJson.remove("resources-output");
        projectConfigJson.remove("export-comments");
        projectConfigJson.remove("export-gamelog");
        projectConfigJson.remove("clear-datapack-output");
        projectConfigJson.remove("clear-resources-output");
    }

    public TridentProject(File rootDirectory) {
        instantiationTime = System.currentTimeMillis();
        this.rootDirectory = rootDirectory;

        datapackRoot = rootDirectory.toPath().resolve("datapack").toFile();
        resourceRoot = rootDirectory.toPath().resolve("resources").toFile();
        File projectConfigFile = new File(rootDirectory.getAbsolutePath() + File.separator + TridentCompiler.PROJECT_FILE_NAME);
        this.name = rootDirectory.getName();

        resourceCacheFile = rootDirectory.toPath().resolve(".tdnui").resolve("resource_cache").toFile();
        if(resourceCacheFile.exists() && resourceCacheFile.isFile()) {
            try(InputStreamReader isr = new InputStreamReader(new FileInputStream(resourceCacheFile), TridentUI.DEFAULT_CHARSET)) {
                JsonObject jsonObject = new Gson().fromJson(isr, JsonObject.class);
                if(jsonObject != null) {
                    for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                        try {
                            resourceCache.put(entry.getKey(), new ParsingSignature(entry.getValue().getAsInt()));
                        } catch (NumberFormatException | UnsupportedOperationException x) {
                            x.printStackTrace();
                        }
                    }
                }
            } catch (IOException | JsonParseException x) {
                x.printStackTrace();
            }
        }

        if(projectConfigFile.exists() && projectConfigFile.isFile()) {
            try(InputStreamReader isr = new InputStreamReader(new FileInputStream(projectConfigFile), TridentUI.DEFAULT_CHARSET)) {
                this.projectConfigJson = new Gson().fromJson(isr, JsonObject.class);

                if(this.projectConfigJson.has("target-version") && this.projectConfigJson.get("target-version").isJsonArray()) {
                    JsonArray arr = this.projectConfigJson.getAsJsonArray("target-version");

                    int major = 1;
                    int minor = 14;
                    int patch = 0;

                    if(arr.size() >= 1) {
                        JsonElement rawMajor = arr.get(0);
                        if(rawMajor.isJsonPrimitive() && rawMajor.getAsJsonPrimitive().isNumber()) {
                            major = rawMajor.getAsInt();
                        }

                        if(arr.size() >= 2) {
                            JsonElement rawMinor = arr.get(1);
                            if(rawMinor.isJsonPrimitive() && rawMinor.getAsJsonPrimitive().isNumber()) {
                                minor = rawMinor.getAsInt();
                            }

                            if(arr.size() >= 3) {
                                JsonElement rawPatch = arr.get(2);
                                if(rawPatch.isJsonPrimitive() && rawPatch.getAsJsonPrimitive().isNumber()) {
                                    patch = rawPatch.getAsInt();
                                }
                            }
                        }
                    }

                    targetVersion = new JavaEditionVersion(major, minor, patch);
                }
            } catch (IOException | JsonParseException x) {
                x.printStackTrace();
            }

            File buildConfigFile = rootDirectory.toPath().resolve(TridentCompiler.PROJECT_BUILD_FILE_NAME).toFile();
            if(buildConfigFile.exists()) {

                try(InputStreamReader isr = new InputStreamReader(new FileInputStream(buildConfigFile), TridentUI.DEFAULT_CHARSET)) {
                    this.buildConfigJson = new Gson().fromJson(isr, JsonObject.class);
                    return;
                } catch (IOException | JsonParseException x) {
                    x.printStackTrace();
                }
            }

            return;
        }
        this.rootDirectory = null;
        throw new RuntimeException("Invalid configuration file.");
    }

    public TokenPatternMatch getFileStructure() {
        return productions.getValue().FILE;
    }

    public void rename(String name) throws IOException {
        File newFile = new File(ProjectManager.getWorkspaceDir() + File.separator + name);
        if(newFile.exists()) {
            throw new IOException("A project by that name already exists!");
        }
        this.name = name;
        updateConfig();
    }

    public boolean canFlatten(File file) {
        if(getRelativePath(file) == null) return true;
        if(!file.getParentFile().equals(this.getRootDirectory())) return true;
        return !Arrays.asList("src","resources","data").contains(file.getName());
    }

    public void updateConfig() {
        this.datapackRoot.mkdirs();
        File projectConfigFile = new File(rootDirectory.getAbsolutePath() + File.separator + TridentCompiler.PROJECT_FILE_NAME);
        File buildConfigFile = new File(rootDirectory.getAbsolutePath() + File.separator + TridentCompiler.PROJECT_BUILD_FILE_NAME);

        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            try(PrintWriter writer = new PrintWriter(projectConfigFile, "UTF-8")) {
                writer.print(gson.toJson(this.projectConfigJson));
            } catch (FileNotFoundException | UnsupportedEncodingException x) {
                x.printStackTrace();
                TridentWindow.showException(x);
            }

            if(!projectConfigFile.exists()) projectConfigFile.createNewFile();
            if(!buildConfigFile.exists()) projectConfigFile.createNewFile();

            try(PrintWriter writer = new PrintWriter(buildConfigFile, "UTF-8")) {
                writer.print(gson.toJson(this.buildConfigJson));
            } catch (FileNotFoundException | UnsupportedEncodingException x) {
                x.printStackTrace();
                TridentWindow.showException(x);
            }
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    private boolean exists() {
        return rootDirectory != null && rootDirectory.exists();
    }

    private TridentProject createNew() {
        Path defaultFunctionsDir = datapackRoot.toPath().resolve("data").resolve(getDefaultNamespace()).resolve("functions");

        defaultFunctionsDir.toFile().mkdirs();

        File mainFunctionPath = defaultFunctionsDir.resolve("main.tdn").toFile();
        try {
            mainFunctionPath.createNewFile();

            try(PrintWriter writer = new PrintWriter(mainFunctionPath, "UTF-8")) {
                writer.print("@ tag load\n\nsay Hello World!");
            } catch (IOException x) {
                Debug.log(x.getMessage());
                TridentWindow.showException(x);
            }
        } catch (IOException x) {
            x.printStackTrace();
            TridentWindow.showException(x);
        }

        TridentWindow.tabManager.openTab(new FileModuleToken(mainFunctionPath));

        return this;
    }

    public String getRelativePath(File file) {
        if(!file.getAbsolutePath().startsWith((rootDirectory.getAbsolutePath()+File.separator))) return null;
        return file.getAbsolutePath().substring((rootDirectory.getAbsolutePath()+File.separator).length());
    }

    public void updateClientDataCache(HashMap<String, ParsingSignature> resourceCache) {
        this.resourceCache = resourceCache;

        JsonObject jsonObj = new JsonObject();
        for(Map.Entry<String, ParsingSignature> entry : resourceCache.entrySet()) {
            jsonObj.addProperty(entry.getKey(), entry.getValue().getHashCode());
        }

        try {
            resourceCacheFile.getParentFile().mkdirs();
            resourceCacheFile.createNewFile();
        } catch(IOException x) {
            Debug.log(x.getMessage());
            TridentWindow.showException(x);
        }

        try(PrintWriter writer = new PrintWriter(resourceCacheFile, "UTF-8")) {
            writer.print(new GsonBuilder().setPrettyPrinting().create().toJson(jsonObj));
        } catch (IOException x) {
            Debug.log(x.getMessage());
            TridentWindow.showException(x);
        }
    }

    public void clearClientDataCache() {
        updateClientDataCache(new HashMap<>());
        Debug.log("Client Data Cache for project '" + getName() + "' cleared.");
    }

    public void updateServerDataCache(HashMap<String, ParsingSignature> sourceCache) {
        this.sourceCache = sourceCache;
    }

    public void updateSummary(ProjectSummary summary) {
        this.summary = (TridentProjectSummary) summary;
    }

    public TridentProjectSummary getSummary() {
        return summary;
    }

    public HashMap<String, ParsingSignature> getSourceCache() {
        return sourceCache;
    }

    public HashMap<String, ParsingSignature> getResourceCache() {
        return resourceCache;
    }

    public File getRootDirectory() {
        return rootDirectory;
    }

    public File getServerDataRoot() {
        return datapackRoot;
    }

    public File getClientDataRoot() {
        return resourceRoot;
    }

    public String getName() {
        return name;
    }

    @Override
    public JavaEditionVersion getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(@Nullable JavaEditionVersion version) {
        targetVersion = version;
        if(version != null) {
            JsonArray versionArr = new JsonArray(3);
            versionArr.add(version.getMajor());
            versionArr.add(version.getMinor());
            versionArr.add(version.getPatch());
            projectConfigJson.add("target-version", versionArr);
        } else {
            projectConfigJson.remove("target-version");
        }
    }

    public int getLanguageLevel() {
        if(projectConfigJson.has("language-level") && projectConfigJson.get("language-level").isJsonPrimitive() && projectConfigJson.get("language-level").getAsJsonPrimitive().isNumber()) {
            int level = Math.max(1, Math.min(3, projectConfigJson.get("language-level").getAsInt()));
            projectConfigJson.addProperty("language-level", level);
            return level;
        }
        projectConfigJson.addProperty("language-level", 1);
        return 1;
    }

    public void setLanguageLevel(int level) {
        projectConfigJson.addProperty("language-level", Math.max(1, Math.min(3, level)));
    }

    public String getDefaultNamespace() {
        if(projectConfigJson.has("default-namespace") && projectConfigJson.get("default-namespace").isJsonPrimitive() && projectConfigJson.get("default-namespace").getAsJsonPrimitive().isString()) {
            String namespace = projectConfigJson.get("default-namespace").getAsString();
            if(namespace.matches(Namespace.ALLOWED_NAMESPACE_REGEX)) {
                return namespace;
            }
        }
        String namespace = StringUtil.getInitials(this.getName()).toLowerCase(Locale.ENGLISH);
        projectConfigJson.addProperty("default-namespace", namespace);
        return namespace;
    }

    public void setDefaultNamespace(String namespace) {
        if(namespace.matches(Namespace.ALLOWED_NAMESPACE_REGEX)) {
            projectConfigJson.addProperty("default-namespace", namespace);
        }
    }

    public String getAnonymousFunctionName() {
        if(projectConfigJson.has("anonymous-function-name") && projectConfigJson.get("anonymous-function-name").isJsonPrimitive() && projectConfigJson.get("anonymous-function-name").getAsJsonPrimitive().isString()) {
            return projectConfigJson.get("anonymous-function-name").getAsString();
        }
        String defaultValue = "_anonymous*";
        projectConfigJson.addProperty("anonymous-function-name", defaultValue);
        return defaultValue;
    }

    public void setAnonymousFunctionName(String value) {
        projectConfigJson.addProperty("anonymous-function-name", value);
    }

    public boolean isStrictNBT() {
        if(projectConfigJson.has("strict-nbt") && projectConfigJson.get("strict-nbt").isJsonPrimitive() && projectConfigJson.get("strict-nbt").getAsJsonPrimitive().isBoolean()) {
            return projectConfigJson.get("strict-nbt").getAsBoolean();
        }
        projectConfigJson.addProperty("strict-nbt", false);
        return false;
    }

    public void setStrictNBT(boolean strict) {
        projectConfigJson.addProperty("strict-nbt", strict);
    }

    public boolean isStrictTextComponents() {
        if(projectConfigJson.has("strict-text-components") && projectConfigJson.get("strict-text-components").isJsonPrimitive() && projectConfigJson.get("strict-text-components").getAsJsonPrimitive().isBoolean()) {
            return projectConfigJson.get("strict-text-components").getAsBoolean();
        }
        projectConfigJson.addProperty("strict-text-components", false);
        return false;
    }

    public void setStrictTextComponents(boolean strict) {
        projectConfigJson.addProperty("strict-text-components", strict);
    }

    public File getDataOut() {
        String path = JsonTraverser.INSTANCE.reset(buildConfigJson).get("output").get("directories").get("data-pack").asString();
        if(path != null) {
            return TridentCompiler.newFileObject(path, rootDirectory);
        }
        return null;
    }

    public void setDataOut(File file) {
        JsonObject directories = JsonTraverser.INSTANCE.reset(buildConfigJson).createOnTraversal().get("output").get("directories").asJsonObject();

        if(file != null) {
            directories.addProperty("data-pack", file.getPath());
        } else {
            directories.remove("data-pack");
        }
    }

    public File getResourcesOut() {
        String path = JsonTraverser.INSTANCE.reset(buildConfigJson).get("output").get("directories").get("resource-pack").asString();
        if(path != null) {
            return TridentCompiler.newFileObject(path, rootDirectory);
        }
        return null;
    }

    public void setResourcesOut(File file) {
        JsonObject directories = JsonTraverser.INSTANCE.reset(buildConfigJson).createOnTraversal().get("output").get("directories").asJsonObject();

        if(file != null) {
            directories.addProperty("resource-pack", file.getPath());
        } else {
            directories.remove("resource-pack");
        }
    }

    public boolean isExportComments() {
        return JsonTraverser.INSTANCE.reset(buildConfigJson).get("output").get("export-comments").asBoolean(false);
    }

    public void setExportComments(boolean value) {
        JsonTraverser.INSTANCE.reset(buildConfigJson).createOnTraversal().get("output").asJsonObject().addProperty("export-comments", value);
    }

    public boolean isExportGamelog() {
        return JsonTraverser.INSTANCE.reset(buildConfigJson).get("output").get("export-gamelog").asBoolean(true);
    }

    public void setExportGamelog(boolean value) {
        JsonTraverser.INSTANCE.reset(buildConfigJson).createOnTraversal().get("output").asJsonObject().addProperty("export-gamelog", value);
    }

    public boolean isClearData() {
        return JsonTraverser.INSTANCE.reset(buildConfigJson).get("output").get("clean-directories").get("data-pack").asBoolean(false);
    }

    public void setClearData(boolean clear) {
        JsonTraverser.INSTANCE.reset(buildConfigJson).createOnTraversal().get("output").get("clean-directories").asJsonObject().addProperty("data-pack", clear);
    }

    public boolean isClearResources() {
        return JsonTraverser.INSTANCE.reset(buildConfigJson).get("output").get("clean-directories").get("resource-pack").asBoolean(false);
    }

    public void setClearResources(boolean clear) {
        JsonTraverser.INSTANCE.reset(buildConfigJson).createOnTraversal().get("output").get("clean-directories").asJsonObject().addProperty("resource-pack", clear);
    }

    public JsonObject getProjectConfigJson() {
        return projectConfigJson;
    }

    @Override
    public String toString() {
        return "Project [" + name + "]";
    }

    public void setName(String name) {
        this.name = name;
    }

    private boolean ensureBuildDataExists() {
        File buildDataFile = rootDirectory.toPath().resolve(TridentCompiler.PROJECT_BUILD_FILE_NAME).toFile();
        if(!buildDataFile.exists()) {
            String confirmation = new OptionDialog("New Project Format", "Project \"" + name + "\" is using an outdated settings format. Update it now?", new String[] {"Update this project", "Update all projects", "Not now"}).result;
            if(confirmation == null) return false;
            switch(confirmation) {
                case "Update this project": {
                    createBuildDataFromTDNProj();
                    break;
                }
                case "Update all projects": {
                    for(Project project : ProjectManager.getLoadedProjects()) {
                        if(project instanceof TridentProject) {
                            ((TridentProject) project).createBuildDataFromTDNProj();
                        }
                    }
                    break;
                }
                default: {
                    return false;
                }
            }
        }
        return true;
    }

    private void createBuildDataFromTDNProj() {
        buildConfigJson = new JsonObject();

        buildConfigJson.addProperty("input-resources", "(automatically selected by Trident-UI)");

        JsonObject outputObj = new JsonObject();
        buildConfigJson.add("output", outputObj);

        JsonObject directoriesObj = new JsonObject();
        outputObj.add("directories", directoriesObj);

        JsonObject cleanDirectoriesObj = new JsonObject();
        outputObj.add("clean-directories", cleanDirectoriesObj);

        directoriesObj.add("data-pack", projectConfigJson.get("datapack-output"));
        directoriesObj.add("resource-pack", projectConfigJson.get("resources-output"));
        cleanDirectoriesObj.add("data-pack", projectConfigJson.get("clear-datapack-output"));
        cleanDirectoriesObj.add("resource-pack", projectConfigJson.get("clear-resources-output"));

        outputObj.add("export-comments", projectConfigJson.get("export-comments"));
        outputObj.add("export-gamelog", projectConfigJson.get("export-gamelog"));

        JsonObject tridentUIObj = new JsonObject();
        buildConfigJson.add("trident-ui", tridentUIObj);

        JsonObject actionsObj = new JsonObject();
        tridentUIObj.add("actions", actionsObj);

        actionsObj.add("pre", new JsonArray());
        actionsObj.add("post", new JsonArray());

        updateConfig();
        Debug.log("Created " + TridentCompiler.PROJECT_BUILD_FILE_NAME + " for \"" + name + "\"");
    }

    public TridentBuildConfiguration getBuildConfig() {
        return buildConfig.getValue();
    }

    @Override
    public Iterable<String> getPreActions() {
        return preActions;
    }

    @Override
    public Iterable<String> getPostActions() {
        return postActions;
    }

    public ProjectType getProjectType() {
        return TridentProject.PROJECT_TYPE;
    }

    @Override
    public ProjectSummarizer createProjectSummarizer() {
        TridentBuildConfiguration buildConfig = this.getBuildConfig();

        ProjectSummarizer summarizer = new TridentProjectSummarizer(
                this.getRootDirectory(),
                buildConfig
        );
        summarizer.setSourceCache(this.getSourceCache());
        summarizer.addCompletionListener(() -> {
            this.updateServerDataCache(summarizer.getSourceCache());
            this.updateSummary(summarizer.getSummary());
        });
        return summarizer;
    }

    @Override
    public Image getIconForFile(File file) {
        if(file.isDirectory()) {
            if(PROJECT_TYPE.isProjectRoot(file.getParentFile())) {
                if(file.getName().equals("datapack")) return Commons.getIcon("data");
                if(file.getName().equals("resources")) return Commons.getIcon("resources");
            }
        } else {
            String extension = "";
            if(file.getName().lastIndexOf(".") >= 0) {
                extension = file.getName().substring(file.getName().lastIndexOf("."));
            }
            switch(extension) {
                case ".json": {
                    if(file.getName().equals("sounds.json"))
                        return Commons.getIcon("sound_config");
                    else if(file.getParentFile().getName().equals("blockstates"))
                        return Commons.getIcon("blockstate");
                    else if(file.getParentFile().getName().equals("lang"))
                        return Commons.getIcon("lang");
                    break;
                }
                case ".mcmeta":
                case TridentCompiler.PROJECT_FILE_NAME:
                case TridentCompiler.PROJECT_BUILD_FILE_NAME:
                    return Commons.getIcon("meta");
                case ".nbt":
                    return Commons.getIcon("structure");
            }
        }
        return null;
    }

    @Override
    public AbstractProcess createBuildProcess() {
        TridentCompiler compiler = new TridentCompiler(this.getRootDirectory());

        compiler.setBuildConfig(this.getBuildConfig());

        compiler.setSourceCache(this.getSourceCache());
        compiler.setInResourceCache(this.getResourceCache());

        return compiler;
    }

    @Override
    public long getInstantiationTime() {
        return instantiationTime;
    }
}
