package br.com.caroline.sparqlpaths.service;

import br.com.caroline.sparqlpaths.automaton.Automaton;
import br.com.caroline.sparqlpaths.automaton.AutomatonBuilder;
import br.com.caroline.sparqlpaths.automaton.Transition;
import br.com.caroline.sparqlpaths.model.Path;
import br.com.caroline.sparqlpaths.parser.PathVisitor;
import br.com.caroline.sparqlpaths.parser.PropertyPathLexer;
import br.com.caroline.sparqlpaths.parser.PropertyPathParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.jena.rdf.model.*;

import java.util.*;

public class PathFinderService {

    /**
     * Representa o estado de uma busca durante a execução do algoritmo.
     * <p>
     *     Este record é utilizado como unidade de progresso durante a exploração do grafo.
     * </p>
     *
     * @param graphNode         o nó atual no grafo RDF
     * @param automatonState    o ID do estado atual no autômato
     * @param path              o caminho percorrido até este ponto da busca
     */
    private record SearchState(RDFNode graphNode, int automatonState, Path path) {}

    private final Model graph;

    public PathFinderService(Model graph) {
        this.graph = graph;
    }

    public List<Path> findPaths(String startNodeURI, String regexString) {

        // Autômato criado a partir da regex
        Automaton automaton = buildAutomatonFromRegex(regexString);

        // Fila de estados a explorar (BFS)
        Queue<SearchState> queue = new LinkedList<>();

        // Mapa de estados visitados: <URI + ID do estado>, profundidade mínima
        Map<String, Integer> visited = new HashMap<>();

        // Lista com todos os caminhos finais válidos encontrados
        List<Path> finalResults = new ArrayList<>();

        // Nó inicial como recurso RDF
        Resource startNode = this.graph.createResource(startNodeURI);

        // Caminho inicial (nó de origem, sem predicados ainda)
        Path initialPath = new Path(List.of(startNode), List.of());

        // Busca as transições épsilon a partir do estado inicial da busca e adiciona todos os estados alcançáveis via transições épsilon à fila principal
        addReachableStates(new SearchState(startNode, automaton.getInitialState(), initialPath), queue, visited, automaton);

        while (!queue.isEmpty()) {
            SearchState currentState = queue.poll();
            Path currentPath = currentState.path;
            Resource currentNode = currentPath.nodes().get(currentPath.nodes().size() - 1);

            // Se um estado de aceitação foi alcançado, adiciona o caminho até ele na lista de resultados
            if (automaton.isFinalState(currentState.automatonState())) {
                finalResults.add(currentPath);
            }

            List<Transition> transitions = automaton.getTransitions(currentState.automatonState());

            for (Transition transition : transitions) {
                if (transition.predicate().equals(AutomatonBuilder.EPSILON)) {
                    addReachableStates(new SearchState(currentNode, transition.targetState(), currentPath), queue, visited, automaton);

                } else if (currentState.graphNode().isResource()) {
                    Property predicateToSearch = this.graph.createProperty(transition.predicate());

                    StmtIterator neighbors = this.graph.listStatements(currentNode, predicateToSearch, (RDFNode) null);

                    while (neighbors.hasNext()) {
                        Statement edge = neighbors.nextStatement();

                        if (edge.getObject().isResource()) {

                            Resource neighborNode = edge.getObject().asResource();
                            Path newPath = currentPath.extend(neighborNode, edge.getPredicate());
                            addReachableStates(new SearchState(neighborNode, transition.targetState(), newPath), queue, visited, automaton);
                        }
                    }
                }
            }
        }

        return filterShortestPaths(finalResults);

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

        PathVisitor visitor = new PathVisitor();

        return visitor.visit(tree);
    }

