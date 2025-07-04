package br.com.caroline.sparqlpaths.application;


import br.com.caroline.sparqlpaths.model.Path;
import br.com.caroline.sparqlpaths.service.PathFinderService;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;

import java.util.List;
import java.util.Map;

public class Main {

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

    public static void main(String[] args) {

        String graphFile;

        if (args.length > 0) {
            graphFile = args[0];
        } else {
            graphFile = "graph.ttl";
        }

        String startNodeURI = "http://exemplo.org/grafo-pathfinder#John"; // TODO permitir consultas sem especificar o nó inicial (ou esse nó sendo dado pelo próprio Jena quando integramos com a consulta SPARQL)
//         String regex = "<http://exemplo.org/grafo-pathfinder#follows>+ | <http://exemplo.org/grafo-pathfinder#lives>";
        String regex = "^ex:follows*"; // TODO permitir consultas com prefixos ao invés de URIs

        System.out.println("Iniciando a aplicação...");

        try {
            // Carrega o grafo a partir do arquivo
            Model graph = loadGraph(graphFile);
            System.out.println("Grafo carregado: " + graphFile);

            Map<String, String> prefixMap = graph.getNsPrefixMap();

            PathFinderService pathFinder = new PathFinderService(graph, prefixMap);
            List<Path> results = pathFinder.findPaths(startNodeURI, regex);

            // Exibe o resultado
            System.out.println("\n--- RESULTADO DA BUSCA ---");
            if (results.isEmpty()) {
                System.out.println("Nenhum caminho encontrado.");
            } else {
                System.out.println("Encontrados " + results.size() + " caminhos:");
                int count = 1;
                for (Path path : results) {
                    System.out.println("Caminho " + (count++) + ": " + path.toString());
                }
            }

            System.out.println("--------------------------");

        } catch (RiotException e) {
            System.err.println("Erro na aplicação: " + e.getMessage());
        }

    }
}