package br.com.caroline.sparqlpaths.automaton;

/**
 * Representa uma transição em um autômato finito, rotulado com um predicado RDF.
 * <p>
 * Cada transição liga um estado de origem (implícito no contexto) a um estado de destino ({@code targetState}) por
 * meio de um predicado ({@code predicate}).
 *
 * @param predicate o predicado que rotula a transição
 * @param targetState o estado de destino da transição
 */
public record Transition(String predicate, int targetState) {
}