    /**
     * Calcula o fecho épsilon de um estado de busca e adiciona todos os estados alcançáveis sem consumo de predicados à fila principal da busca.
     * <p>
     *     O fecho épsilon de um estado é o conjunto de todos os estados do autômato que podem ser alcançados a partir dele utilizando apenas transições épsilon (representadas pela constante {@link AutomatonBuilder#EPSILON}).
     * </p>
     * <p>
     *     Este método realiza uma "mini busca em largura" sobre as transições épsilon a partir do {@code startState}. Todos os estados alcançados dessa forma e que ainda não foram visitados (ou foram alcançados por um caminho de custo igual ou menor) são adicionados à {@code mainQueue}, que é a fila principal do algoritmo de busca no grafo RDF.
     * </p>
     *
     * @param startState o estado inicial da mini busca (nó do grafo, estado do autômato e caminho até ele)
     * @param mainQueue a fila principal da busca que será alimentada com estados válidos encontrados via épsilon
     * @param visited mapa que armazena o custo mínimo já conhecido para cada par (nó RDF, estado do autômato)
     * @param automaton o autômato onde as transições épsilon serão verificadas
     */
    private void addReachableStates(SearchState startState, Queue<SearchState> mainQueue, Map<String, Integer> visited, Automaton automaton) {

        Queue<SearchState> epsilonQueue = new LinkedList<>();

        if(canVisit(startState, visited)) {
            epsilonQueue.add(startState);
            visited.put(getKey(startState.graphNode(), startState.automatonState()), startState.path().predicates().size());
        }

        while(!epsilonQueue.isEmpty()) {
            SearchState currentState = epsilonQueue.poll();
            mainQueue.add(currentState);

            List<Transition> transitions = automaton.getTransitions(currentState.automatonState());

            for (Transition transition : transitions) {
                if (transition.predicate().equals(AutomatonBuilder.EPSILON)) {
                    SearchState newState = new SearchState(currentState.graphNode(), transition.targetState(), currentState.path());

                    if (canVisit(newState, visited)) {
                        visited.put(getKey(newState.graphNode(), newState.automatonState()), newState.path().predicates().size());
                        epsilonQueue.add(newState);
                    }
                }
            }
        }
    }

    /**
     * Decide se um estado recém-descoberto deve ser explorado ou descartado, com base na profundidade (custo) do caminho.
     * <p>
     *     O estado é representado por um par (nó do grafo, estado do autômato). Este método:
     * </p>
     * <ul>
     *     <li>Calcula a profundidade do caminho atual (número de predicados percorridos)</li>
     *     <li>Gera uma chave única com {@code getKey()}</li>
     *     <li>Permite a visita se esse par ainda não foi explorado ou se o novo caminho é mais curto ou igual ao já conhecido</li>
     * </ul>
     * <p>
     *     O uso de {@code <=} garante que todos os caminhos mais curtos possíveis sejam considerados.
     * </p>
     *
     * @param state o estado atual da busca (nó do grafo + estado do autômato + caminho até aqui)
     * @param visited mapa que armazena a menor profundidade já encontrada para cada estado visitado
     * @return {@code true} se o estado deve ser visitado; {@code false} caso contrário
     */
    private boolean canVisit(SearchState state, Map<String, Integer> visited) {
        int depth = state.path().predicates().size();
        String key = getKey(state.graphNode(), state.automatonState());
        return !visited.containsKey(key) || depth <= visited.get(key);
    }

    /**
     * Gera uma chave única que identifica um estado da busca no grafo RDF.
     * <p>
     *     Um estado da busca é definido pela combinação de:
     * </p>
     * <ul>
     *     <li>Um nó do grafo RDF ({@code node})</li>
     *     <li>Um estado do autômato ({@code automatonState})</li>
     * </ul>
     * <p>
     *     A chave é usada no mapa {@code visited} para verificar se já passamos por esse par (nó, estado).
     * </p>
     *
     * @param node              o nó atual no grafo RDF
     * @param automatonState    o ID do estado atual no autômato
     * @return uma {@code String} no formato {@code "<nó>:<estado>} que identifica de forma única o par (nó, estado)
     */
    private String getKey(RDFNode node, int automatonState) {
        return node.toString() + ":" + automatonState;
    }

    /**
     * Filtra a lista de caminhos encontrados para manter apenas os caminhos mínimos (com menor número de predicados) até cada nó de destino.
     * <p>
     *     Para cada caminho na entrada:
     *     <ul>
     *         <li>Identifica o nó de destino (último nó do caminho);</li>
     *         <li>Armazena o menor comprimento de caminho encontrado para esse destino;</li>
     *         <li>Retorna apenas os caminhos cujo comprimento é igual ao mínimo identificado para aquele destino.</li>
     *     </ul>
     *     Caminhos duplicados (com mesmos nós e predicados) são eliminados.
     * </p>
     * @param paths a lista de caminhos válidos gerados pela busca
     * @return uma nova lista contendo apenas os caminhos mínimos e únicos para cada destino
     */
    private List<Path> filterShortestPaths(List<Path> paths) {
        Map<String, Integer> shortestLengths = new HashMap<>();

        for (Path path : paths) {
            if (path.nodes().isEmpty()) continue;
            String destURI = path.nodes().get(path.nodes().size() - 1).getURI();
            shortestLengths.merge(destURI, path.predicates().size(), Integer::min);
        }

        List<Path> shortestPaths = new ArrayList<>();
        Set<Path> uniquePaths = new HashSet<>();

        for (Path path : paths) {
            if (path.nodes().isEmpty()) continue;
            String destURI = path.nodes().get(path.nodes().size() - 1).getURI();
            if (path.predicates().size() == shortestLengths.get(destURI)) {
                uniquePaths.add(path);
            }
        }

        shortestPaths.addAll(uniquePaths);
        return shortestPaths;
    }
}


