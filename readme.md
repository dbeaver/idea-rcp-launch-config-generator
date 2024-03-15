# eclipse-plugins-resolver

This tool generates the `dev.properties` and `config.ini` files needed to run the project in the dev environment.

Accepts the following required parameters:

* -productFile — Path to .product file
* -projectsFolder — Path to projects folder
* -eclipse — Path to Eclipse instance
* -output — Place for result files

For example, the command to create files for CB CE:

```
./eclipse-plugins-resolver -productFile $PROJECTS_DIR$/cloudbeaver/server/product/web-server/CloudbeaverServer.product -projectsFolder $PROJECTS_DIR$ -eclipse $ECLIPSE_PATH$ -output $PROJECTS_DIR$/eclipse/workspace/.metadata/.plugins/org.eclipse.pde.core/CloudbeaverServer.product'
```

In addition, you can customize where to look for packages by creating a config.properties file in app working directory.
The default configuration is stored in resources