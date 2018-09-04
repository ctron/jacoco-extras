
def slurper = new XmlSlurper();
slurper.setFeature('http://apache.org/xml/features/disallow-doctype-decl', false);
slurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

def report = slurper.parse(new File(basedir, "module2/target/jacoco.xml"))

return report.'**'.findAll {
	node -> node.name() == 'class' && node.@name == 'foo/bar/Baz'
}.size() > 0
