package br.com.caroline.sparqlpaths.automaton;

import java.util.*;

/**
 * Representa um autômato finito com transições rotuladas por predicados RDF.
 * <p>
 *     Um autômato é definido por um estado inicial, um conjunto de estados finais e uma função de transição que mapeia
 *     cada estado para uma lista de transições possíveis.
 * </p>
 */
public class Automaton {

    private final int initialState;
    private final Set<Integer> finalStates;
    final Map<Integer, List<Transition>> transitions = new HashMap<>();

    /**
     * Cria um novo autômato com um estado inicial e um conjunto de estados finais.
     *
     * @param initialState o estado inicial do autômato
     * @param finalStates o conjunto de estados finais
     */
    public Automaton(int initialState, Set<Integer> finalStates) {
        this.initialState = initialState;
        this.finalStates = finalStates;
    }

    public int getInitialState() {
        return initialState;
    }

    public Set<Integer> getFinalStates() {
        return finalStates;
    }

    /**
     * Adiciona uma nova transição ao autômato.
     */
    public void addTransition(int source, String predicate, int target) {
        this.transitions.computeIfAbsent(source, k -> new ArrayList<>()).add(new Transition(predicate, target));
    }

    /**
     * Retorna a lista de transições possíveis a partir de um estado específico.
     * <p>
     *     Caso o estado não tenha transições definidas, uma lista vazia é retornada.
     * </p>
     *
     * @param source o estado de origem
     * @return uma lista de {@link Transition} a partir do estado informado,
     *         ou uma lista vazia se não houver transições
     */
    public List<Transition> getTransitions(int source) {
        return this.transitions.getOrDefault(source, Collections.emptyList());
    }

    /**
     * Verifica se o estado informado pertence ao conjunto de estados finais do autômato.
     *
     * @param state o estado a ser verificado
     * @return {@code true} se o estado for final, {@code false} caso contrário
     */
    public boolean isFinalState(int state) {
        return this.finalStates.contains(state);
    }

    /**
     * Cria e retorna uma nova instância do autômato com as transições invertidas.
     * <p>
     *     Cada transição original do tipo {@code (source) --p--> (target)} será transformada em uma nova transição
     *     do tipo {@code (source) --^p--> (target)}, onde o símbolo {@code ^} é adicionado ao predicado original
     *     para indicar que se trata de uma transição invertida.
     * </p>
     * <p>
     *     O novo autômato gerado mantém o mesmo estado inicial e o mesmo conjunto de estados finais.
     * </p>
     *
     * @return um novo {@link Automaton} contendo as mesmas transições, mas com os predicados invertidos.
     */
    public Automaton withInvertedTransitions() {
        Automaton invertedAutomaton = new Automaton(this.initialState, this.finalStates);

        this.transitions.forEach((sourceState, transitionsList) -> {
            for (Transition transition : transitionsList) {
                if (transition.predicate().equals(AutomatonBuilder.EPSILON)) {
                    invertedAutomaton.addTransition(sourceState, transition.predicate(), transition.targetState());
                } else {
                    invertedAutomaton.addTransition(sourceState, "^" + transition.predicate(), transition.targetState());
                }
            }
        });
        return invertedAutomaton;
    }

    /**
     * Método auxiliar para imprimir a estrutura de um autômato de forma legível.
     * @param description Uma descrição para o teste.
     * @param automaton O autômato a ser impresso.
     */
    public static void printAutomaton(String description, Automaton automaton) {
        System.out.println("--- Teste para: " + description + " ---");
        if (automaton == null) {
            System.out.println("Autômato é nulo.");
            return;
        }
        System.out.println("Estado Inicial: " + automaton.getInitialState());
        System.out.println("Estados Finais: " + automaton.getFinalStates());
        System.out.println("Mapa de Transições:");

        automaton.transitions.keySet().stream()
                .sorted()
                .forEach(sourceState -> {
                    System.out.println("  Do estado " + sourceState + ":");
                    // Pega a lista de transições para este estado específico
                    for (Transition t : automaton.getTransitions(sourceState)) {
                        System.out.println("    --[" + t.predicate() + "]--> " + t.targetState());
                    }
                });
        System.out.println("--------------------------------------------------\n");
    }
}
