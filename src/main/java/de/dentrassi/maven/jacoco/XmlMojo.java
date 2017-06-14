/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.maven.jacoco;

import static org.apache.maven.plugins.annotations.LifecyclePhase.VERIFY;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jacoco.report.IReportGroupVisitor;
import org.jacoco.report.IReportVisitor;

@Mojo(defaultPhase = VERIFY, name = "xml", requiresProject = true, inheritByDefault = true)
public class XmlMojo extends AbstractMojo {

    /**
     * Allows to skip the execution
     */
    @Parameter(property = "jacoco.extras.skip", defaultValue = "false")
    private boolean skip;

    /**
     * The jacoco execution data <br>
     * If this file doesn't exist, execution of this plugin will be skipped
     */
    @Parameter(property = "jacoco.extras.execFile", defaultValue = "${project.build.directory}/jacoco.exec", required = true)
    private File execFile;

    /**
     * The output XML file
     */
    @Parameter(property = "jacoco.extras.xmlFile", defaultValue = "${project.build.directory}/jacoco.xml", required = true)
    private File xmlFile;

    /**
     * The encoding of the source files
     */
    @Parameter(property = "project.build.sourceEncoding", defaultValue = "UTF-8")
    private String sourceEncoding;

    /**
     * Include patterns. The default is to include everything.
     */
    @Parameter
    private List<String> includes;

    /**
     * Exclude patterns. The default is to exclude nothing.
     */
    @Parameter
    private List<String> excludes;

    @Parameter(property = "project", readonly = true)
    private MavenProject project;

    @Parameter(property = "reactorProjects", readonly = true)
    private List<MavenProject> reactorProjects;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (this.skip) {
            return;
        }

        if (!this.execFile.isFile()) {
            getLog().debug("Not running. No execution data found.");
            return;
        }

        try {
            this.xmlFile.getParentFile().mkdirs();

            final ReportSupport report = new ReportSupport(getLog());
            report.loadExecutionData(this.execFile);
            report.addXmlFormatter(this.xmlFile, "UTF-8");

            final IReportVisitor visitor = report.initRootVisitor();
            final IReportGroupVisitor group = visitor.visitGroup("XML");

            for (final MavenProject dependency : findDependencies(Artifact.SCOPE_COMPILE, Artifact.SCOPE_RUNTIME)) {
                report.processProject(group, dependency.getArtifactId(), dependency, this.includes, this.excludes,
                        this.sourceEncoding);
            }

            visitor.visitEnd();

        } catch (final IOException e) {
            throw new MojoExecutionException("Failed to convert to XML", e);
        }
    }

    private List<MavenProject> findDependencies(final String... scopes) {
        final List<MavenProject> result = new ArrayList<>();
        final List<String> scopeList = Arrays.asList(scopes);
        for (final Object dependencyObject : this.project.getDependencies()) {
            final Dependency dependency = (Dependency) dependencyObject;
            if (scopeList.contains(dependency.getScope())) {
                final MavenProject project = findProjectFromReactor(dependency);
                if (project != null) {
                    result.add(project);
                }
            }
        }
        return result;
    }

    private MavenProject findProjectFromReactor(final Dependency d) {
        for (final MavenProject p : this.reactorProjects) {
            if (p.getGroupId().equals(d.getGroupId()) && p.getArtifactId().equals(d.getArtifactId())
                    && p.getVersion().equals(d.getVersion())) {
                return p;
            }
        }
        return null;
    }

}
