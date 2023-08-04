package com.mall4j.cloud.common.database.interceptor;

import com.mall4j.cloud.api.leaf.feign.SegmentFeignClient;
import com.mall4j.cloud.common.database.annotations.DistributedId;
import com.mall4j.cloud.common.exception.Mall4cloudException;
import com.mall4j.cloud.common.model.BaseModel;
import com.mall4j.cloud.common.response.ResponseEnum;
import com.mall4j.cloud.common.response.ServerResponseEntity;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * 分布式id生成
 * 1. 分布式id是通过美团的leaf生成的，是需要与mall4cloud-leaf数据库 当中 leaf_alloc表中 biz_tag字段相关联的key
 * 2. 为了注入分布式id更加方便，规定为DistributedId为注解的字段加入该字段
 * @see DistributedId
 * @author FrozenWatermelon
 * @date 2020/9/9
 */
@Component
@Intercepts({@Signature(type = Executor.class, method = "update", args = {MappedStatement.class,Object.class})})
public class GeneratedKeyInterceptor implements Interceptor {

    private static final Logger logger = LoggerFactory.getLogger(GeneratedKeyInterceptor.class);
    /**
     * 单个插入名称
     */
    private static final String INSERT = "insert";

    /**
     * 单个插入名称
     */
    private static final String SAVE = "save";

    /**
     * 批量插入名称
     */
    private static final String BATCH_INSERT = "insertBatch";

    /**
     * 批量插入名称
     */
    private static final String BATCH_SAVE = "saveBatch";

    @Autowired
    private SegmentFeignClient segmentFeignClient;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement mappedStatement = (MappedStatement)invocation.getArgs()[0];

        // 获取 SQL
        SqlCommandType sqlCommandType = mappedStatement.getSqlCommandType();

        // 不是 insert 类型的跳过
        if (SqlCommandType.INSERT != sqlCommandType) {
            return invocation.proceed();
        }

        int one = 1;

        // 获取参数
        Object parameter = invocation.getArgs()[one];

        // 找数据库中的对象
        Object dbObject = findDbObject(parameter);

        if (dbObject == null) {
            return invocation.proceed();
        }

        // MappedStatement 的 ID 是由命名空间和语句的 ID 用点号连接而成的。
        // 通常，命名空间是 Mapper 接口的类路径，语句的 ID 是 Mapper 接口中定义的方法名。
        // 插入
        if (mappedStatement.getId().contains(INSERT) || mappedStatement.getId().contains(SAVE)){
            generatedKey(dbObject);
        }
        // 批量插入
        else if (mappedStatement.getId().contains(BATCH_INSERT) || mappedStatement.getId().contains(BATCH_SAVE)){
            // 获取批量查询的参数并生成主键，批量查询时，Executor 类的插入参数对象为 Map
            if (parameter instanceof HashMap){
                Object list = ((Map)parameter).get("list");
                if (list instanceof ArrayList) {
                    for (Object o : (ArrayList) list) {
                        generatedKey(dbObject);
                    }
                }
            }
        }

        return invocation.proceed();
    }

    protected BaseModel findDbObject(Object parameterObj) {
        if (parameterObj instanceof BaseModel) {
            return  (BaseModel)parameterObj;
        } else if (parameterObj instanceof Map) {
            for (Object val : ((Map<?, ?>) parameterObj).values()) {
                if (val instanceof BaseModel) {
                    return  (BaseModel)val;
                }
            }
        }
        return null;
    }

    /**
     * 获取私有成员变量 ,并设置主键
     * @param parameter 参数
     */
    private void generatedKey(Object parameter) throws Throwable {

        Field[] fieldList = parameter.getClass().getDeclaredFields();

        for (Field field : fieldList) {
            // 第一个字段不是 Long 类型（ID），退出方法
            if (!field.getType().isAssignableFrom(Long.class)) {
                break;
            }
            // 得到有 @DistributedId 注解的字段
            DistributedId annotation = field.getAnnotation(DistributedId.class);
            if (annotation == null) {
                break;
            }
            // 通过调用setAccessible(true)，我们可以打破这种限制，使得私有成员可以被访问。
            field.setAccessible(true);
            if (field.get(parameter) != null) {
                break;
            }
            // 通过 annotation.value() 即 biz_tag 获取分布式ID
            ServerResponseEntity<Long> segmentIdResponseEntity = segmentFeignClient.getSegmentId(annotation.value());
            if (segmentIdResponseEntity.isSuccess()) {
                // 这里设置分布式id
                field.set(parameter,segmentIdResponseEntity.getData());
            } else {
                logger.error("can't get distributed id !!!! ");
                throw new Mall4cloudException(ResponseEnum.EXCEPTION);
            }
        }
    }

    /**
     * Plugin.wrap生成拦截代理对象
     */
    @Override
    public Object plugin(Object o) {
        if (o instanceof Executor) {
            // 代理对象将会拦截目标对象方法的调用。通过该方法生成一个动态代理对象，并将拦截器与目标对象关联起来。
            return Plugin.wrap(o, this);
        } else {
            return o;
        }
    }

}

