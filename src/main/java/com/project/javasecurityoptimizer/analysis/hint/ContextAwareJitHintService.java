package com.project.javasecurityoptimizer.analysis.hint;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class ContextAwareJitHintService {
    private static final int DEFAULT_MAX_FILES = 100;
    private static final int DEFAULT_MAX_METHODS_PER_FILE = 40;

    public ContextHintResponse analyze(ContextHintRequest request) {
        if (request == null || request.projectPath() == null || request.projectPath().isBlank()) {
            throw new IllegalArgumentException("projectPath must not be empty");
        }
        Path projectPath = Path.of(request.projectPath());
        if (!Files.exists(projectPath) || !Files.isDirectory(projectPath)) {
            throw new IllegalArgumentException("projectPath does not exist or is not a directory");
        }

        int maxFiles = request.maxFiles() == null || request.maxFiles() <= 0 ? DEFAULT_MAX_FILES : request.maxFiles();
        int maxMethods = request.maxMethodsPerFile() == null || request.maxMethodsPerFile() <= 0
                ? DEFAULT_MAX_METHODS_PER_FILE : request.maxMethodsPerFile();

        List<Path> javaFiles = collectJavaFiles(projectPath, request.targetFiles(), maxFiles);
        List<FileContextSummary> fileSummaries = new ArrayList<>();
        List<JitHint> jitHints = new ArrayList<>();

        int classCount = 0;
        int methodCount = 0;
        int loopCount = 0;
        int branchCount = 0;

        for (Path file : javaFiles) {
            CompilationUnit cu;
            try {
                cu = StaticJavaParser.parse(file);
            } catch (Exception parseException) {
                jitHints.add(new JitHint(
                        "JIT.PARSE.FAILURE",
                        "FILE",
                        file.toString(),
                        null,
                        null,
                        "文件解析失败，无法进行深度上下文分析",
                        "先修复语法错误，再执行多级上下文分析",
                        "HIGH"
                ));
                continue;
            }

            List<ClassContextSummary> classSummaries = new ArrayList<>();
            for (ClassOrInterfaceDeclaration classDecl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                classSummaries.add(new ClassContextSummary(
                        classDecl.getNameAsString(),
                        classDecl.getMethods().size(),
                        classDecl.getFields().size(),
                        classDecl.getConstructors().size()
                ));
            }

            List<MethodDeclaration> methodDeclarations = new ArrayList<>(cu.findAll(MethodDeclaration.class));
            methodDeclarations.sort(Comparator.comparingInt(this::statementCount).reversed());
            if (methodDeclarations.size() > maxMethods) {
                methodDeclarations = methodDeclarations.subList(0, maxMethods);
            }

            List<MethodContextSummary> methodSummaries = new ArrayList<>();
            for (MethodDeclaration method : methodDeclarations) {
                int statements = statementCount(method);
                int loops = loopCount(method);
                int branches = branchCount(method);
                int calls = method.findAll(MethodCallExpr.class).size();

                methodSummaries.add(new MethodContextSummary(
                        method.getNameAsString(),
                        statements,
                        loops,
                        branches,
                        calls,
                        method.isSynchronized()
                ));
                collectJitHints(file, method, statements, loops, branches, calls, jitHints);
            }

            int fileLoops = loopCount(cu);
            int fileBranches = branchCount(cu);
            int fileMethods = cu.findAll(MethodDeclaration.class).size();
            int fileClasses = cu.findAll(ClassOrInterfaceDeclaration.class).size();

            fileSummaries.add(new FileContextSummary(
                    file.toString(),
                    fileClasses,
                    fileMethods,
                    fileLoops,
                    fileBranches,
                    classSummaries,
                    methodSummaries
            ));

            classCount += fileClasses;
            methodCount += fileMethods;
            loopCount += fileLoops;
            branchCount += fileBranches;
        }

        ProjectContextSummary projectSummary = new ProjectContextSummary(
                fileSummaries.size(),
                classCount,
                methodCount,
                loopCount,
                branchCount
        );
        return new ContextHintResponse(projectSummary, fileSummaries, jitHints);
    }

    private List<Path> collectJavaFiles(Path projectPath, List<String> targetFiles, int maxFiles) {
        List<Path> files = new ArrayList<>();
        if (targetFiles != null && !targetFiles.isEmpty()) {
            for (String item : targetFiles) {
                if (item == null || item.isBlank()) {
                    continue;
                }
                Path candidate = Path.of(item);
                Path absolute = candidate.isAbsolute() ? candidate : projectPath.resolve(candidate);
                if (Files.exists(absolute) && absolute.toString().endsWith(".java")) {
                    files.add(absolute.normalize());
                }
                if (files.size() >= maxFiles) {
                    break;
                }
            }
            return files;
        }

        try (var stream = Files.walk(projectPath)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .limit(maxFiles)
                    .toList()) {
                files.add(file);
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to scan java files: " + e.getMessage(), e);
        }
        return files;
    }

    private void collectJitHints(
            Path file,
            MethodDeclaration method,
            int statements,
            int loops,
            int branches,
            int calls,
            List<JitHint> jitHints
    ) {
        if (statements > 80) {
            jitHints.add(hint("JIT.INLINE.LARGE_METHOD", file, method,
                    "方法体过大，JIT 内联概率降低",
                    "将冷路径分支与工具逻辑下沉到私有方法，保留热路径精简",
                    "HIGH"));
        }
        if (branches > 20) {
            jitHints.add(hint("JIT.BRANCH.COMPLEX", file, method,
                    "分支密度高，可能造成分支预测失败",
                    "使用早返回、策略分发或查表方式降低 if/switch 链复杂度",
                    "MEDIUM"));
        }
        if (calls > 40) {
            jitHints.add(hint("JIT.CALL.HEAVY", file, method,
                    "调用点过多，内联预算可能被快速耗尽",
                    "合并重复调用、缓存稳定结果，并把热循环内调用下沉到循环外",
                    "MEDIUM"));
        }
        if (method.isSynchronized() && statements <= 3) {
            jitHints.add(hint("JIT.LOCK.TINY_METHOD", file, method,
                    "短方法使用 synchronized 可能造成不必要锁开销",
                    "考虑细化同步粒度或改为无锁结构/原子变量",
                    "LOW"));
        }

        long allocationsInLoop = method.findAll(ObjectCreationExpr.class).stream()
                .filter(this::isInsideLoop)
                .count();
        if (allocationsInLoop >= 3) {
            jitHints.add(hint("JIT.ALLOC.IN_LOOP", file, method,
                    "循环内存在频繁对象分配，可能增加 GC 压力",
                    "将可复用对象移到循环外，或使用对象池/预分配容器",
                    "HIGH"));
        }

        long catchInLoop = method.findAll(CatchClause.class).stream()
                .filter(this::isInsideLoop)
                .count();
        if (catchInLoop > 0) {
            jitHints.add(hint("JIT.EXCEPTION.IN_LOOP", file, method,
                    "循环内异常处理路径会影响热路径优化",
                    "将异常控制流移出热循环，避免把异常作为常规分支",
                    "MEDIUM"));
        }

        boolean hasReflection = method.findAll(MethodCallExpr.class).stream().anyMatch(call -> {
            String text = call.toString().toLowerCase(Locale.ROOT);
            return text.contains("class.forname")
                    || text.contains(".invoke(")
                    || text.contains("methodhandles.lookup");
        });
        if (hasReflection) {
            jitHints.add(hint("JIT.REFLECTION.HOT_PATH", file, method,
                    "检测到反射调用，JIT 可能难以内联优化",
                    "对稳定调用点改为直接调用、MethodHandle 预绑定或代码生成",
                    "MEDIUM"));
        }

        boolean hasBoxingInLoop = method.findAll(MethodCallExpr.class).stream().anyMatch(call -> {
            if (!isInsideLoop(call)) {
                return false;
            }
            String text = call.toString();
            return text.contains("Integer.valueOf")
                    || text.contains("Long.valueOf")
                    || text.contains("Double.valueOf")
                    || text.contains("intValue()")
                    || text.contains("longValue()");
        });
        if (hasBoxingInLoop) {
            jitHints.add(hint("JIT.BOXING.IN_LOOP", file, method,
                    "循环内存在装箱/拆箱调用，可能增加分配与类型转换开销",
                    "优先使用基础类型数组或 primitive 集合，减少装箱路径",
                    "MEDIUM"));
        }
    }

    private JitHint hint(
            String hintId,
            Path file,
            MethodDeclaration method,
            String message,
            String recommendation,
            String priority
    ) {
        String className = findClassName(method);
        return new JitHint(
                hintId,
                "METHOD",
                file.toString(),
                className,
                method.getNameAsString(),
                message,
                recommendation,
                priority
        );
    }

    private String findClassName(Node node) {
        Node current = node;
        while (current != null) {
            if (current instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
                return classOrInterfaceDeclaration.getNameAsString();
            }
            current = current.getParentNode().orElse(null);
        }
        return null;
    }

    private int statementCount(MethodDeclaration method) {
        return method.getBody().map(body -> body.getStatements().size()).orElse(0);
    }

    private int loopCount(Node node) {
        return node.findAll(ForStmt.class).size()
                + node.findAll(ForEachStmt.class).size()
                + node.findAll(WhileStmt.class).size()
                + node.findAll(DoStmt.class).size();
    }

    private int branchCount(Node node) {
        return node.findAll(IfStmt.class).size()
                + node.findAll(SwitchStmt.class).size()
                + node.findAll(CatchClause.class).size();
    }

    private boolean isInsideLoop(Node node) {
        Node current = node.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof ForStmt
                    || current instanceof ForEachStmt
                    || current instanceof WhileStmt
                    || current instanceof DoStmt) {
                return true;
            }
            current = current.getParentNode().orElse(null);
        }
        return false;
    }
}
