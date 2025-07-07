package br.com.caroline.sparqlpaths.parser;

import br.com.caroline.sparqlpaths.automaton.Automaton;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Map;

/**
 * Responsável por transformar expressões regulares de caminhos (Property Paths) em autômatos finitos.
 * <p>
 * Esta classe encapsula a lógica de parsing de uma expressão SPARQL Property Path,
 * utilizando o ANTLR para gerar uma árvore sintática e, a partir dela, construir um {@link Automaton}.
 * </p>
 *
 * <p>
 * A construção considera também a resolução de prefixos, permitindo o uso de URIs abreviadas
 * conforme um mapa de prefixos fornecido no momento da criação da instância.
 * </p>
 *
 * <p>
 * Exemplo de uso:
 * </p>
 * <pre>{@code
 * Map<String, String> prefixes = Map.of("foaf", "http://xmlns.com/foaf/0.1/");
 * AutomatonParser parser = new AutomatonParser(prefixes);
 * Automaton automaton = parser.buildAutomatonFromRegex("foaf:follows+");
 * }</pre>
 *
 */

public class AutomatonParser {

    private final Map<String, String> prefixes;

    public AutomatonParser(Map<String, String> prefixes) {
        this.prefixes = prefixes;
    }

    /**
     * Constrói um autômato finito a partir de uma expressão regular de caminho (Property Path) usando ANTLR.
     * <p>
     *     A expressão passada como string é processada pela pipeline do ANTLR:
     * </p>
     * <ol>
     *     <li>Convertida em fluxo de caracteres ({@link CharStream})</li>
     *     <li>Interpretada por um lexer gerado automaticamente ({@link PropertyPathLexer})</li>
     *     <li>Transformada em tokens e analisada por um parser ({@link PropertyPathParser})</li>
     *     <li>Convertida em uma árvore sintática abstrata ({@link ParseTree})</li>
     *     <li>Traduzida em um {@link Automaton} pelo {@link PathVisitor}</li>
     * </ol>
     * @param propertyPath a string contendo a expressão regular de caminho
     * @return um {@link Automaton} equivalente à expressão regular fornecida
     */
    public Automaton buildAutomatonFromRegex(String propertyPath) {
        CharStream input = CharStreams.fromString(propertyPath);

        PropertyPathLexer lexer = new PropertyPathLexer(input);

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        PropertyPathParser parser = new PropertyPathParser(tokens);

        ParseTree tree = parser.start();

        PathVisitor visitor = new PathVisitor(prefixes);

        return visitor.visit(tree);
    }
}
