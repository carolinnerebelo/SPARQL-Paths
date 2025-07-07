package br.com.caroline.sparqlpaths.model;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa um caminho percorrido em um grafo RDF, contendo os nós visitados e os predicados utilizados.
 * <p>
 * Um {@code Path} é composto por:
 * <ul>
 *     <li>Uma lista ordenada de {@link Resource}s ({@code nodes}), representando os nós do grafo RDF visitados na ordem em que foram percorridos.</li>
 *     <li>Uma lista correspondente de {@link Property}s ({@code predicates}), representando as arestas (predicados) utilizadas para transitar entre os nós.</li>
 * </ul>
 * O número de predicados é sempre igual a {@code nodes.size() - 1}.
 * </p>
 *
 * <p>
 * A classe é imutável e modelada como um {@code record}, facilitando a manipulação de caminhos durante algoritmos de busca,
 * especialmente em aplicações como avaliação de expressões regulares sobre grafos RDF.
 * </p>
 *
 * @param nodes a lista ordenada de nós RDF visitados
 * @param predicates a lista ordenada de predicados utilizados nas transições entre os nós
 */
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
