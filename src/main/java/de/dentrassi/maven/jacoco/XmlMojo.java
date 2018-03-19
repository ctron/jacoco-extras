/*******************************************************************************
 * Copyright (c) 2017, 2018 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.maven.jacoco;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.maven.artifact.Artifact.SCOPE_COMPILE;
import static org.apache.maven.artifact.Artifact.SCOPE_PROVIDED;
import static org.apache.maven.artifact.Artifact.SCOPE_RUNTIME;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VERIFY;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jacoco.report.IReportGroupVisitor;
import org.jacoco.report.IReportVisitor;
import org.w3c.dom.Document;

/**
 * Convert binary execution data to XML report including dependencies. <br>
 * This mojo will convert the binary jacoco execution data into the same format
 * as the XML report. But it will take all module dependencies into
 * consideration. This may be helpful when tools actually require XML data, but
 * tests in a module are responsible for testing classes in other modules.
 */
@Mojo(defaultPhase = VERIFY, name = "xml", requiresProject = true, inheritByDefault = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
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

    /**
     * If the XML file should be pretty printed.
     *
     * @since 0.1.2
     */
    @Parameter(property = "jacoco.extras.pretty", defaultValue = "true")
    private boolean pretty = true;

    /**
     * When pretty printing, if the original file should be deleted.
     *
     * @since 0.1.2
     */
    @Parameter(property = "jacoco.extras.deleteRaw", defaultValue = "true")
    private final boolean deleteRaw = true;

    public void setPretty(final boolean pretty) {
        this.pretty = pretty;
    }

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

            processProject(report, group, this.project);

            for (final MavenProject dependency : findDependencies(SCOPE_COMPILE, SCOPE_RUNTIME, SCOPE_PROVIDED)) {
                processProject(report, group, dependency);
            }

            visitor.visitEnd();

            if (this.pretty) {
                makePretty();
            }

        } catch (final IOException e) {
            throw new MojoExecutionException("Failed to convert to XML", e);
        }
    }

    private void processProject(final ReportSupport report, final IReportGroupVisitor group, final MavenProject project)
            throws IOException {
        report.processProject(group, project.getArtifactId(), project, this.includes, this.excludes,
                this.sourceEncoding);
    }

    private void makePretty() throws MojoExecutionException {
        try {
            final TransformerFactory tf = TransformerFactory.newInstance();

            final Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.ENCODING, this.sourceEncoding);
            transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "-//JACOCO//DTD Report 1.0//EN");
            transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "report.dtd");

            final Path tmp = this.xmlFile.toPath().getParent()
                    .resolve("raw." + this.xmlFile.toPath().getFileName().toString());

            Files.move(this.xmlFile.toPath(), tmp, REPLACE_EXISTING);

            try (final InputStream in = new BufferedInputStream(Files.newInputStream(tmp));
                    final OutputStream out = new BufferedOutputStream(Files.newOutputStream(this.xmlFile.toPath()));) {

                final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setValidating(false);
                dbf.setExpandEntityReferences(false);
                dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                final Document doc = dbf.newDocumentBuilder().parse(in);

                final DOMSource source = new DOMSource(doc);
                final StreamResult result = new StreamResult(out);

                transformer.transform(source, result);
            }

            // we only delete this if we succeeded
            if (this.deleteRaw) {
                Files.deleteIfExists(tmp);
            }

        } catch (final Exception e) {
            throw new MojoExecutionException("Failed to pretty print XML file", e);
        }

    }

    private List<MavenProject> findDependencies(final String... scopes) {
        final List<MavenProject> result = new LinkedList<>();
        final Set<String> scopeList = new HashSet<>(Arrays.asList(scopes));
        for (final Dependency dependency : this.project.getDependencies()) {
            if (scopeList.contains(dependency.getScope())) {
                getLog().info("Adding dependency - " + dependency.toString());
                final MavenProject project = findProjectFromReactor(dependency);
                if (project != null) {
                    getLog().info("  -> Unable to find in reactor");
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
