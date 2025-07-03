package br.com.caroline.sparqlpaths.model;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

import java.util.ArrayList;
import java.util.List;

public record Path(List<Resource> nodes, List<Property> predicates) {

    /**
     * Cria um novo {@link Path} estendendo o caminho atual com um novo nó e um novo predicado.
     * <p>
     *     O novo caminho é uma cópia do caminho atual, com:
     * <ul>
     *     <li>o {@code newNode} adicionado à lista de nós visitados,</li>
     *     <li>e, se {@code newPredicate} não for {@code null}, ele também é adicionado à lista de predicados</li>
     * </ul>
     *      Isso permite representar o avanço da busca no grafo RDF a partir de uma transição no autômato.
     *      Transições épsilon (representadas por predicados nulos) são ignoradas na reconstrução do caminho.
     * </p>
     *
     * @param newNode o novo nó do grafo RDF alcançado pela transição
     * @param newPredicate o predicado usado para alcançar o novo nó, ou {@code null} se a transição for épsilon
     * @return um novo {@link Path} que representa o caminho atual estendido com o novo passo
     */
    public Path extend(Resource newNode, Property newPredicate) {
        List<Resource> newNodes = new ArrayList<>(this.nodes);
        newNodes.add(newNode);

        List<Property> newPredicates = new ArrayList<>(this.predicates);

        if (newPredicate != null) {
            newPredicates.add(newPredicate);
        }

        return new Path(newNodes, newPredicates);
    }

    @Override
    public String toString() {

        if (nodes == null || nodes.isEmpty()) {
            return "Caminho vazio";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("(").append(nodes.get(0).getLocalName()).append(")");

        for (int i = 0; i < predicates.size(); i++) {
            sb.append(" --[").append(predicates.get(i).getLocalName()).append("]--> ");
            sb.append("(").append(nodes.get(i+1).getLocalName()).append(")");
        }

        return sb.toString();

    }

}
