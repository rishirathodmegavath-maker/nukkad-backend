package com.nukkad.startup.repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs a JPA {@link Specification} against stand-in criteria objects that only write down what was asked of them, so a
 * test can read the rule a specification builds (for example {@code and(isTrue(isRaising), isTrue(fundraisingVisible))})
 * without a database. It shows the shape of the query; that the database then returns the right rows is checked
 * against a real MySQL in the browser/API suites.
 *
 * Every criteria call returns another recording object whose text is {@code method(arguments)}; {@code root.get("x")}
 * reads simply as {@code x}.
 */
public final class CriteriaRecorder {

    private final List<String> calls = new ArrayList<>();

    /** The predicate the specification produced, as text. */
    public String render(Specification<?> spec) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Predicate predicate = ((Specification) spec).toPredicate(node(Root.class, "root"), node(CriteriaQuery.class, "query"), node(CriteriaBuilder.class, "cb"));
        return String.valueOf(predicate);
    }

    /** Every criteria call made, in order, including the ones inside a subquery. */
    public List<String> calls() {
        return calls;
    }

    @SuppressWarnings("unchecked")
    private <T> T node(Class<T> type, String label) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "toString": return label;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    default: break;
                }
                Class<?> returns = method.getReturnType();
                if (returns == void.class) return null;
                String text = method.getName().equals("get") ? String.valueOf(args[0]) : method.getName() + "(" + render(args) + ")";
                calls.add(text);
                if (returns.isInterface()) return node(returns, text);
                if (returns == boolean.class) return false;
                return null;
            }
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static String render(Object[] args) {
        if (args == null) return "";
        StringBuilder out = new StringBuilder();
        for (Object arg : args) {
            if (out.length() > 0) out.append(", ");
            if (arg instanceof Object[] array) out.append(String.join(", ", Arrays.stream(array).map(CriteriaRecorder::one).toList()));
            else out.append(one(arg));
        }
        return out.toString();
    }

    private static String one(Object arg) {
        return arg instanceof Class<?> type ? type.getSimpleName() : String.valueOf(arg);
    }
}
