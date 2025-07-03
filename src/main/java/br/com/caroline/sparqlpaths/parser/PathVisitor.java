package br.com.caroline.sparqlpaths.parser;

import br.com.caroline.sparqlpaths.automaton.Automaton;
import br.com.caroline.sparqlpaths.automaton.AutomatonBuilder;

import java.util.Map;

/**
 * Visitor responsável por percorrer a árvore sintática de expressões Property Paths gerada pelo ANTLR e construir
 * um autômato correspondente para a expressão analisada.
 * <p>
 *     Essa classe estende {@link PropertyPathBaseVisitor} (gerada automaticamente pelo ANTLR) e implementa
 *     os métodos de visita para cada regra da gramática SPARQL Property Paths, traduzindo-as em chamadas para o
 *     {@link AutomatonBuilder}.
 * </p>
 * <p>
 *     Cada método {@code visit...} retorna um {@link Automaton} que representa a subexpressão correspondente da
 *     árvore sintática.
 * </p>
 * <p>
 *     O {@link AutomatonBuilder} é utilizado internamente para compor os autômatos com base nos operadores da
 *     linguagem.
 * </p>
 * <p>
 *      Os métodos implementados correspondem às seguintes regras da gramática:
 * </p>
 * <ul>
 *   <li>{@code visitStart}: Ponto de entrada, que delega para a regra {@code path}.</li>
 *   <li>{@code visitPath}: Trata da alternativa (operador {@code |}) entre diferentes sequências de caminho.</li>
 *   <li>{@code visitPathSequence}: Concatena elementos de caminho utilizando o operador {@code /}.</li>
 *   <li>{@code visitPathEltOrInverse}: Processa a possibilidade de inversão de um elemento de caminho (operador {@code ^}).</li>
 *   <li>{@code visitPathElt}: Aplica os modificadores de repetição ({@code *}, {@code +} e {@code ?}) a um elemento de caminho.</li>
 *   <li>{@code visitPathPrimary}: Lida com os elementos básicos, que podem ser um predicado (URI) ou uma subexpressão entre parênteses.</li>
 * </ul>
 *
 * @see Automaton
 * @see AutomatonBuilder
 * @see PropertyPathBaseVisitor
 */

public class PathVisitor extends PropertyPathBaseVisitor<Automaton> {
    private final AutomatonBuilder builder = new AutomatonBuilder();
    private final Map<String, String> prefixes; // dicionário de prefixos

    public PathVisitor(Map<String, String> prefixes) {
        this.prefixes = prefixes;
    }

    /**
     * Visita o nó raiz da árvore sintática, correspondente à regra {@code start} da gramática.
     * <p>
     *     Essa regra delega diretamente para a sub-regra {@code path}, que representa a expressão do caminho
     *     principal.
     * </p>
     *
     * @param ctx o contexto do parser (nó da árvore sintática) correspondente à regra {@code start}
     * @return o {@link Automaton} resultante da visita à subárvore {@code path}
     */
    @Override
    public Automaton visitStart(PropertyPathParser.StartContext ctx) {
        return visit(ctx.path());
    }

    /**
     * Visita uma expressão de caminho que representa alternativas (uso do operador {@code |}).
     * <p>
     *     Essa regra corresponde à construção {@code a | b | c}, em que múltiplos caminhos são combinados
     *     por meio da operação de alternância. A implementação visita cada {@code pathSequence} e utiliza o
     *     {@link AutomatonBuilder} para combiná-los com a operação {@code alternative}.
     * </p>
     *
     * @param ctx o contexto do parser (nó da árvore sintática) correspondente à regra {@code path}
     * @return um {@link AutomatonBuilder} que representa a união (alternância) de todos os caminhos visitados
     */
    @Override
    public Automaton visitPath(PropertyPathParser.PathContext ctx) {
        Automaton result = visit(ctx.pathSequence(0));

        for (int i = 1; i < ctx.pathSequence().size(); i++) {
            Automaton nextAutomaton = visit(ctx.pathSequence(i));
            result = builder.alternative(result, nextAutomaton);
        }
        return result;
    }

    /**
     * Visita uma expressão de caminho que representa uma sequência (uso do operador {@code /}).
     * <p>
     *     Essa regra corresponde à construção {@code a / b / c}, em que múltiplos elementos de caminho devem
     *     ocorrer em ordem. A implementação visita cada {@code pathEltOrInverse} e os concatena usando o método
     *     {@link AutomatonBuilder#sequence}.
     * </p>
     * @param ctx o contexto do parser (nó da árvore sintática) correspondente à regra {@code pathSequence}
     * @return um {@link Automaton} que representa a sequência de todos os elementos visitados
     */
    @Override
    public Automaton visitPathSequence(PropertyPathParser.PathSequenceContext ctx) {
        Automaton result = visit(ctx.pathEltOrInverse(0));

        for (int i = 1; i < ctx.pathEltOrInverse().size(); i++) {
            Automaton nextAutomaton = visit(ctx.pathEltOrInverse(i));
            result = builder.sequence(result, nextAutomaton);
        }

        return result;
    }

