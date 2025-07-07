package br.com.caroline.sparqlpaths.sparql;

import br.com.caroline.sparqlpaths.model.Path;
import br.com.caroline.sparqlpaths.service.PathFinderService;
import org.apache.jena.graph.Node;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.ExecutionContext;
import org.apache.jena.sparql.engine.QueryIterator;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingFactory;
import org.apache.jena.sparql.engine.iterator.QueryIterPlainWrapper;
import org.apache.jena.sparql.pfunction.PropFuncArg;
import org.apache.jena.sparql.pfunction.PropertyFunctionBase;
import org.apache.jena.sparql.util.IterLib;
import org.apache.jena.sparql.util.NodeFactoryExtra;

import java.util.*;

/**
 * Implementa uma Property Function personalizada do Jena para realizar buscas em grafos RDF com base em Property Paths (expressões regulares).
 * <p>
 * Esta função SPARQL permite consultas da forma:
 * </p>
 *
 * <pre>{@code
 * ?s <pathFinder:findPaths> (?pathId ?stepIndex ?predicate ?node)
 * }</pre>
 *
 * <p>
 * Internamente, a função recebe como sujeito uma lista contendo:
 * </p>
 * <ul>
 *     <li>O nó de partida (URI)</li>
 *     <li>Uma expressão regular de caminho como literal (ex: {@code foaf:follows+})</li>
 * </ul>
 * <p>
 * E como objeto, uma lista com variáveis que recebem:
 * </p>
 * <ul>
 *     <li>O identificador do caminho encontrado ({@code ?pathId})</li>
 *     <li>O índice do passo no caminho ({@code ?stepIndex})</li>
 *     <li>O predicado usado para chegar ao nó atual ({@code ?predicate})</li>
 *     <li>O nó do grafo visitado nesse passo ({@code ?node})</li>
 * </ul>
 *
 * <p>
 * A função utiliza a classe {@link br.com.caroline.sparqlpaths.service.PathFinderService} para executar a lógica de busca.
 * Cada caminho encontrado é desmembrado em múltiplos bindings, um para cada passo do caminho.
 * </p>
 *
 * @see org.apache.jena.sparql.pfunction.PropertyFunctionBase
 * @see br.com.caroline.sparqlpaths.service.PathFinderService
 */
public class PathFinderPropertyFunction extends PropertyFunctionBase {

    /**
     * Executa a Property Function sobre uma consulta SPARQL, aplicando a lógica de busca por caminhos definidos por expressões regulares.
     * <p>
     * Espera como entrada:
     * <ul>
     *     <li>No sujeito: uma lista com dois elementos — o URI do nó inicial e a expressão regular de caminho (como literal)</li>
     *     <li>No objeto: uma lista com quatro variáveis — {@code (?pathId ?stepIndex ?predicate ?node)}</li>
     * </ul>
     * </p>
     * <p>
     * Para cada caminho encontrado no grafo, o método gera múltiplos bindings, correspondentes aos passos do caminho.
     * Cada binding inclui:
     * </p>
     * <ul>
     *     <li>O identificador do caminho</li>
     *     <li>O índice do passo</li>
     *     <li>O predicado usado (ou nulo em caso de transição inicial)</li>
     *     <li>O nó RDF visitado</li>
     * </ul>
     *
     * @param binding     o binding atual do Jena, representando a associação parcial de variáveis
     * @param subjArg     o argumento do sujeito, que deve ser uma lista com [URI, regex]
     * @param predicate   o predicado (ignorado, pois não é usado diretamente aqui)
     * @param objArg      o argumento do objeto, que deve ser uma lista com quatro variáveis
     * @param execCxt     o contexto de execução da consulta
     * @return um {@link QueryIterator} com os bindings correspondentes aos caminhos encontrados
     */
    @Override
    public QueryIterator exec(Binding binding, PropFuncArg subjArg, Node predicate, PropFuncArg objArg, ExecutionContext execCxt) {
        try {

            // Validação dos argumentos
            if (!subjArg.isList() || subjArg.getArgList().size() != 2) {
                throw new QueryBuildException("O sujeito deve ser uma lista com (nóDePartida 'regex')");
            }
            if (!objArg.isList() || objArg.getArgList().size() != 4) {
                throw new QueryBuildException("O objeto deve ser uma lista com (?pathId ?stepIndex ?predicate ?node)");
            }

            // Extração dos argumentos do sujeito
            List<Node> subjectArgs = subjArg.getArgList();
            Node startNode = subjectArgs.get(0);
            Node regexNode = subjectArgs.get(1);

            if (!startNode.isURI() || !regexNode.isLiteral()) {
                throw new QueryBuildException("Argumentos do sujeito inválidos: esperado (URI, Literal)");
            }
            String startNodeURI = startNode.getURI();
            String regexString = regexNode.getLiteralLexicalForm();

            // Extração das variáveis do objeto
            List<Node> objectArgs = objArg.getArgList();
            Var varPathId = Var.alloc(objectArgs.get(0));
            Var varStepIndex = Var.alloc(objectArgs.get(1));
            Var varPredicate = Var.alloc(objectArgs.get(2));
            Var varNode = Var.alloc(objectArgs.get(3));

            Model model = ModelFactory.createModelForGraph(execCxt.getActiveGraph());

            // Acionando a lógica de busca
            PathFinderService pathFinder = new PathFinderService(model, model.getNsPrefixMap());
            List<Path> foundPaths = pathFinder.findPaths(startNodeURI, regexString);

            // Geração dos bindings que vão armazenar o resultado
            List<Binding> outputBindings = new ArrayList<>();
            int pathIdCounter = 0;
            for (Path path : foundPaths) {
                for (int i = 0; i < path.nodes().size(); i++) {
                    Binding newBinding = BindingFactory.binding(binding);

                    newBinding = BindingFactory.binding(newBinding, varPathId, NodeFactoryExtra.intToNode(pathIdCounter));
                    newBinding = BindingFactory.binding(newBinding, varStepIndex, NodeFactoryExtra.intToNode(i));
                    newBinding = BindingFactory.binding(newBinding, varNode, path.nodes().get(i).asNode());

                    if (i > 0) {
                        newBinding = BindingFactory.binding(newBinding, varPredicate, path.predicates().get(i - 1).asNode());
                    }
                    outputBindings.add(newBinding);
                }
                pathIdCounter++;
            }

            return QueryIterPlainWrapper.create(outputBindings.iterator());

        } catch (Exception e) {
            e.printStackTrace();
            return IterLib.noResults(execCxt);
        }
    }
}
