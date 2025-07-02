grammar PropertyPath;

// A regra principal: o que queremos analisar é um 'path' completo.
// EOF = End Of File, garante que a string inteira foi consumida.
start: path EOF;

// A regra 'path' é o coração dos property paths do SPARQL.
// Ela define a alternativa ( | ) como o operador de menor precedência.
path: pathSequence ( '|' pathSequence )*;

// A regra 'pathSequence' define a sequência ( / ).
pathSequence: pathEltOrInverse ( '/' pathEltOrInverse )*;

// 'pathEltOrInverse' lida com um elemento de caminho normal ou sua forma inversa (^).
pathEltOrInverse: pathElt | '^' pathElt;

// 'pathElt' é o elemento de caminho com seu modificador opcional (*, +, ?).
pathElt: pathPrimary pathMod?;

// 'pathMod' define os modificadores possíveis.
pathMod: '*' | '?' | '+';

// 'pathPrimary' é a unidade básica: ou um predicado (iri) ou uma expressão entre parênteses.
pathPrimary: iri | '(' path ')';

// As regras abaixo são para o "dicionário" (Lexer). Elas definem o que é uma IRI,
// um nome prefixado, etc.

iri: IRI_REF | PrefixedName;

IRI_REF: '<' ( ~[<>"{}|^`\\] )* '>';
PrefixedName: PNAME_LN | PNAME_NS;
PNAME_LN: PNAME_NS PN_LOCAL;
PNAME_NS: PN_PREFIX? ':';

PN_PREFIX: [a-zA-Z]+;
PN_LOCAL: ( [a-zA-Z0-9] | '_' | '-' | '.' )+;

// Ignora espaços em branco, tabulações e quebras de linha.
WS: [ \t\r\n]+ -> skip;