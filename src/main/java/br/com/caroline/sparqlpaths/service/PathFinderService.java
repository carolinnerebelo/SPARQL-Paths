package br.com.caroline.sparqlpaths.service;

import br.com.caroline.sparqlpaths.model.Path;
import org.apache.jena.rdf.model.Model;

import java.util.ArrayList;
import java.util.List;

public class PathFinderService {
    private final Model graph;

    public PathFinderService(Model graph) {
        this.graph = graph;
    }

    public List<Path> findPaths() {

        // Lógica do algoritmo 2 aqui
        System.out.println("LOG: Grafo lido em PathFinderService:\n" + graph.getGraph());

        return new ArrayList<>();
    }
}
