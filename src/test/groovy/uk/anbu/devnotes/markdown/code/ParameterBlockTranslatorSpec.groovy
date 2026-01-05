package uk.anbu.devnotes.markdown.code

import org.commonmark.node.Node
import spock.lang.Specification
import spock.lang.Shared
import uk.anbu.devnotes.markdown.code.datablock.ParameterRegistry

class ParameterBlockTranslatorSpec extends Specification {

    @Shared
    ParameterRegistry registry

    @Shared
    ParameterBlockTranslator translator

    def setupSpec() {
        registry = new ParameterRegistry()
        translator = new ParameterBlockTranslator(registry)
    }

    def "loads simple key/value parameters from a single block"() {
        given:
        String yaml = '''
id: 2
name: Alice
active: true
'''

        when:
        Node result = translator.renderParameterBlock(yaml)

        then:
        result != null
        // Expect a simple two-column table: each key and its value should appear in <td> elements
        def html = result.literal
        html.contains('<td>id</td>')
        html.contains('<td>2</td>')
        html.contains('<td>name</td>')
        html.contains('<td>Alice</td>')
        html.contains('<td>active</td>')
        html.contains('<td>true</td>')

        registry.getAll().size() == 3
        def idParam = registry.get('id')
        idParam != null
        (idParam instanceof Number) && ((Number) idParam).intValue() == 2

        def nameParam = registry.get('name')
        nameParam != null
        nameParam.toString() == 'Alice'

        def activeParam = registry.get('active')
        activeParam != null
        (activeParam instanceof Boolean) && ((Boolean) activeParam)
    }

    def "multiple parameter blocks append and override existing parameters"() {
        given:
        String yaml1 = '''
id: 1
foo: bar
'''
        String yaml2 = '''
id: 2
extra: baz
'''

        when:
        translator.renderParameterBlock(yaml1)
        translator.renderParameterBlock(yaml2)

        then:
        // id should be overridden by second block
        registry.get('id') != null
        ((Number) registry.get('id')).intValue() == 2
        registry.get('foo').toString() == 'bar'
        registry.get('extra').toString() == 'baz'
    }
}