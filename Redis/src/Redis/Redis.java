package Redis;



import com.sun.deploy.util.StringUtils;

import static com.sun.corba.se.impl.util.RepositoryId.cache;

public class Redis {
    //缓存穿透伪代码
    //解决方案一：对空数据进行缓存
    public object GetProductListNew() {
        int cacheTime = 30;
        String cacheKey = "product_list";

        String cacheValue = CacheHelper.Get(cacheKey);
        if (cacheValue != null) {
            return cacheValue;
        }

        cacheValue = CacheHelper.Get(cacheKey);
        if (cacheValue != null) {
            return cacheValue;
        } else {
            //数据库查询不到，为空
            cacheValue = GetProductListFromDB();
            if (cacheValue == null) {
                //如果发现为空，设置个默认值，也缓存起来
                cacheValue = string.Empty;
            }
            //product_list,空值,过期时间
            CacheHelper.Add(cacheKey, cacheValue, cacheTime);
            return cacheValue;
        }
    }
    ///解决方案二：布隆过滤器
    String get(String key) {
        String value = redis.get(key);
        if (value  == null) {
            if(!bloomfilter.mightContain(key)){
                //不存在则返回
                return null;
            }else{
                //可能存在则查数据库
                value = db.get(key);
                redis.set(key, value);
            }
        }
        return value;
    }
    //缓存击穿
    public String get(key){
        //从缓存中获取数据
        String value = redis.get(key);
        if(value == null){  //缓存中数据不存在
            if(reenLock.tryLock()){ //获取锁
                //从数据库获取数据
                value=db.get(key);
                //更新缓存数据
                if(value!=null){
                    redis.set(key, value, expire_secs);
                }
                //释放锁
                reenLock.unlock();
            }else{  //获取锁失败
                //暂停100ms再重新取获取数据
                Thread.sleep(100);
                value = redis.get(key);
            }
        }
    }
    public String get(key){
        //从缓存中获取数据
        String value = redis.get(key);
        if(value == null){  //缓存中数据不存在
            if(reenLock.tryLock()){ //获取锁
                //从数据库获取数据
                value=db.get(key);
                //更新缓存数据
                if(value!=null){
                    redis.set(key, value, expire_secs);
                }
                //释放锁
                reenLock.unlock();
            }else{  //获取锁失败
                //暂停100ms再重新取获取数据
                Thread.sleep(100);
                value = redis.get(key);
            }
        }
    }
    public String get(Sting key){
        V v = redis.get(key);
        String value = v.getValue();
        long timeout = v.getTimeout();
        if (v.timeout <= System.currentTimeMillis()){
            // 异步更新后台异常执行
            threadPool.execute(new Runnable(){
                public void run(){
                    String keyMutex = "mutex:" + key;
                    if(redis.setnx(keyMutex, "1")){
                        //3 min timeout to avoid mutex holder crash
                        redis.expire(keyMutex, 3 * 60);
                        String dbValue = db.get(key);
                        redis.set(key, dbValue);
                        redis.delete(keyMutex);
                    }
                }
            });
        }
        return value;
    }
    //缓存雪崩
    //伪代码
    public object GetProductListNew() {
        int cacheTime = 30;
        String cacheKey = "product_list";
        String lockKey = cacheKey;

        String cacheValue = CacheHelper.get(cacheKey);
        if (cacheValue != null) {
            return cacheValue;
        } else {
            synchronized(lockKey) {
                cacheValue = CacheHelper.get(cacheKey);
                if (cacheValue != null) {
                    return cacheValue;
                } else {
                    //这里一般是sql查询数据
                    cacheValue = GetProductListFromDB();
                    CacheHelper.Add(cacheKey, cacheValue, cacheTime);
                }
            }
            return cacheValue;
        }
    }
    //伪代码
    public object GetProductListNew() {
        int cacheTime = 30;
        String cacheKey = "product_list";
        //缓存标记
        String cacheSign = cacheKey + "_sign";

        String sign = CacheHelper.Get(cacheSign);
        //获取缓存值
        String cacheValue = CacheHelper.Get(cacheKey);
        if (sign != null) {
            return cacheValue; //未过期，直接返回
        } else {
            CacheHelper.Add(cacheSign, "1", cacheTime);
            ThreadPool.QueueUserWorkItem((arg) -> {
                //这里一般是 sql查询数据
                cacheValue = GetProductListFromDB();
                //日期设缓存时间的2倍，用于脏读
                CacheHelper.Add(cacheKey, cacheValue, cacheTime * 2);
            });
            return cacheValue;
        }
    }
}
