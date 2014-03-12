package com.datastax.driver.mapping;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import com.datastax.driver.core.*;

// TODO: we probably should make that an abstract class and move some bit in a "ReflexionMethodMapper"
// subclass for consistency with the rest, but we can see to that later
class MethodMapper {

    public final Method method;
    public final String queryString;
    public final String[] paramNames;

    private final ConsistencyLevel consistency;
    private final int fetchSize;
    private final boolean tracing;

    private Session session;
    private PreparedStatement statement;

    private Mapper<?> returnMapper;
    private boolean mapOne;
    private boolean async;

    MethodMapper(Method method, String queryString, String[] paramNames, ConsistencyLevel consistency, int fetchSize, boolean enableTracing) {
        this.method = method;
        this.queryString = queryString;
        this.paramNames = paramNames;
        this.consistency = consistency;
        this.fetchSize = fetchSize;
        this.tracing = enableTracing;
    }

    public void prepare(MappingManager manager, PreparedStatement ps) {
        this.session = manager.getSession();
        this.statement = ps;

        Class<?> returnType = method.getReturnType();
        if (Void.class.isAssignableFrom(returnType) || ResultSet.class.isAssignableFrom(returnType))
            return;

        if (ResultSetFuture.class.isAssignableFrom(returnType)) {
            this.async = true;
            return;
        }

        if (ListenableFuture.class.isAssignableFrom(returnType)) {
            this.async = true;
            Type k = ((ParameterizedType)method.getGenericReturnType()).getActualTypeArguments()[0];
            if (k instanceof Class && ResultSet.class.isAssignableFrom((Class<?>)k))
                return;

            mapType(manager, returnType, k);
        } else {
            mapType(manager, returnType, method.getGenericReturnType());
        }
    }

    private void mapType(MappingManager manager, Class<?> fullReturnType, Type type) {

        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType)type;
            Type raw = pt.getRawType();
            if (raw instanceof Class && Result.class.isAssignableFrom((Class)raw)) {
                type = pt.getActualTypeArguments()[0];
            } else {
                mapOne = true;
            }
        } else {
            mapOne = true;
        }

        if (!(type instanceof Class))
            throw new RuntimeException(String.format("Cannot map return of method %s to unsupported type %s", method, type));

        try {
            this.returnMapper = (Mapper<?>)manager.mapper((Class<?>)type);
        } catch (Exception e) {
            throw new RuntimeException("Cannot map return to class " + fullReturnType, e);
        }
    }

    public Object invoke(Object[] args) {

        BoundStatement bs = statement.bind();

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg == null)
                continue;

            bs.setBytesUnsafe(paramNames[i], DataType.serializeValue(args[i]));
        }

        if (consistency != null)
            bs.setConsistencyLevel(consistency);
        if (fetchSize > 0)
            bs.setFetchSize(fetchSize);
        if (tracing)
            bs.enableTracing();

        if (async) {
            ListenableFuture<ResultSet> future = session.executeAsync(bs);
            if (returnMapper == null)
                return future;

            return mapOne
                 ? Futures.transform(future, returnMapper.mapOneFunction)
                 : Futures.transform(future, returnMapper.mapAllFunction);
        } else {
            ResultSet rs = session.execute(bs);
            if (returnMapper == null)
                return rs;

            Result<?> result = returnMapper.map(rs);
            return mapOne ? result.one() : result;
        }
    }
}
