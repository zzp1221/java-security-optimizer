package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ResourceNotClosedRule extends AbstractJavaRule {
    private static final Set<String> CLOSEABLE_TYPES = Set.of(
            "FileInputStream", "FileOutputStream", "BufferedReader", "BufferedWriter",
            "InputStream", "OutputStream", "Reader", "Writer", "Scanner"
    );

    @Override
    public String id() {
        return "JAVA.RESOURCE.NOT_CLOSED";
    }

    @Override
    public String description() {
        return "识别未关闭的 I/O 资源";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (MethodDeclaration method : compilationUnit.findAll(MethodDeclaration.class)) {
            if (method.getBody().isEmpty()) {
                continue;
            }
            for (VariableDeclarationExpr declaration : method.findAll(VariableDeclarationExpr.class)) {
                declaration.getVariables().forEach(variable -> {
                    if (variable.getInitializer().isEmpty()
                            || !(variable.getInitializer().get() instanceof ObjectCreationExpr objectCreationExpr)) {
                        return;
                    }
                    String typeName = objectCreationExpr.getType().getNameAsString();
                    if (!CLOSEABLE_TYPES.contains(typeName)) {
                        return;
                    }
                    String varName = variable.getNameAsString();
                    boolean closed = method.findAll(MethodCallExpr.class).stream()
                            .anyMatch(call -> call.getNameAsString().equals("close")
                                    && call.getScope().isPresent()
                                    && call.getScope().get().toString().equals(varName));
                    boolean inTryWithResources = method.findAll(TryStmt.class).stream()
                            .flatMap(tryStmt -> tryStmt.getResources().stream())
                            .filter(VariableDeclarationExpr.class::isInstance)
                            .map(VariableDeclarationExpr.class::cast)
                            .flatMap(v -> v.getVariables().stream())
                            .anyMatch(v -> v.getNameAsString().equals(varName));
                    if (!closed && !inTryWithResources) {
                        issues.add(issue(context, variable, IssueSeverity.HIGH,
                                "资源 " + varName + " 可能未关闭，建议使用 try-with-resources"));
                    }
                });
            }
        }
        return issues;
    }
}
