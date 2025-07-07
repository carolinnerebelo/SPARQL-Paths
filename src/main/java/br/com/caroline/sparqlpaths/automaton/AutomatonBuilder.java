package br.com.caroline.sparqlpaths.automaton;

import java.util.HashSet;
import java.util.Set;

/**
 * Classe utilitária para construção de autômatos finitos direcionados ao reconhecimento de expressões de caminhos
 * (Property Paths) em RDF usados em consultas SPARQL.
 * <p>
 *     A classe {@link AutomatonBuilder} oferece métodos para criar autômatos que representam operações básicas
 * e compostas sobre caminhos.
 * </p>
 * <p>
 *     Os autômatos construídos são formados por estados identificados por inteiros e transições rotuladas por
 *     predicados, incluindo a constante especial {@link #EPSILON}, que representa transições vazias.
 * </p>
 */
public class AutomatonBuilder {
    public static final String EPSILON = "##EPSILON##";
    private int nextStateId = 0;

    /**
     * Gera e retorna um novo identificador único para um estado.
     * <p>
     *     Cada chamada retorna um ID inteiro crescente, garantindo que nenhum estado gerado
     *     tenha o mesmo identificador de outro já criado anteriormente.
     * </p>
     *
     * @return um novo ID de estado único
     */
    private int newState() {
        return nextStateId++;
    }

    /**
     * Cria um autômato simples que reconhece um único predicado.
     * <p>
     *     A estrutura gerada é composta por dois estados: um estado inicial e um estado final,
     *     com uma única transição entre eles rotulada com o predicado fornecido.
     * </p>
     * <p>
     *     Estrutura resultante:
     *     {@code (start) --[predicate]--> (final)}
     * </p>
     *
     * @param predicate o predicado a ser reconhecido pelo autômato
     * @return um {@link Automaton} que aceita apenas uma ocorrência do predicado informado
     */
    public Automaton fromPredicate(String predicate) {
        int startState = newState();
        int finalState = newState();

        Automaton automaton = new Automaton(startState, Set.of(finalState));
        automaton.addTransition(startState, predicate, finalState);
        return automaton;
    }

    /**
     * Cria um autômato concatenando dois autômatos em sequência, representando um caminho composto (elt1 / elt2).
     * <p>
     *     A sequência é construída conectando todos os estados finais do primeiro autômato ({@code a1}) ao estado
     *     inicial do segundo autômato ({@code a2}) por meio de uma transição {@link #EPSILON}, que representa uma
     *     transição vazia (sem consumo de predicado).
     * </p>
     * <p>
     *     O autômato resultante começa no estado inicial de {@code a1} e termina nos estados finais de {@code a2}.
     * </p>
     *
     * @param a1 o primeiro autômato da sequência
     * @param a2 o segundo autômato da sequência
     * @return um novo {@link Automaton} que representa a sequência de {@code a1} seguido de {@code a2}
     */
    public Automaton sequence(Automaton a1, Automaton a2) {
        Automaton result = new Automaton(a1.getInitialState(), a2.getFinalStates());

        result.transitions.putAll(a1.transitions);
        result.transitions.putAll(a2.transitions);

        for (int finalStateA1 : a1.getFinalStates()) {
            result.addTransition(finalStateA1, EPSILON, a2.getInitialState());
        }

        return result;
    }

    /**
     * Cria um autômato que representa a alternativa entre dois caminhos (elt1 | elt2).
     * <p>
     *     O novo autômato começa em um novo estado inicial, que possui transições {@link #EPSILON} para os estados
     *     iniciais de {@code a1} e {@code a2}. Os estados finais são a união dos estados finais de ambos os autômatos.
     * </p>
     *
     * @param a1 o primeiro autômato da alternativa
     * @param a2 o segundo autômato da alternativa
     * @return um novo {@link Automaton} que representa a escolha entre {@code a1} ou {@code a2}
     */
    public Automaton alternative(Automaton a1, Automaton a2) {
        int newStartState = newState();
        Set<Integer> newFinalStates = new HashSet<>();

        newFinalStates.addAll(a1.getFinalStates());
        newFinalStates.addAll(a2.getFinalStates());

        Automaton result = new Automaton(newStartState, newFinalStates);

        result.transitions.putAll(a1.transitions);
        result.transitions.putAll(a2.transitions);

        result.addTransition(newStartState, EPSILON, a1.getInitialState());
        result.addTransition(newStartState, EPSILON, a2.getInitialState());

        return result;

    }