    /**
     * Visita uma expressão de caminho que pode estar invertida (uso do operador {@code ^}}.
     * <p>
     *     Essa regra permite expressões como {@code ^a}, que indicam a travessia inversa de um predicado. Se o
     *     operador {@code ^} estiver presente, o autômato resultante terá todas as transições invertidas (com os
     *     predicados prefixados por {@code ^}.
     * </p>
     * <p>
     *     Caso o operador não esteja presente, retorna o autômato base da subexpressão {@code pathElt}.
     * </p>
     * @param ctx o contexto do parser (nó da árvore sintática) correspondente à regra {@code pathEltOrInverse}
     * @return um {@link Automaton} que representa a subexpressão, com ou sem inversão
     */
    @Override
    public Automaton visitPathEltOrInverse(PropertyPathParser.PathEltOrInverseContext ctx) {
        Automaton baseAutomaton = visit(ctx.pathElt());

        if (ctx.getChildCount() > 1 && ctx.getChild(0).getText().equals("^")) {
            return baseAutomaton.withInvertedTransitions();
        }

        return baseAutomaton;
    }

    /**
     * Visita uma expressão de caminho com modificadores de repetição ({@code *}, {@code +}, {@code ?}).
     * <p>
     *     Essa regra permite expressões como {@code a*}, {@code a+} e {@code a?}, que indicam, respectivamente,
     *     zero ou mais, uma ou mais, ou zero ou uma ocorrências do caminho {@code a}.
     * </p>
     * <p>
     *     O método visita o {@code pathPrimary} correspondente ao caminho base, e aplica o modificador, se presente,
     *     utilizando os métodos apropriados do {@link AutomatonBuilder}.
     * </p>
     *
     * @param ctx o contexto do parser (nó da árvore sintática) correspondente à regra {@code pathElt}
     * @return um {@link Automaton} que representa o caminho modificado (ou não, caso não haja modificador)
     */
    @Override
    public Automaton visitPathElt(PropertyPathParser.PathEltContext ctx) {
        Automaton primary = visit(ctx.pathPrimary());

        if (ctx.pathMod() != null) {
            String modifier = ctx.pathMod().getText();
            if (modifier.equals("*")) {
                return builder.zeroOrMore(primary);
            } else if (modifier.equals("+")) {
                return builder.oneOrMore(primary);
            } else if (modifier.equals("?")) {
                return builder.zeroOrOne(primary);
            }
        }

        return primary;
    }

    /**
     * Visita um elemento básico da expressão de caminho, que pode ser:
     * <ul>
     *     <li>Um predicado (URI entre {@code < >})</li>
     *     <li>Uma subexpressão entre parênteses</li>
     * </ul>
     * <p>
     *     Se o nó contiver um {@code iri}, é construído um autômato que reconhece apenas esse predicado.
     *     Caso contrário, é assumido que se trata de uma subexpressão entre parênteses, e o método visita
     *     recursivamente o {@code path} interno.
     * </p>
     *
     * @param ctx o contexto do parser (nó da árvore sintática) correspondente à regra {@code pathPrimary}
     * @return um {@link Automaton} que representa o predicado ou a subexpressão entre parênteses
     */
    public Automaton visitPathPrimary(PropertyPathParser.PathPrimaryContext ctx) {
        if (ctx.iri() != null) {
            String text = ctx.iri().getText();
            String fullURI;

            if (text.startsWith("<") && text.endsWith(">")) {
                // Caso 1: nesse caso, já é uma URI completa.
                fullURI = text.substring(1, text.length() - 1);
            } else {
                // Caso 2: é um nome prefixado.
                String[] parts = text.split(":", 2);
                if (parts.length < 2) {
                    throw new RuntimeException("Nome prefixado inválido: " + text);
                }
                String prefix = parts[0];
                String localName = parts[1];

                String namespace = prefixes.get(prefix);
                if (namespace == null) {
                    throw new RuntimeException("Prefixo não definido no mapa: '" + prefix + "'");
                }

                fullURI = namespace + localName;
                System.out.println(fullURI);
            }
            return builder.fromPredicate(fullURI);
        }

        return visit(ctx.path());
    }

}
