package br.com.caroline.sparqlpaths.application;

import br.com.caroline.sparqlpaths.sparql.PathFinderPropertyFunction;
import org.apache.jena.graph.Node;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.sparql.pfunction.PropertyFunctionRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args) {

        String graphFile;

        if (args.length > 0) {
            graphFile = args[0];
        } else {
            graphFile = "graph.ttl";
        }

        System.out.println("Iniciando a aplicação...");

        try {
            // Carrega o grafo a partir do arquivo
            Model graph = loadGraph(graphFile);
            System.out.println("Grafo carregado: " + graphFile);

            System.out.println("Digite a consulta SPARQL (encerre com linha vazia): ");

            // String simpleQuery = readFromStdin();
            String simpleQuery = """
            PREFIX ex: <http://exemplo.org/grafo-pathfinder#>

            SELECT ?caminhoFinal
            WHERE {
                ex:Joe "ex:follows+/ex:works" ?caminhoFinal .
            }
            """;

            String queryString = transformQuery(simpleQuery);

            // Registro da Property Function
            String pfURI = "http://example.com/ns#pathFinder";
            PropertyFunctionRegistry.get().put(pfURI, PathFinderPropertyFunction.class);

            ResultSet resultsCopy;
            try (QueryExecution qe = QueryExecutionFactory.create(queryString, graph)) {
                // Executamos a consulta e armazenamos
                ResultSet liveResults = qe.execSelect();

                // Cria uma cópia em memória do resultado para que possa ser reutilizado
                resultsCopy = ResultSetFactory.copyResults(liveResults);
            }

            System.out.println("--- RESULTADOS ---");
            // Imprime a versão "legível" dos caminhos
            printReconstructedPaths(resultsCopy, graph);

            // 4. Imprime a versão "SPARQL"
            printSparqlTable(resultsCopy);


        } catch (Exception e) {
            System.out.println("Erro na aplicação: " + e.getMessage());
        }

    }

    /**
     * Lê múltiplas linhas da entrada padrão (terminal) até encontrar uma linha vazia.
     * <p>
     * Este método permite que o usuário digite uma consulta SPARQL manualmente no terminal,
     * encerrando a entrada ao pressionar Enter em uma linha em branco.
     * </p>
     *
     * @return uma {@link String} contendo todas as linhas digitadas, separadas por quebras de linha
     * @throws IOException se ocorrer um erro durante a leitura da entrada
     */
    private static String readFromStdin() throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            if (line.isEmpty()) break;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * Carrega um grafo RDF a partir de um arquivo e o retorna como um objeto {@link Model} do Apache Jena.
     * <p>
     * O método tenta ler o arquivo RDF especificado (geralmente localizado em {@code src/main/resources}) e o carrega
     * na memória. Se houver erro de leitura ou o grafo estiver vazio, uma exceção é lançada.
     *
     * @param graphFile o caminho relativo para o arquivo RDF a ser carregado
     * @return um objeto {@link Model} representando o grafo RDF carregado
     * @throws RuntimeException se o arquivo não puder ser lido, analisado ou estiver vazio
     */
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

    /**
     * Imprime no console todas as triplas (sujeito, predicado, objeto) presentes em um grafo RDF.
     * <p>
     * O método percorre o modelo RDF fornecido utilizando um {@link StmtIterator} e exibe cada tripla no formato
     * {@code sujeito | predicado | objeto}. Se o objeto for um literal, ele é impresso entre aspas simples.
     *
     * @param graph o objeto {@link Model} que representa o grafo RDF a ser impresso
     */
    public static void printGraph(Model graph) {
        System.out.println("Listando todas as triplas do grafo:");

        // StmtIterator é um iterador para percorrer todas as triplas do grafo
        StmtIterator iter = graph.listStatements();

        while (iter.hasNext()) {
            Statement stmt = iter.nextStatement(); // pega a próxima tripla
            Resource subject = stmt.getSubject(); // pega o sujeito
            Property predicate = stmt.getPredicate(); // pega o predicado
            RDFNode object = stmt.getObject(); // pega o objeto

            // Imprime as triplas
            if (object.isLiteral()) {
                System.out.println(subject.getURI() + " | " + predicate.getURI() + " | '" + object + "'");
                System.out.println();
            } else {
                System.out.println(subject.getURI() + " | " + predicate.getURI() + " | " + object);
                System.out.println();
            }
        }
    }

    /**
     * Reconstrói e imprime, de forma legível, os caminhos encontrados pela consulta SPARQL executada.
     * <p>
     * Cada linha da {@link ResultSet} deve conter:
     * <ul>
     *     <li>{@code pathId}: um identificador único para o caminho</li>
     *     <li>{@code stepIndex}: a posição do passo dentro do caminho</li>
     *     <li>{@code predicate} (opcional): a aresta usada entre os nós</li>
     *     <li>{@code node}: o nó do grafo RDF correspondente ao passo atual</li>
     * </ul>
     * O método organiza os passos de cada caminho em ordem crescente de {@code stepIndex} e depois monta
     * uma representação textual de cada caminho utilizando {@link #buildPathString(List, Model)}.
     * </p>
     *
     * @param results o {@link ResultSet} retornado por uma consulta SPARQL com os campos {@code pathId}, {@code stepIndex}, {@code predicate} e {@code node}
     * @param graph o modelo RDF utilizado para imprimir os prefixos legíveis de URIs
     */
    private static void printReconstructedPaths(ResultSet results, Model graph) {
        results.rewindable();
        Map<Integer, TreeMap<Integer, Node[]>> pathsMap = new HashMap<>();

        while (results.hasNext()) {
            var sol = results.next();

            int pathId = sol.get("pathId").asLiteral().getInt();
            int stepIndex = sol.get("stepIndex").asLiteral().getInt();
            Node predicate = sol.contains("predicate") ? sol.get("predicate").asNode() : null;
            Node node = sol.get("node").asNode();

            pathsMap.computeIfAbsent(pathId, k-> new TreeMap<>());

            pathsMap.get(pathId).put(stepIndex, new Node[]{predicate, node});
        }

        System.out.println("\nCaminho reconstruído: ");
        System.out.println("Encontrados " + pathsMap.size() + " caminhos:");

        int count = 1;
        for (TreeMap<Integer, Node[]> stepsMap : pathsMap.values()) {
            List<Node[]> orderedSteps = new ArrayList<>(stepsMap.values());
            String pathStr = buildPathString(orderedSteps, graph);
            System.out.println("Caminho " + (count++) + ": " + pathStr);

        }
    }

    /**
     * Imprime no console uma tabela formatada contendo os resultados de uma consulta SPARQL.
     * <p>
     * O método usa a utilidade {@link ResultSetFormatter#out} da API Jena para exibir o {@link ResultSet}
     * em formato tabular legível. Antes de imprimir, ele torna o resultado "rebobinável" chamando {@code results.rewindable()},
     * o que garante que todos os resultados sejam exibidos a partir do início.
     * </p>
     *
     * @param results o conjunto de resultados da consulta SPARQL a ser impresso
     */
    private static void printSparqlTable(ResultSet results) {
        results.rewindable();
        System.out.println("\nTabela de Resultados do Jena: ");
        ResultSetFormatter.out(System.out, results);
    }

    /**
     * Constrói uma representação textual legível de um caminho, formatada com prefixos definidos no modelo RDF.
     * <p>
     * Cada passo do caminho é impresso no formato:
     * <pre>
     * (nó_inicial) --[predicado]--> (nó_destino)
     * </pre>
     * Os predicados e nós são exibidos usando os prefixos registrados no {@link Model}, quando disponíveis.
     * Transições que não possuem predicados (ex: transições épsilon) são representadas com uma seta simples ({@code -->}).
     * </p>
     *
     * @param steps a lista ordenada de pares [predicado, nó], representando cada passo do caminho
     * @param model o modelo RDF usado para resolver prefixos (via {@code model.shortForm(uri)})
     * @return uma string representando o caminho reconstruído com nomes abreviados (prefixos)
     */
    private static String buildPathString(List<Node[]> steps, Model model) {
        if (steps.isEmpty()) return "(vazio)";

        StringBuilder sb = new StringBuilder();

        Node firstNode = steps.get(0)[1];
        sb.append("(").append(getPrefixedName(firstNode, model)).append(")");

        for (int i = 1; i < steps.size(); i++) {
            Node predicateNode = steps.get(i)[0];
            Node objectNode = steps.get(i)[1];

            if (predicateNode != null) {
                sb.append(" --[").append(getPrefixedName(predicateNode, model)).append("]--> ");
            } else {
                sb.append(" --> ");
            }
            sb.append("(").append(getPrefixedName(objectNode, model)).append(")");
        }

        return sb.toString();
    }

    /**
     * Retorna o nome com prefixo de um nó RDF com base nas definições de prefixo do modelo.
     * <p>
     * Se o nó for uma URI, tenta obter sua forma abreviada (como {@code ex:works}) usando {@code model.shortForm(uri)}.
     * Se for um literal, retorna sua forma textual. Outros tipos de nó são retornados como {@code toString()}.
     * </p>
     *
     * @param node o nó RDF a ser formatado
     * @param model o modelo RDF com os mapeamentos de prefixo
     * @return a representação abreviada do nó com prefixo, ou sua forma literal/textual
     */
    private static String getPrefixedName(Node node, Model model) {
        if (node.isURI()) {
            String uri = node.getURI();
            String prefixed = model.shortForm(uri);
            return prefixed;
        } else if (node.isLiteral()) {
            return node.getLiteralLexicalForm();
        } else {
            return node.toString();
        }
    }

    /**
     * Transforma uma consulta SPARQL simplificada no formato completo que a nossa
     * PropertyFunction espera.
     * @param simpleQuery A consulta no formato simplificado.
     * @return A consulta completa e pronta para ser executada pelo Jena.
     */
    private static String transformQuery(String simpleQuery) {

        String pfURI = "http://example.com/ns#pathFinder";
        String pfNamespace = "http://example.com/ns#";
        // Esta expressão regular busca pelo padrão: { ?sujeito "regex" ?objeto . }
        // Ela captura o sujeito, a regex e o objeto em grupos.
        Pattern pattern = Pattern.compile(
                "\\{\\s*([^\\s]+)\\s*\"([^\"]+)\"\\s*(\\?[^\\s\\}]+)\\s*\\.\\s*\\}",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(simpleQuery);

        if (matcher.find()) {
            String subject = matcher.group(1);
            String regex = matcher.group(2);
            String objectVar = matcher.group(3);

            // Monta a nova cláusula WHERE para a PropertyFunction
            String propertyFunctionClause = String.format(
                    "(%s \"%s\") myfns:pathFinder (?pathId ?stepIndex ?predicate %s) .",
                    subject,
                    regex,
                    objectVar
            );

            // Substitui o SELECT e o WHERE
            String transformedQuery = simpleQuery
                    .replaceFirst("SELECT\\s+\\S+", "SELECT ?pathId ?stepIndex ?predicate (" + objectVar + " AS ?node)")
                    .replace(matcher.group(0), "{\n    " + propertyFunctionClause + "\n}");

            // Adiciona a declaração do prefixo 'myfns' no início da consulta transformada
            String finalQuery = "PREFIX myfns: <" + pfNamespace + ">\n" + transformedQuery;

            // Adiciona o ORDER BY
            return finalQuery + "\nORDER BY ?pathId ?stepIndex";
        }

        return simpleQuery;
    }
}