# SPARQL Paths - Projeto de Extensão de SPARQL com Retorno de Caminhos

Este projeto é uma prova de conceito que integra o algoritmo PathFinder ao motor Apache Jena para estender o suporte a *Property Paths* no SPARQL, permitindo o retorno dos caminhos completos encontrados.

---

## Estrutura do Projeto

- `src/` — código-fonte Java da aplicação principal.
- `test/` — código de testes automatizados (JUnit), incluindo benchmarks.
- `pom.xml` — arquivo de configuração Maven, com dependências e plugin para ANTLR.

---

## Pré-requisitos

- Java JDK 21 instalado e configurado no PATH.
- Maven 3.8+ instalado e configurado no PATH.
- (Opcional) IntelliJ IDEA com suporte nativo a Maven

---

## Compilação

Para compilar o projeto, rodar:

```bash
mvn clean compile
```

Esse comando também irá acionar o plugin do ANTLR e gerar os arquivos necessários a partir da gramática PropertyPath.g4.

## Execução

O programa pode ser executado com o seguinte comando no terminal:

```bash
mvn exec:java -Dexec.mainClass="br.com.caroline.sparqlpaths.application.Main"
```

A aplicação aceita um argumento opcional, que é o arquivo `.ttl` (ele deve estar na pasta `src/main/resources/`).

Executando com argumento:

```bash
mvn compile exec:java -Dexec.mainClass="br.com.caroline.sparqlpaths.application.Main" -Dexec.args="grafo.ttl"
```

Caso nenhum argumento seja fornecido, será carregado automaticamente o grafo de exemplo `grafo_pathfinder.ttl`.

Será solicitado ao usuário que digite uma consulta SPARQL com uma Property Function personalizada.

A consulta SPARQL deve ter o seguinte formato:

```bash
PREFIX myfns: <http://example.com/ns#>
PREFIX ex: <http://exemplo.org/algum-grafo#>

SELECT ?pathId ?stepIndex ?predicate ?node
WHERE {
  (<no_inicial> "<expressão_de_caminho>") myfns:pathFinder (?pathId ?stepIndex ?predicate ?node) .
}
ORDER BY ?pathId ?stepIndex
```

## Organização dos Diretórios

`src/main/resources/`: Grafos RDF para serem usados na aplicação.

`src/main/java/`: Código-fonte da aplicação.

`src/test/java/`: Código de testes e benchmarks, além do conversor do Pokec.

`src/test/resources/pokec/`: Subgrafos RDF da Pokec Social Network, usados nos testes.

`src/main/antlr4/`: Arquivo de gramática ANTLR (PropertyPath.g4).

## Testes e Benchmarks

Os testes automatizados com JUnit incluem a execução de benchmarks em subgrafos da Pokec Social Network.

Para executar os testes:

```bash
mvn test
```

Isso irá rodar a classe SPARQLPathsTest, que mede os tempos médios de execução das consultas sobre os arquivos .ttl localizados em src/test/resources/pokec/.

O resultado dos benchmarks será salvo no arquivo benchmark_results.csv, gerado na raiz do projeto.