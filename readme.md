# Eclipse RCP launcher generator for IDEA

This tool generates the `dev.properties` and `config.ini` files needed to run the project in the dev environment.

Instructions:
- Download and unpack Eclipse IDE (we recommend to use `Eclipse for RCP and RAP developers` package)
- Launch Eclipse IDE and install all dependencies (use Help->Install new software):
  - https://p2.dev.dbeaver.com/eclipse-repo (DBeaver and CloudBeaver 3rd party deps)
- Launch IDEA
- Set variable `ECLIPSE_PATH` to the location where Eclipse IDE is installed.
- Open project (e.g. `idea-workspace-dbeaver`)
- Build project (CTRL+F9)
- Execute run configuration `Generate DBeaver CE dev props`


Accepts the following required parameters:

Parameter | Description
------|----
-config | Path to file with configuration 
-productFile | Path to .product file
-projectsFolder | Path to projects folder
-eclipse | Path to Eclipse instance
-output | Place for result files

For example, the command to create files for CB CE:

```
./eclipse-rcp-launcher -productFile $PROJECTS_DIR$/cloudbeaver/server/product/web-server/CloudbeaverServer.product -projectsFolder $PROJECTS_DIR$ -eclipse $ECLIPSE_PATH$ -output $PROJECT_DIR$/../eclipse/workspace/.metadata/.plugins/org.eclipse.pde.core/CloudbeaverServer.product'
```

Configuration file:
```properties
featuresPaths=dbeaver/features;
bundlesPaths=\
  dbeaver-common/modules;\
  dbeaver/plugins;
```
featuresPaths: list of paths to Eclipse features folders  
bundlesPaths: list of paths to Eclipse bundles folders

In addition, you can customize where to look for packages by creating a config.properties file in app working directory.
The default configuration is stored in resources