    /**
     * Cria um autômato que representa o fecho de Kleene (zero ou mais repetições) de um caminho (elt*).
     * <p>
     *     O autômato resultante aceita qualquer número de repetições do autômato {@code a}, incluindo a possibilidade
     *     de não executar nenhuma transição (caminho vazio).
     * </p>
     * <p>
     *     A estrutura inclui:
     * </p>
     * <ul>
     *     <li>Um novo estado inicial com transições {@link #EPSILON} para o início de {@code a} e para o novo
     *     estado final</li>
     *     <li>Transições originais de {@code a}</li>
     *     <li>Transições dos estados finais de {@code a} de volta para seu estado inicial (loop)</li>
     *     <li>Transições dos estados finais de {@code a} para o novo estado final</li>
     * </ul>
     *
     * @param a o autômato a ser repetido zero ou mais vezes
     * @return um novo {@link Automaton} que representa {@code a*}
     */
    public Automaton zeroOrMore(Automaton a) {
        int newStartState = newState();
        int newFinalState = newState();

        Automaton result = new Automaton(newStartState, Set.of(newFinalState));

        result.transitions.putAll(a.transitions);

        result.addTransition(newStartState, EPSILON, a.getInitialState());
        result.addTransition(newStartState, EPSILON, newFinalState);

        for (int finalStateA : a.getFinalStates()) {
            result.addTransition(finalStateA, EPSILON, a.getInitialState());
            result.addTransition(finalStateA, EPSILON, newFinalState);
        }

        return result;

    }

    /**
     * Cria um autômato que representa uma ou mais repetições de um caminho (elt+).
     * <p>
     *     O autômato resultante aceita uma execução obrigatória do autômato {@code a}, seguida de repetições
     *     opcionais dele.
     * </p>
     * <p>
     *     A estrutura inclui:
     * </p>
     * <ul>
     *     <li>Transições originais de {@code a}</li>
     *     <li>Transições dos estados finais de {@code a} de volta ao seu início (loop)</li>
     *     <li>Transições dos estados finais de {@code a} para o novo estado final</li>
     * </ul>
     *
     * @param a o autômato a ser repetido uma ou mais vezes
     * @return um novo {@link Automaton} que representa {@code a+}
     */
    public Automaton oneOrMore(Automaton a) {
        int newFinalState = newState();

        Automaton result = new Automaton(a.getInitialState(), Set.of(newFinalState));
        result.transitions.putAll(a.transitions);

        for (int finalStateA : a.getFinalStates()) {
            result.addTransition(finalStateA, EPSILON, a.getInitialState());
            result.addTransition(finalStateA, EPSILON, newFinalState);
        }

        return result;
    }

    /**
     * Cria um autômato que representa zero ou uma ocorrência de um caminho (elt?).
     * <p>
     *     O autômato resultante aceita o caminho descrito por {@code a}, mas também permite que ele seja
     *     completamente ignorado (caminho vazio).
     * </p>
     * <p>
     *     A estrutura inclui:
     * </p>
     * <ul>
     *     <li>Transições originais de {@code a}</li>
     *     <li>Um novo estado inicial com transições {@link #EPSILON} para o início de {@code a} e para cada estado
     *     final de {@code a}</li>
     * </ul>
     *
     * @param a o autômato a ser tornado opcional
     * @return um novo {@link Automaton} que representa {@code a?}
     */
    public Automaton zeroOrOne(Automaton a) {
        int newStartState = newState();

        Automaton result = new Automaton(newStartState, a.getFinalStates());
        result.transitions.putAll(a.transitions);

        result.addTransition(newStartState, EPSILON, a.getInitialState());

        for (int finalStateA : a.getFinalStates()) {
            result.addTransition(newStartState, EPSILON, finalStateA);
        }

        return result;

    }
}


