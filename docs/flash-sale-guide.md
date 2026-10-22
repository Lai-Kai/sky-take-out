# 限量套餐抢购模块学习说明

这份说明是给刚接触高并发、Redis、MQ、锁、索引优化的同学看的。你可以先看这份文档，再回到代码里看注释。

## 这个模块解决什么问题

普通下单是用户提交请求后直接写 MySQL。抢购活动不适合这样做，因为很多人会同时点同一个套餐，数据库容易出现两个问题：

- 库存超卖：库存只剩 1 份，却同时创建了多份订单。
- 数据库压力过大：所有请求都直接打到 MySQL，热点库存行会被大量竞争。

所以这里用了一个经典链路：

```text
用户请求 -> Redis + Lua 抢资格 -> RabbitMQ 排队 -> 消费者异步创建订单 -> MySQL 唯一索引兜底
```

## 一次抢购请求怎么走

1. 管理端创建活动

接口：`POST /admin/flash-sale`

示例：

```json
{
  "setmealId": 1,
  "title": "MK limited setmeal",
  "price": 19.90,
  "stock": 100,
  "beginTime": "2026-05-30 10:00",
  "endTime": "2026-05-30 12:00",
  "status": 1
}
```

时间格式必须为 `yyyy-MM-dd HH:mm`，不带 `T` 或秒。示例日期仅用于说明，实际测试时请改成覆盖当前时间的活动区间；可参考 [项目使用教程](../README.md) 中动态生成时间的请求示例。

代码位置：

- `sky-server/src/main/java/com/sky/controller/admin/FlashSaleController.java`
- `sky-server/src/main/java/com/sky/service/impl/FlashSaleActivityServiceImpl.java`

创建活动后，代码会把库存预热到 Redis：

```text
flash_sale:stock:{activityId} = 100
```

2. 用户提交抢购

接口：`POST /user/flash-sale/submit`

示例：

```json
{
  "activityId": 1,
  "addressBookId": 1
}
```

代码先做三件事：

- 用 Redisson 锁挡住同一个用户的重复点击。
- 用 Lua 脚本在 Redis 里原子判断库存和一人一单。
- 抢购成功后把消息发到 RabbitMQ，不直接写订单。

核心代码位置：

- `sky-server/src/main/java/com/sky/service/impl/FlashSaleActivityServiceImpl.java`
- `sky-server/src/main/resources/lua/flash_sale.lua`

3. RabbitMQ 异步创建订单

RabbitMQ 像一个排队区。用户请求成功后，只是进入队列，真正创建订单由消费者慢慢处理。

代码位置：

- `sky-server/src/main/java/com/sky/mq/FlashSaleOrderListener.java`
- `sky-server/src/main/java/com/sky/service/impl/FlashSaleOrderServiceImpl.java`

消费者创建订单时又做了几层兜底：

- Redisson 锁：防止同一条消息重复消费时并发写库。
- 唯一索引：`uk_flash_sale_user(activity_id, user_id)` 保证一人一单。
- MySQL 条件扣减：`where id = ? and stock > 0`，防止数据库层面库存扣成负数。
- 事务：抢购订单、普通订单、订单明细要么都成功，要么都回滚。

## 每个技术点怎么理解

### Redis + Lua：为什么适合高并发

Redis 很快，适合做抢购入口的第一层过滤。Lua 脚本的好处是把多个 Redis 命令合成一个原子操作：

```text
判断用户是否买过 -> 判断库存是否大于 0 -> 扣库存 -> 记录用户已买
```

如果不用 Lua，这几步中间可能被别的请求插队，容易出并发问题。

### RabbitMQ：为什么叫削峰

如果 10000 个请求同时成功，直接写 MySQL 会很猛。MQ 的做法是让请求先排队，消费者按固定速度处理。

这就是削峰：

```text
瞬间高峰流量 -> MQ 队列 -> 平滑写入数据库
```

### Redisson 锁：锁住什么

这里锁的不是整个活动，而是：

```text
activityId + userId
```

也就是说，只限制同一个用户对同一个活动的重复请求，不会把所有用户都堵住。

### 唯一索引：为什么还需要数据库兜底

Redis 和锁是应用层保护。数据库唯一索引是最后一道防线。

`flash_sale_order` 表里有：

```sql
UNIQUE KEY uk_flash_sale_user (activity_id, user_id)
```

这表示同一个用户对同一个活动只能有一条抢购记录。即使消息重复消费，数据库也不会允许重复插入。

### 普通索引：为什么能优化查询

索引像目录。没有索引时，数据库可能一行一行扫；有合适索引时，可以更快定位。

本模块新增了这些典型索引：

- `idx_orders_user_status_time`：用户查历史订单、按状态和时间排序。
- `idx_orders_status_time`：后台按状态处理超时订单。
- `idx_setmeal_category_status`：用户按分类查询可售套餐。
- `idx_cart_user_setmeal` / `idx_cart_user_dish`：购物车按用户和商品查询。

## 启动前要准备什么

1. 启动 MySQL、Redis、RabbitMQ。
2. 执行 SQL：

```text
sky-server/src/main/resources/db/flash_sale_schema.sql
```

3. 确认配置：

```yaml
sky:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: /
```

## 面试或简历可以怎么说

可以这样讲：

> 设计限量套餐抢购模块，基于 Redis + Lua 实现库存校验、原子扣减和一人一单判断；引入 RabbitMQ 将订单创建异步化，削峰填谷，降低 MySQL 瞬时写入压力；使用 Redisson 对用户维度重复请求加锁，并通过 MySQL 条件扣减和唯一索引兜底，避免库存超卖和重复下单；针对订单、购物车、套餐等高频查询场景补充联合索引，提升查询效率。

## 调试时看哪里

- 抢购入口：`FlashSaleActivityServiceImpl.submit`
- Lua 原子扣减：`flash_sale.lua`
- MQ 配置：`RabbitMQConfiguration`
- MQ 消费者：`FlashSaleOrderListener`
- 异步落库：`FlashSaleOrderServiceImpl.createOrder`
- 数据库表和索引：`flash_sale_schema.sql`
