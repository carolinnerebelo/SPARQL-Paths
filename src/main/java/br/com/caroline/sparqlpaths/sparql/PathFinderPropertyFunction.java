package br.com.caroline.sparqlpaths.sparql;

import br.com.caroline.sparqlpaths.model.Path;
import br.com.caroline.sparqlpaths.service.PathFinderService;
import org.apache.jena.graph.Node;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.ExecutionContext;
import org.apache.jena.sparql.engine.QueryIterator;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingFactory;
import org.apache.jena.sparql.engine.iterator.QueryIterNullIterator;
import org.apache.jena.sparql.engine.iterator.QueryIterPlainWrapper;
import org.apache.jena.sparql.pfunction.PropFuncArg;
import org.apache.jena.sparql.pfunction.PropertyFunctionBase;
import org.apache.jena.sparql.pfunction.PropertyFunctionRegistry;
import org.apache.jena.sparql.util.NodeFactoryExtra;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Property Function. Ela age como uma ponte entre o SPARQL e o PathFinderService.
 */

public class PathFinderPropertyFunction extends PropertyFunctionBase {

    // Representa um passo único no caminho para evitar duplicatas
    private record PathStep(int index, Node predicate, Node node) {}

    @Override
    public QueryIterator exec(Binding binding, PropFuncArg argSubject, Node predicate, PropFuncArg argObject, ExecutionContext execCtx) {

        // 1. Extrair e validar os argumentos
        if (!argSubject.isList() || argSubject.getArgList().size() != 2) {
            // Sujeito deve ser uma lista com (startNode, regex) (TODO tentar mudar isso depois)
            return QueryIterNullIterator.create(execCtx);
        }
        if (!argObject.isList() || argObject.getArgList().size() != 3) {
            // Objeto deve ser uma lista com (?index, ?predicate, ?node) (TODO tentar mudar isso depois)
            return QueryIterNullIterator.create(execCtx);
        }

        // Extrai os argumentos do sujeito
        List<Node> subjectArgs = argSubject.getArgList();
        Node startNode = subjectArgs.get(0);
        Node regexNode = subjectArgs.get(1);

        // Extrai as variáveis do objeto
        List<Node> objectArgs = argObject.getArgList();
        Var varIndex = (Var) objectArgs.get(0);
        Var varPredicate =  (Var) objectArgs.get(1);
        Var varNode = (Var) objectArgs.get(2);

        // Valida e resolve os argumentos
        if (startNode.isVariable()) {
            startNode = binding.get((Var) startNode);
        }
        if (regexNode.isVariable()) {
            regexNode = binding.get((Var) regexNode);
        }
        if (startNode == null || !startNode.isURI() || !regexNode.isLiteral()) {
            return QueryIterNullIterator.create(execCtx);
        }

        String startNodeURI = startNode.getURI();
        String regexString = regexNode.getLiteralLexicalForm();

        // 2. Instanciar e chamar a lógica de busca
        Model model = ModelFactory.createModelForGraph(execCtx.getActiveGraph());

        // Supondo que o construtor seja PathFinderService(Model, Map<String, String>)
        // Em um sistema real, poderíamos obter os prefixos do execCxt (TODO remover a parte do prefixo da Main pois já estamos pegando os prefixos aqui)
        PathFinderService pathFinder = new PathFinderService(model, model.getNsPrefixMap());

        List<Path> foundPaths = pathFinder.findPaths(startNodeURI, regexString);

        // verificar se essa parte abaixo é necessária
//        if (foundPaths.isEmpty()) {
//            return QueryIterNullIterator.create(execCtx);
//        }

        // 3. Processar os resultados e gerar as soluções (bindings)
        // --- INÍCIO DA CORREÇÃO ---

        // 1. Usar um Set para armazenar apenas os passos únicos de todos os caminhos
        Set<PathStep> uniqueSteps = new HashSet<>();

        for (Path path : foundPaths) {
            for (int i = 0; i < path.nodes().size(); i++) {
                Node currentNode = path.nodes().get(i).asNode();
                Node predicateNode = (i > 0) ? path.predicates().get(i - 1).asNode() : null;
                uniqueSteps.add(new PathStep(i, predicateNode, currentNode));
            }
        }

        // 2. Agora, criar os bindings a partir do conjunto de passos únicos
        List<Binding> outputBindings = new ArrayList<>();
        for (PathStep step : uniqueSteps) {
            Binding newBinding = binding;
            newBinding = BindingFactory.binding(newBinding, varIndex, NodeFactoryExtra.intToNode(step.index()));
            newBinding = BindingFactory.binding(newBinding, varNode, step.node());
            if (step.predicate() != null) {
                newBinding = BindingFactory.binding(newBinding, varPredicate, step.predicate());
            }
            outputBindings.add(newBinding);
        }

        // --- FIM DA CORREÇÃO ---

        // Retorna um iterador sobre a lista de todas as linhas de solução geradas
        return QueryIterPlainWrapper.create(outputBindings.iterator(), execCtx);
    }

