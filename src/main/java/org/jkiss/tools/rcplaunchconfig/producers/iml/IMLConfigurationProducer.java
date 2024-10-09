package org.jkiss.tools.rcplaunchconfig.producers.iml;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.tools.rcplaunchconfig.*;
import org.jkiss.tools.rcplaunchconfig.p2.P2RepositoryManager;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2BundleInfo;
import org.jkiss.tools.rcplaunchconfig.producers.DevPropertiesProducer;
import org.jkiss.tools.rcplaunchconfig.util.BundleUtils;
import org.jkiss.tools.rcplaunchconfig.util.FileUtils;
import org.jkiss.tools.rcplaunchconfig.util.Version;
import org.jkiss.tools.rcplaunchconfig.util.VersionRange;
import org.jkiss.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class IMLConfigurationProducer {
    public static final Logger log = LoggerFactory.getLogger(IMLConfigurationProducer.class);

    public static final IMLConfigurationProducer INSTANCE = new IMLConfigurationProducer();
    public static final String TEST_FOLDER = "src/test/java";
    private final Map<Pair<String, VersionRange>, Set<BundleInfo>> bundlePackageImports = new ConcurrentHashMap<>();

    private final Set<String> generatedLibraries = new LinkedHashSet<>();
    private final Set<Path> rootModules = new LinkedHashSet<>();
    private final Set<ModuleInfo> modules = new LinkedHashSet<>();

    Set<Path> createdModules = new LinkedHashSet<>();

    private final Lock lock = new ReentrantLock();

    private final Map<Path, Result> products = new LinkedHashMap<>();

    /**
     * Generates IML configuration from the result
     *
     * @param result      result of dependency resolving
     * @param productPath path to product file
     * @throws IOException file access error
     */
    public void generateIMLFiles(@NotNull Result result, @Nullable Path productPath) throws IOException {
        log.info("Generating IML configuration in " + PathsManager.INSTANCE.getImlModulesPath());
        // Bundles
        List<ModuleInfo> modules = new ArrayList<>();
        for (Set<BundleInfo> bundles : result.getBundlesByNames().values()) {
            for (BundleInfo bundleInfo : bundles) {
                if (this.modules.contains(bundleInfo)) {
                    continue;
                }
                if (DevPropertiesProducer.isBundleAcceptable(bundleInfo.getBundleName())) {
                    String moduleConfig = generateIMLBundleConfig(bundleInfo, result);
                    modules.add(bundleInfo);
                    Path imlFilePath = getImlModulePath(bundleInfo);
                    createConfigFile(imlFilePath, moduleConfig);
                }
            }
        }

        // Features
        for (FeatureInfo featureInfo : result.getResolvedFeatures().values()) {
            if (DevPropertiesProducer.isBundleAcceptable(featureInfo.getFeatureName())) {
                String moduleConfig = generateIMLFeatureConfig(featureInfo, result);
                modules.add(featureInfo);
                Path imlFilePath = getImlModulePath(featureInfo);
                createConfigFile(imlFilePath, moduleConfig);
            }
        }


        if (productPath != null) {
            products.put(productPath, result);
        }
        log.info(modules.size() + " module IML configs associated for " + result.getProductName());
        this.modules.addAll(modules);
        rootModules.addAll(generateRootModules());
    }

    public void generateImplConfiguration() throws IOException {
        String modulesConfig = generateModulesConfig();
        createConfigFile(getImplModuleConfigPath(), modulesConfig);
        createRunConfiguration();
        processAdditionalConfigFiles();
    }

    private void processAdditionalConfigFiles() throws IOException {
        List<Path> ideaConfigurationFiles = PathsManager.INSTANCE.getIdeaConfigurationFiles();
        if (ideaConfigurationFiles != null) {
            for (Path ideaConfigurationFile : ideaConfigurationFiles) {

                Path newLocationRoot = PathsManager.INSTANCE.getImlModulesPath();
                Path oldLocation = PathsManager.INSTANCE.getProjectsFolderPath().relativize(ideaConfigurationFile);
                Path oldLocationRoot = oldLocation;
                while (oldLocationRoot.getParent() != null) {
                    oldLocationRoot = oldLocationRoot.getParent();
                }
                Path file = newLocationRoot.resolve(oldLocationRoot.relativize(oldLocation));
                if (ideaConfigurationFile.toFile().isDirectory()) {
                    FileUtils.copyFolder(ideaConfigurationFile, PathsManager.INSTANCE.getImlModulesPath(), true);
                } else {
                    Files.copy(ideaConfigurationFile, file, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void createRunConfiguration() throws IOException {
        for (Map.Entry<Path, Result> pathPairEntry : products.entrySet()) {
            String config =
                generateLaunchConfig(
                    pathPairEntry.getKey(),
                    pathPairEntry.getValue()
                );
            createConfigFile(getXMLRunConfigurationPath()
                .resolve("RUN_" + pathPairEntry.getValue().getProductName().replace(" ", "_") + ".xml"), config);
        }
    }

    private String generateLaunchConfig(Path productPath, Result result) {
        StringBuilder config = new StringBuilder();
        config.append("<component name=\"ProjectRunConfigurationManager\">\n");
        config.append(String.format("  <configuration default=\"false\" name=\"Run " + result.getProductName() + " \" type=\"Application\" factoryName=\"Application\">\n"));
        config.append("    <option name=\"ALTERNATIVE_JRE_PATH\" value=\"17\" />\n");
        config.append("    <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"true\" />\n");
        config.append("    <option name=\"MAIN_CLASS_NAME\" value=\"org.jkiss.dbeaver.launcher.DBeaverLauncher\" />\n");
        config.append("    <module name=\"org.jkiss.dbeaver.launcher\" />\n");
        buildProgramParameters(config, productPath, result);
        config.append("    <option name=\"VM_PARAMETERS\" value=\"");
        if (result.getArguments().getVmARGS() != null) {
            for (String programARG : result.getArguments().getVmARGS()) {
                config.append(programARG).append(" ");
            }
        }
        if (isMacOS()) {
            if (result.getArguments().getVmARGSMac() != null) {
                for (String s : result.getArguments().getVmARGSMac()) {
                    config.append(s).append(" ");
                }
            }
        }
        config.append("\"/>\n");
        config.append("    <option name=\"WORKING_DIRECTORY\" value=").append(result.getWorkDir() != null ? "\""
            + result.getWorkDir() + "\"" : "\"$MODULE_WORKING_DIR$\"").append("/>\n");
        config.append("    <shortenClasspath name=\"ARGS_FILE\" />\n");
        config.append("    <method v=\"2\" />\n")
            .append("  </configuration>\n")
            .append("</component>");
        return config.toString();
    }

    private void buildProgramParameters(StringBuilder config, Path productPath, Result result) {
        config.append("    <option name=\"PROGRAM_PARAMETERS\" value=\"-name ");
        config.append(result.getProductName()).append(" ");
        config.append("-product ");
        config.append(result.getProductId()).append(" ");
        config.append("-configuration &quot;");
        config.append(getFormattedRelativePath(productPath, false, true, true)).append("&quot; ");
        config.append("-dev &quot;");
        config.append(getFormattedRelativePath(productPath, false, true, true)).append("/dev.properties").append("&quot; ");
        config.append("-nl en -consoleLog -showsplash ");
        if (result.getArguments().getProgramARGS() != null) {
            for (String programARG : result.getArguments().getProgramARGS()) {
                config.append(programARG).append(" ");
            }
        }
        if (isMacOS()) {
            if (result.getArguments().getGetProgramARGSMacOS() != null) {
                for (String s : result.getArguments().getGetProgramARGSMacOS()) {
                    config.append(s).append(" ");
                }
            }
        }
        config.append("-vmargs ");
        config.append("-Xmx4096M ");
        config.append("\"/>\n");
    }

    private Set<Path> generateRootModules() throws IOException {
        Set<Path> presentModules = PathsManager.INSTANCE.getModulesRoots().stream().filter(it -> it.toFile().exists()).collect(Collectors.toSet());
        Set<Path> rootModules = new LinkedHashSet<>();
        Path imlModuleRoot = PathsManager.INSTANCE.getImlModulesPath();
        for (Path presentModule : presentModules) {
            String rootModuleConfig = generateRootModule(presentModule);
            Path rootIml = imlModuleRoot.resolve(presentModule.getFileName() + ".iml");
            rootModules.add(rootIml);
            createConfigFile(rootIml, rootModuleConfig);
        }
        Path rootIml = imlModuleRoot.resolve(imlModuleRoot.getFileName() + ".iml");
        createConfigFile(rootIml, generateImlRepositoryRootModule(imlModuleRoot));
        rootModules.add(rootIml);
        return rootModules;
    }

    public static boolean isMacOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.contains("mac");
    }

    private String generateImlRepositoryRootModule(@NotNull Path imlRoot) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<module type=\"WEB_MODULE\" version=\"4\">\n" +
            "  <component name=\"NewModuleRootManager\" inherit-compiler-output=\"true\">\n" +
            "    <exclude-output />\n" +
            "    <content url=\"file://$MODULE_DIR$/../" + imlRoot.getFileName() + "\" />\n" +
            "    <orderEntry type=\"inheritedJdk\" />\n" +
            "    <orderEntry type=\"sourceFolder\" forTests=\"false\" />\n" +
            "  </component>\n" +
            "</module>";
    }

    private String generateRootModule(@NotNull Path presentModule) {
        StringBuilder productExcludes = new StringBuilder();
        Map<Path, String> productsPathsAndWorkDirs = PathsManager.INSTANCE.getProductsPathsAndWorkDirs();
        for (Path productConfigPath : productsPathsAndWorkDirs.keySet()) {
            if (productConfigPath.startsWith(presentModule)) {
                productExcludes.append("        <excludeFolder url=\"")
                    .append(getFormattedRelativePath(presentModule, false, false))
                    .append("/").append(presentModule.relativize(productConfigPath.getParent()).toString().replace("\\", "/"))
                    .append("/target\"/>\n");
            }
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<module type=\"JAVA_MODULE\" version=\"4\">\n" +
            "  <component name=\"NewModuleRootManager\">\n" +
            "    <output url=\"file://$MODULE_DIR$/target/classes\" />\n" +
            "    <output-test url=\"file://$MODULE_DIR$/target/classes\" />\n" +
            "    <content url =\"" + getFormattedRelativePath(presentModule, false, false) + "\">\n" +
            productExcludes +
            "    </content>\n" +
            "    <orderEntry type=\"inheritedJdk\" />\n" +
            "    <orderEntry type=\"sourceFolder\" forTests=\"false\" />\n" +
            "  </component>\n" +
            "</module>";
    }

    /**
     * Add imported by package bundle to the list
     */
    public void addRequiredBundleforPackage(@NotNull Pair<String, VersionRange> packageName, @NotNull BundleInfo bundleInfo) {
        bundlePackageImports.computeIfAbsent(packageName, it -> new LinkedHashSet<>()).add(bundleInfo);
    }

    private void createConfigFile(@NotNull Path configPath, @NotNull String libraryConfig) throws IOException {
        while (!lock.tryLock()) {
            Thread.onSpinWait();
        }
        try {
            if (createdModules.contains(configPath)) {
                return;
            }
            Files.deleteIfExists(configPath);
            Files.createDirectories(configPath.getParent());
            Files.createFile(configPath);
            if (Files.exists(configPath)) {
                try (PrintWriter out = new PrintWriter(configPath.toFile())) {
                    out.print(libraryConfig);
                }
            }
            createdModules.add(configPath);
        } finally {
            lock.unlock();
        }
    }

    private String generateModulesConfig() throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<project version=\"4\">\n");
        builder.append("  <component name=\"ProjectModuleManager\">\n");
        builder.append("    <modules>\n");
        for (Path rootModule : rootModules) {
            builder.append("      <module fileurl=\"file://$PROJECT_DIR$/")
                .append(rootModule.getFileName()).append("\" filepath=\"$PROJECT_DIR$/")
                .append(rootModule.getFileName()).append("\"/>\n");
        }
        for (ModuleInfo module : modules) {
            builder.append("      <module fileurl=\"file://$PROJECT_DIR$/")
                .append(module.getModuleName()).append(".iml\"").append(" filepath=\"$PROJECT_DIR$/")
                .append(module.getModuleName()).append(".iml").append("\"/>\n");
        }
        processAdditionalIMLModules(builder);
        builder.append("    </modules>\n");
        builder.append("  </component>\n");
        builder.append("</project>");
        return builder.toString();
    }

    private void processAdditionalIMLModules(StringBuilder builder) throws IOException {
        List<Path> additionalIMlModules = PathsManager.INSTANCE.getAdditionalIMlModules();
        if (additionalIMlModules == null) {
            return;
        }
        Path imlModulesPath = PathsManager.INSTANCE.getImlModulesPath();
        for (Path additionalIMlModule : additionalIMlModules) {
            processModulePath(builder, additionalIMlModule, imlModulesPath);
        }
    }

    private void processModulePath(StringBuilder builder, Path sourceFile, Path imlModulesPath)
        throws IOException {
        if (sourceFile.toFile().isDirectory()) {
            try (Stream<Path> walk = Files.walk(sourceFile)) {
                for (Path path : walk.toList()) {
                    Path destination = Paths.get(imlModulesPath.resolve(sourceFile.getFileName()).toString(), path.toString()
                        .substring(sourceFile.toString().length()));
                    try {
                        Files.copy(
                            path,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING
                        );
                    } catch (DirectoryNotEmptyException | FileAlreadyExistsException ignore) {

                    } catch (IOException e) {
                        log.error("Error transferring data", e);
                    }
                    if (!path.toFile().isDirectory()) {
                        builder.append("      <module fileurl=\"file://$PROJECT_DIR$/")
                            .append(imlModulesPath.relativize(destination).toString().replace("\\", "/")).append("\"")
                            .append(" filepath=\"$PROJECT_DIR$/").append(imlModulesPath.relativize(destination).toString().replace("\\", "/")).append("\"/>\n");
                    }
                }
            }
        } else {
            Path fileName = sourceFile.getFileName();
            Files.copy(sourceFile, imlModulesPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            builder.append("      <module fileurl=\"file://$PROJECT_DIR$/")
                .append(fileName).append("\"").append(" filepath=\"$PROJECT_DIR$/")
                .append(fileName).append("\"/>\n");
        }
    }

    private void appendLibraryInfo(
        @NotNull StringBuilder builder,
        @NotNull BundleInfo bundleInfo,
        @NotNull Result result,
        @NotNull Set<Pair<String, Version>> resolvedBundles,
        boolean isLibrary) {
        resolvedBundles.add(new Pair<>(bundleInfo.getBundleName(), new Version(bundleInfo.getBundleVersion())));
        if (bundleInfo.getPath().toFile().isDirectory()) {
            List<String> classpathLibs = bundleInfo.getClasspathLibs();
            if (!classpathLibs.isEmpty()) {
                for (String classpathLib : classpathLibs) {
                    appendClasspathLib(builder, bundleInfo, classpathLib, isLibrary);
                }
            } else {
                builder.append("      <root url=\"").append(getFormattedRelativePath(bundleInfo.getPath(), false, isLibrary)).append("\"/>\n");
            }
        } else {
            builder.append("      <root url=\"").append(getFormattedRelativePath(bundleInfo.getPath(), true, isLibrary)).append("\"/>\n");
        }
        for (BundleInfo fragment : bundleInfo.getFragments()) {
            appendLibraryInfo(builder, fragment, result, resolvedBundles, isLibrary);
        }
        if (!bundleInfo.getReexportedBundles().isEmpty()) {
            for (String reexportedBundle : bundleInfo.getReexportedBundles()) {
                Set<BundleInfo> bundlesByName = result.getBundlesByName(reexportedBundle);
                Optional<Pair<String, VersionRange>> bundleRequirements = bundleInfo.getRequireBundles().stream().filter(it -> it.getFirst().equals(reexportedBundle)).findFirst();
                if (bundleRequirements.isEmpty() || bundlesByName == null) {
                    log.warn("Missing reexported bundle " + reexportedBundle);
                    continue;
                }
                BundleInfo suitableBundle = bundlesByName.stream().filter(it -> VersionRange.isVersionsCompatible(bundleRequirements.get().getSecond(), new Version(it.getBundleVersion()))).findFirst().orElseThrow();
                appendLibraryInfo(builder, suitableBundle, result, resolvedBundles, isLibrary);
            }
            for (Pair<String, VersionRange> importPackage : bundleInfo.getImportPackages()) {
                if (bundlePackageImports.get(importPackage) != null) {
                    for (BundleInfo info : bundlePackageImports.get(importPackage)) {
                        appendLibraryInfo(builder, info, result, resolvedBundles, isLibrary);
                    }
                }
            }
        }
    }

    private void appendClasspathLib(@NotNull StringBuilder builder,
                                    @NotNull BundleInfo bundleInfo,
                                    @NotNull String classpathLib,
                                    boolean isLibrary) {
        builder.append("      <root url=\"")
            .append(getFormattedRelativePath(bundleInfo.getPath().resolve(classpathLib), true, isLibrary))
            .append("\"/>\n");
    }

    private String getFormattedRelativePath(@NotNull Path pathToFormat,
                                            boolean jar,
                                            boolean useProjectDir,
                                            boolean launchConfig) {
        String type = jar ? "jar:" : "file:";
        if (!launchConfig) {
            type += "//";
        }
        String prefix = type + (useProjectDir ? "$PROJECT_DIR$/../../" : "$MODULE_DIR$/../../");
        return prefix + getRelativizedPath(pathToFormat).toString().replace("\\", "/") + (jar ? "!/" : "");
    }

    private String getFormattedRelativePath(@NotNull Path pathToFormat, boolean jar, boolean useProjectDir) {
        return getFormattedRelativePath(pathToFormat, jar, useProjectDir, false);
    }


    @NotNull
    private Path getRelativizedPath(@NotNull Path bundlePath) {
        return PathsManager.INSTANCE.getEclipsePath().getParent().getParent().relativize(bundlePath);
    }

    @Nullable
    private String generateIMLBundleConfig(@NotNull BundleInfo bundleInfo, @NotNull Result result) throws IOException {
        if (bundleInfo.getPath() == null) {
            log.warn("Bundle doesn't contain any data");
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<module type=\"JAVA_MODULE\" version=\"4\">\n");
        builder.append(" <component name=\"NewModuleRootManager\">\n");
        Properties properties = readBuildConfiguration(bundleInfo.getPath());
        List<String> outputs = properties.get("output..") != null ? List.of(((String) properties.get("output..")).split(",")) : List.of();
        if (!outputs.isEmpty()) {
            builder.append("  <output url=\"").append(getFormattedRelativePath(bundleInfo.getPath().resolve(outputs.get(0)), false, false))
                .append("\"/>").append("\n");
        }
        builder.append("  <exclude-output/>").append("\n");
        List<String> sources = properties.get("source..") != null ? List.of(((String) properties.get("source..")).split(",")) : List.of();
        boolean hasContent = !sources.isEmpty() && !sources.get(0).isEmpty();

        if (hasContent) {
            builder.append("  <content url=\"").append(getFormattedRelativePath(bundleInfo.getPath(), false, false)).append("\">")
                .append("\n");
            for (String source : sources) {
                builder.append("   <sourceFolder url=\"")
                    .append(getFormattedRelativePath(bundleInfo.getPath().resolve(source), false, false))
                    .append("\"/>").append("\n");
            }
            builder.append("   <excludeFolder url=\"")
                .append(getFormattedRelativePath(bundleInfo.getPath().resolve("target"), false, false))
                .append("\"/>").append("\n");

            for (String output : outputs) {
                builder.append("   <excludeFolder url=\"")
                    .append(getFormattedRelativePath(bundleInfo.getPath().resolve(output), false, false))
                    .append("\"/>").append("\n");
            }
            if (bundleInfo.getPath() != null) {
                appendTestSources(bundleInfo.getPath(), builder);
            }
            builder.append("  </content>").append("\n");
        }
        builder.append("  <orderEntry type=\"inheritedJdk\" />").append("\n");
        builder.append("  <orderEntry type=\"sourceFolder\" forTests=\"false\" />").append("\n");
        LinkedHashSet<Pair<String, Version>> resolvedBundles = new LinkedHashSet<>();
        for (Pair<String, VersionRange> requireBundle : bundleInfo.getRequireBundles()) {
            appendBundleInfo(bundleInfo, requireBundle, builder, result, resolvedBundles);
        }
        if (bundleInfo.getFragmentHost() != null) {
            appendBundleInfo(bundleInfo, bundleInfo.getFragmentHost(), builder, result, resolvedBundles);
        }
        for (Pair<String, VersionRange> importPackage : bundleInfo.getImportPackages()) {
            if (bundlePackageImports.get(importPackage) != null) {
                for (BundleInfo info : bundlePackageImports.get(importPackage)) {
                    appendBundleInfo(bundleInfo, new Pair<>(info.getBundleName(), importPackage.getSecond()), builder, result, resolvedBundles);
                }
            }
        }
        if (bundleInfo.getPath() != null && bundleInfo.getPath().resolve(TEST_FOLDER).toFile().exists()) {
            Set<String> testLibraries = PathsManager.INSTANCE.getTestLibraries();
            if (testLibraries != null) {
                for (String testLibrary : testLibraries) {
                    addModuleLibrary(new Pair<>(testLibrary, null), builder, result, resolvedBundles, false);
                }
            }
        }
        for (String requireFragment : bundleInfo.getRequireFragments()) {
            builder.append("  <orderEntry type = \"module\" module-name=\"").append(requireFragment)
                .append("\"/>").append("\n");
        }
        if (!bundleInfo.getClasspathLibs().isEmpty()) {
            addLibraryEntry(bundleInfo, builder, true, false);
            for (String classpathLib : bundleInfo.getClasspathLibs()) {
                appendClasspathLib(builder, bundleInfo, classpathLib, false);
            }
            endLibraryEntry(builder, result, Set.of());
        }
        builder.append(" </component>").append("\n");
        builder.append("</module>");
        return builder.toString();
    }

    private void appendTestSources(@NotNull Path bundlePath, StringBuilder builder) {
        Path testFolder = bundlePath.resolve(TEST_FOLDER);
        if (testFolder.toFile().exists()) {
            builder.append("   <sourceFolder url=\"")
                .append(getFormattedRelativePath(bundlePath.resolve(testFolder), false, false))
                .append("\" isTestSource=\"true\"/>").append("\n");
        }
    }

    @Nullable
    private String generateIMLFeatureConfig(@NotNull FeatureInfo featureInfo, @NotNull Result result)
        throws IOException {
        if (featureInfo.getFeatureXmlFile() == null) {
            log.warn("Feature doesn't contain any data");
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<module type=\"JAVA_MODULE\" version=\"4\">\n");
        builder.append(" <component name=\"NewModuleRootManager\">\n");
        Path featurePath = featureInfo.getFeatureXmlFile().getParent();
        builder.append("  <exclude-output/>").append("\n");

        {
            builder.append("  <content url=\"").append(getFormattedRelativePath(featurePath, false, false)).append("\">")
                .append("\n");
            builder.append("   <excludeFolder url=\"")
                .append(getFormattedRelativePath(featurePath.resolve("target"), false, false))
                .append("\"/>").append("\n");
            builder.append("  </content>").append("\n");
        }
        builder.append(" </component>").append("\n");
        builder.append("</module>");
        return builder.toString();
    }

    private Properties readBuildConfiguration(Path bundlePath) throws IOException {
        Path resolve = bundlePath.resolve("build.properties");
        return FileUtils.readPropertiesFile(resolve);
    }

    private void appendBundleInfo(
        @NotNull BundleInfo bundleInfo,
        @NotNull Pair<String, VersionRange> requireBundle,
        @NotNull StringBuilder builder,
        @NotNull Result result,
        @NotNull Set<Pair<String, Version>> resolvedBundles
    ) throws IOException {
        Set<BundleInfo> bundleByName = result.getBundlesByName(requireBundle.getFirst());
        if (bundleByName == null) {
            if (bundleInfo.getPath() == null) {
                log.warn("Bundle doesn't contain any data");
            }
            return;
        }
        BundleInfo bundle = bundleByName.stream().filter(it -> VersionRange.isVersionsCompatible(requireBundle.getSecond(), new Version(it.getBundleVersion()))).findFirst().orElseThrow();
        if (resolvedBundles.contains(new Pair<>(requireBundle.getFirst(), new Version(bundle.getBundleVersion())))) {
            return;
        }
        boolean isExported = bundleInfo.getReexportedBundles().contains(requireBundle.getFirst());
        if (DevPropertiesProducer.isBundleAcceptable(requireBundle.getFirst())) {
            resolvedBundles.add(new Pair<>(requireBundle.getFirst(), new Version(bundle.getBundleVersion())));
            builder.append("  <orderEntry type = \"module\" module-name=\"").append(requireBundle.getFirst())
                .append(isExported ? "\" exported=\"\"" : "\"").append("/>").append("\n");
        } else {
            addModuleLibrary(new Pair<>(requireBundle.getFirst(), new Version(bundle.getBundleVersion())), builder, result, resolvedBundles, isExported);
        }
    }

    private void addModuleLibrary(
        @NotNull Pair<String, Version> requiredLibrary,
        @NotNull StringBuilder builder,
        @NotNull Result result,
        @NotNull Set<Pair<String, Version>> resolvedBundles,
        boolean isExported
    ) throws IOException {
        Set<BundleInfo> bundles = result.getBundlesByName(requiredLibrary.getFirst());
        if (bundles == null) {
            log.warn("Missing reexported bundle " + requiredLibrary);
            return;
        }
        BundleInfo bundleByName;
        if (requiredLibrary.getSecond() != null) {
            bundleByName = bundles.stream().filter(it -> new Version(it.getBundleVersion()).compareTo(requiredLibrary.getSecond()) == 0).findFirst().orElseThrow();
        } else {
            bundleByName = bundles.stream().findFirst().get();
        }
        if (bundleByName.getPath() == null) {
            log.warn("Missing reexported bundle " + requiredLibrary);
            return;
        }
        boolean directoryBundle = bundleByName.getPath().toFile().isDirectory();
        addLibraryEntry(bundleByName, builder, isExported, directoryBundle);
        if (!directoryBundle) {
            Set<Pair<String, Version>> oldResolvedBundles = new LinkedHashSet<>(resolvedBundles);
            appendLibraryInfo(builder, bundleByName, result, resolvedBundles, false);
            HashSet<Pair<String, Version>> diff = new HashSet<>(resolvedBundles);
            diff.removeAll(oldResolvedBundles);
            endLibraryEntry(builder, result, diff);
        } else {
            if (!generatedLibraries.contains(bundleByName.getBundleName())) {
                String libraryConfig = generateXMLLibraryConfig(bundleByName, result);
                if (libraryConfig != null) {
                    createConfigFile(getLibraryConfigPath().resolve(bundleByName.getBundleName() + ".xml"), libraryConfig);
                }
                generatedLibraries.add(bundleByName.getBundleName());
            }
        }
    }

    @Nullable
    private String generateXMLLibraryConfig(@NotNull BundleInfo bundleInfo, Result result) {
        if (bundleInfo.getPath() == null) {
            log.error("Bundle " + bundleInfo.getBundleName() + " path not found");
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<component name=\"libraryTable\">").append("\n");
        builder.append(" <library name=\"").append(bundleInfo.getBundleName()).append("\">\n");
        builder.append("   <CLASSES>").append("\n");
        LinkedHashSet<Pair<String, Version>> libraryObjects = new LinkedHashSet<>();
        appendLibraryInfo(builder, bundleInfo, result, libraryObjects, true);
        builder.append("  </CLASSES>").append("\n");
        builder.append("   <SOURCES>").append("\n");
        for (Pair<String, Version> libraryObject : libraryObjects) {
            Set<BundleInfo> sources = result.getBundlesByName(libraryObject.getFirst() + ".source");
            if (sources != null) {
                appendLibraryInfo(builder, sources.stream().filter(it -> it.getBundleVersion().equals(bundleInfo.getBundleVersion())).findFirst().get(), result, libraryObjects, true);
            }
        }
        builder.append("   </SOURCES>").append("\n");
        builder.append("   <jarDirectory url=\"")
            .append(getFormattedRelativePath(bundleInfo.getPath().resolve("lib"), true, true))
            .append("\" ").append("recursive=\"false\"/>\n");
        builder.append(" </library>\n");
        builder.append("</component>");
        return builder.toString();
    }

    private void endLibraryEntry(
        @NotNull StringBuilder builder,
        Result result,
        Set<Pair<String, Version>> resolvedBundles
    ) {
        builder.append("     </CLASSES>\n");
        builder.append("     <JAVADOC />\n");
        builder.append("     <SOURCES>\n");
        for (Pair<String, Version> resolvedBundle : resolvedBundles) {
            Collection<RemoteP2BundleInfo> sources = P2RepositoryManager.INSTANCE.getLookupCache().getRemoteBundlesByName(resolvedBundle.getFirst() + ".source");
            Optional<RemoteP2BundleInfo> source = sources.stream().filter(it -> new Version(it.getBundleVersion()).compareTo(resolvedBundle.getSecond()) == 0).findFirst();
            if (source.isPresent()) {
                source.get().resolveBundle();
                appendLibraryInfo(builder, source.get(), result, new LinkedHashSet<>(), false);
            }
        }
        builder.append("     </SOURCES>\n");
        builder.append("   </library>\n");
        builder.append("  </orderEntry>\n");
    }

    private static void addLibraryEntry(BundleInfo bundleByName,
                                        @NotNull StringBuilder builder,
                                        boolean isExported,
                                        boolean directoryBundle) {
        if (!directoryBundle) {
            builder.append("  <orderEntry type = \"module-library\"").append(isExported ? " exported=\"\">" : ">").append("\n");
            builder.append("   <library>\n");
            builder.append("     <CLASSES>\n");
        } else {
            builder.append("  <orderEntry type = \"library\" level=\"project\" name=\"")
                .append(bundleByName.getBundleName()).append("\"").append(isExported ? " exported=\"\"/>" : "/>")
                .append("\n");
        }
    }

    private Path getImlModulePath(@NotNull BundleInfo bundleInfo) {
        return PathsManager.INSTANCE.getImlModulesPath().resolve(bundleInfo.getBundleName() + ".iml");
    }

    private Path getImlModulePath(@NotNull FeatureInfo featureInfo) {
        return PathsManager.INSTANCE.getImlModulesPath().resolve(featureInfo.getFeatureName() + ".iml");
    }

    private Path getImplModuleConfigPath() {
        return getIdeaConfigsPath().resolve("modules.xml");
    }

    private Path getIdeaConfigsPath() {
        return PathsManager.INSTANCE.getImlModulesPath().resolve(".idea/");
    }
    private Path getLibraryConfigPath() {
        return getIdeaConfigsPath().resolve("libraries/");
    }

    private Path getXMLRunConfigurationPath() {
        return PathsManager.INSTANCE.getImlModulesPath().resolve(".idea/runConfigurations/");
    }

    private IMLConfigurationProducer() {
    }
}
