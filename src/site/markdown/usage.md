# Usage

The following sections show how the Jacoco extras mojo can be used.

## Convert to XML data

    <plugins>
        …
        <plugin>
            <groupId>de.dentrassi.maven</groupId>
            <artifactId>jacoco-extras</artifactId>
            <version>${project.version}</version>
            <configuration>
                <goals>
                    <goal>xml</goal>
                </goals>
            </configuration>
        </plugin>
        …
    </plugins>

