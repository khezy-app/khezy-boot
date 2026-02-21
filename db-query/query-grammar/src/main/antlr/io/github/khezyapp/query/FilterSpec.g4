grammar FilterSpec;

@header {
    package io.github.khezyapp.query;
}

filterSpec
    : where=orExpr
      groupByClause?
      havingClause?
      EOF                                   # rootQuery
    ;

orExpr
    : andExpr (OR andExpr)*                 # logicalOrExpr
    ;

andExpr
    : primaryExpr (AND primaryExpr)*        # logicalAndExpr
    ;

primaryExpr
    : '(' orExpr ')'                        # parenExpr
    | comparison                            # comparisonExpr
    ;

comparison
    : joinType? left=selectable comparisonRest # baseComparison
    ;

selectable
    : path                              # pathSelectable
    | aggregateFunction                 # aggregateSelectable
    ;

comparisonRest
    : op=operator val=value             # simpleComparison
    | IN '(' value (',' value)* ')'     # inComparison
    | BETWEEN start=value AND end=value # betweenComparison
    | IS NULL                           # nullComparison
    | IS NOT NULL                       # notNullComparison
    ;

groupByClause
    : GROUP BY groupItem (',' groupItem)*
    ;

groupItem
    : path                              # pathGroupItem
    | aggregateFunction                 # aggregateGroupItem
    ;

havingClause
    : HAVING orExpr
    ;

joinType    : LEFT | INNER | RIGHT ;
operator    : '=' | '!=' | '<' | '<=' | '>' | '>=' ;

value
    : NUMBER
    | STRING
    | path
    | aggregateFunction
    ;

path
    : anyIdentifier ('.' anyIdentifier)*
    ;

// This allows keywords to be used as field names
anyIdentifier
    : IDENTIFIER
    | BY
    | LEFT
    | INNER
    | RIGHT
    | GROUP
    | HAVING
    ;

aggregateFunction
    : (COUNT | SUM | AVG | MIN | MAX)
      '(' (path | '*') ')'
    ;

AND         : [Aa][Nn][Dd] ;
OR          : [Oo][Rr] ;
IN          : [Ii][Nn] ;
BETWEEN     : [Bb][Ee][Tt][Ww][Ee][Ee][Nn] ;
IS          : [Ii][Ss] ;
NULL        : [Nn][Uu][Ll][Ll] ;
NOT         : [Nn][Oo][Tt] ;
GROUP       : [Gg][Rr][Oo][Uu][Pp] ;
BY          : [Bb][Yy] ;
HAVING      : [Hh][Aa][Vv][Ii][Nn][Gg] ;
COUNT       : [Cc][Oo][Uu][Nn][Tt] ;
SUM         : [Ss][Uu][Mm] ;
AVG         : [Aa][Vv][Gg] ;
MIN         : [Mm][Ii][Nn] ;
MAX         : [Mm][Aa][Xx] ;

LEFT        : [Ll][Ee][Ff][Tt] ;
INNER       : [Ii][Nn][Nn][Ee][Rr] ;
RIGHT       : [Rr][Ii][Gg][Hh][Tt] ;

IDENTIFIER  : [a-zA-Z_][a-zA-Z_0-9]* ;
NUMBER      : [0-9]+ ('.' [0-9]+)? ;
STRING      : '\'' ( '\\' . | ~['\\\r\n] )* '\'' ;
WS          : [ \t\r\n]+ -> skip ;