    // Main provisória
    public static void main(String[] args) {

        String graphFile;

        if (args.length > 0) {
            graphFile = args[0];
        } else {
            graphFile = "graph.ttl";
        }

        Model graph = loadGraph(graphFile);
        System.out.println("Grafo carregado: " + graphFile);

        // Registrar a Property Function
        String pfURI = "http://example.com/ns#pathFinder";
        PropertyFunctionRegistry.get().put(pfURI, PathFinderPropertyFunction.class);

        // A consulta SPARQL que usará a função

        // A consulta SPARQL para o novo teste
        String queryString = """
            PREFIX myfns: <http://example.com/ns#>
            PREFIX ex: <http://exemplo.org/grafo-pathfinder#>

            SELECT ?pathIndex ?predicate ?node
            WHERE {
                # Início: ex:John, Regex: "ex:follows+ | ex:lives"
                ( ex:John "ex:follows+ | ex:lives" ) myfns:pathFinder (?pathIndex ?predicate ?node) .
            }
            # Ordenar por múltiplos critérios para um resultado estável
            ORDER BY ?pathIndex ?node
            """;

        System.out.println("--- Executando consulta com a PathFinderPropertyFunction ---");
        System.out.println("Consulta SPARQL com regex 'ex:follows+ | ex:lives':\n" + queryString);

//        String queryString = """
//            PREFIX myfns: <http://example.com/ns#>
//            PREFIX ex: <http://example.org/>
//
//            SELECT ?pathIndex ?predicate ?node
//            WHERE {
//                ( ex:A "ex:p/ex:p" ) myfns:pathFinder (?pathIndex ?predicate ?node) .
//            }
//            ORDER BY ?pathIndex
//            """;

        executeQuery(queryString, graph);
    }


    // --- Funções auxiliares ---
    public static Model createTestModel() {
        Model model =  ModelFactory.createDefaultModel();
        model.setNsPrefix("ex", "http://example.org/");
        model.createResource("http://example.org/A")
                .addProperty(model.createProperty("http://example.org/p"),
                        model.createResource("http://example.org/B"));
        model.getResource("http://example.org/B")
                .addProperty(model.createProperty("http://example.org/p"),
                        model.createResource("http://example.org/C"));
        return model;
    }

    public static Model loadGraph(String graphFile) {
        // O objeto 'Model' do Jena é a representação do grafo em memória
        Model graph = ModelFactory.createDefaultModel();

        try {
            // RDFDataMgr carrega o arquivo RDF para dentro do modelo
            // O Jena vai procurar o arquivo na pasta 'src/main/resources'
            RDFDataMgr.read(graph, graphFile);
        } catch (RiotException e) {
            throw new RuntimeException("Falha ao ler ou analisar o arquivo RDF: " + graphFile, e);
        }

        if (graph.isEmpty()) {
            throw new RuntimeException("O arquivo RDF '" + graphFile + "' está vazio ou não contém triplas.");
        }

        return graph;
    }

    private static void executeQuery(String queryString, Model model) {
        try (QueryExecution qe = QueryExecutionFactory.create(queryString, model)) {
            ResultSet results = qe.execSelect();
            ResultSetFormatter.out(System.out, results);
        }
    }
}
