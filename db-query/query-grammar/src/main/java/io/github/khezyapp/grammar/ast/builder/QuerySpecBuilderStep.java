package io.github.khezyapp.grammar.ast.builder;

import io.github.khezyapp.grammar.ast.ASTSpec;
import io.github.khezyapp.grammar.ast.QuerySpec;
import io.github.khezyapp.grammar.ast.operand.Operand;

public interface QuerySpecBuilderStep {

    interface WhereStep {

        GroupByStep where(ASTSpec spec);
    }

    interface GroupByStep extends BuildStep {

        GroupByStep groupBy(String... path);

        GroupByStep groupBy(Operand... operands);
    }

    interface HavingStep extends BuildStep {

        BuildStep having(ASTSpec spec);
    }

    interface BuildStep {
        QuerySpec build();
    }
}
