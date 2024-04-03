/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp
 *
 * All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of DBeaver Corp and its suppliers, if any.
 * The intellectual and technical concepts contained
 * herein are proprietary to DBeaver Corp and its suppliers
 * and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from DBeaver Corp.
 */
package org.jkiss.tools.rcplaunchconfig;

import jakarta.annotation.Nonnull;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;

import java.nio.file.Path;

public class Params {

    @Option(names = "-config", description = "Path to configuration properties file", required = true)
    public Path configFilePath;

    @Option(names = "-elk.version", description = "ELK layout version", required = true)
    public String elkVersion;

    @Option(names = "-eclipse.version", description = "Eclipse version", required = true)
    public String eclipseVersion;

    @Option(names = "-productFile", description = "Path to .product file", required = true)
    public Path productFilePath;

    @Option(names = "-projectsFolder", description = "Path to projects folder", required = true)
    public Path projectsFolderPath;

    @Option(names = "-eclipse", description = "Path to Eclipse instance", required = true)
    public Path eclipsePath;

    @Option(names = "-output", description = "Place for result files", required = true)
    public Path resultFilesPath;

    public @Nonnull ParseResult init(String[] args) {
        return new CommandLine(this)
            .parseArgs(args);
    }
}
