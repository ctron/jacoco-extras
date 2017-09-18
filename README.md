
# Jacoco extras plugin [![Build status](https://api.travis-ci.org/ctron/jacoco-extras.svg)](https://travis-ci.org/ctron/jacoco-extras) ![Maven Central](https://img.shields.io/maven-central/v/de.dentrassi.maven/jacoco-extras.svg "Maven Central Status")


This is a Maven Plugin which allows to convert jacoco execution data
into a simple XML report. This is required for services like https://codecov.io which
work on XML data instead of Jacoco's binary execution data.

However Jacoco generates reports only for classes which are actually in the project
or requires you to set up a full aggregated report project, which requires dependencies
to all modules.

This plugin converts the execution data for a module including all dependencies of the
module automatically.

## Usage

Simply add the follow to your Maven project:

```xml
<plugin>
  <groupId>de.dentrassi.maven</groupId>
  <artifactId>jacoco-extras</artifactId>
  <version><!-- current version --></version>
  <executions>
    <execution>
      <goals>
        <goal>xml</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

For more information about how to use this plugin see
[the documentation](https://ctron.github.io/jacoco-extras).

## License

This plugin is open source and licensed under the EPL. See also [license.html](license.html).

