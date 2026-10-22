# 苍穹外卖 · 使用教程

基于 Spring Boot 的外卖后端学习项目，包含员工、分类、菜品、套餐、购物车、订单、报表，以及 Redis + RabbitMQ 实现的限量套餐抢购。

本教程按 Windows PowerShell 编写。仓库提供 Java 后端，**没有附带管理端网页、微信小程序和完整基础数据库脚本**；准备好对应资料后，可先通过接口文档调试后端。

## 1. 准备环境

| 环境 | 说明 |
| --- | --- |
| JDK 17 | 根 `pom.xml` 的 `java.version` 为 17，建议先用此版本 |
| Maven 3.x | 在项目根目录构建三个模块 |
| MySQL | 建议 MySQL 8，默认数据库 `sky_take_out`、端口 `3306` |
| Redis | 默认端口 `6379`，缓存、抢购库存和 Redisson 锁共用 |
| RabbitMQ | 默认 AMQP 端口 `5672`，用于异步创建抢购订单 |
| Git | 获取源码 |

```powershell
java -version
mvn -v
git --version
git clone https://github.com/Lai-Kai/sky-take-out.git
Set-Location .\sky-take-out
```

`mvn -v` 输出的 Java 版本也应是 17；IDEA 的项目 SDK、Maven Runner 和运行配置应使用同一 JDK。不要仅凭电脑安装了 JDK 就假定 Maven 正在使用它。

## 2. 初始化数据库

仓库中的 [flash_sale_schema.sql](sky-server/src/main/resources/db/flash_sale_schema.sql) 是**增量脚本**：创建两张抢购表，并给已有的 `orders`、`order_detail`、`shopping_cart`、`setmeal` 加索引。它不能单独初始化完整项目。

先准备与本项目实体和 Mapper 匹配的基础业务数据库脚本，包含员工、菜品、套餐、用户、地址和订单等表及需要的初始数据。基础脚本需要从你已有的项目资料取得，本仓库未提供。

可在数据库工具中依次导入；也可以在项目根目录打开 MySQL 客户端：

```powershell
mysql -u root -p
```

然后在 **MySQL 客户端内**执行，替换基础脚本路径：

```sql
CREATE DATABASE IF NOT EXISTS sky_take_out CHARACTER SET utf8mb4;
USE sky_take_out;
SOURCE D:/your-sql/base-schema.sql;
SOURCE sky-server/src/main/resources/db/flash_sale_schema.sql;
```

基础脚本若自行创建或切换数据库，请以实际库名为准，并同步修改配置。增量脚本末尾的 `ADD INDEX` 没有重复执行保护；已有这些索引时，不要整份重复导入。

管理员账号由你导入的 `employee` 数据决定。仓库没有初始管理员账号种子，不假定存在某个固定账号密码。员工登录会对输入密码做 MD5 后与数据库比较，账号还需要处于启用状态。

## 3. 配置本机环境

默认启用 `dev` 配置。配置文件位置：

- `sky-server/src/main/resources/application.yml`：端口、JWT、框架配置。
- `sky-server/src/main/resources/application-dev.yml`：MySQL、Redis、RabbitMQ 和第三方服务配置。
- `sky-server/src/main/resources/application-local.yml`：可选的本机覆盖配置，已被 Git 忽略。

`application-dev.yml` 使用 `optional:classpath:application-local.yml` 导入本机文件。已有此文件时保留原配置；没有时创建：

```powershell
$localConfig = '.\sky-server\src\main\resources\application-local.yml'
if (-not (Test-Path $localConfig)) { New-Item -ItemType File $localConfig | Out-Null }
notepad $localConfig
```

按实际环境填写，例如下面的基础配置；已有内容时合并同名节点，不要重复创建两个 `sky:`：

```yaml
server:
  port: 8081
sky:
  datasource:
    host: 127.0.0.1
    port: 3306
    database: sky_take_out
    username: YOUR_DATABASE_USER
    password: YOUR_DATABASE_PASSWORD
  redis:
    host: 127.0.0.1
    port: 6379
    password: "" # 无密码时留空；有密码时填写实际值
    database: 0
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest
    virtual-host: /
  jwt:
    admin-secret-key: CHANGE_ME_ADMIN_JWT_SECRET
    user-secret-key: CHANGE_ME_USER_JWT_SECRET
```

Redis 在共享 dev 配置中的默认密码是 `123456`，应与实际服务保持一致；上面示例覆盖为空。RabbitMQ 的 `guest` 账号用于本机连接，连接远程实例时使用该实例授权的账号和虚拟主机。程序会声明抢购交换机、队列和绑定，需要相应权限。

### 按需配置第三方功能

| 功能 | 需要的配置 |
| --- | --- |
| 七牛图片上传 | `sky.qiniu` 的 Access Key、Secret Key、空间名、访问域名 |
| 微信用户登录 | `sky.wechat.appid`、`sky.wechat.secret`，以及小程序 `wx.login` 获取的临时 code |
| 普通订单配送范围校验 | `sky.shop.address` 和有效的 `sky.baidu.ak` |
| 微信支付 | 对应商户号、证书、密钥和回调配置，参考 `WeChatProperties`、`WeChatPayUtil` |

仓库里的第三方示例值不能直接当作可用凭据。七牛 Key 可在启动终端设置：

```powershell
$env:QINIU_ACCESS_KEY = 'YOUR_QINIU_ACCESS_KEY'
$env:QINIU_SECRET_KEY = 'YOUR_QINIU_SECRET_KEY'
```

七牛空间、域名和访问模式请在本机 YAML 的 `sky.qiniu` 节点配置；dev 配置已写入这些字段，不能仅靠设置 `QINIU_BUCKET_NAME` 等变量替换它们。不要把真实密钥加入 README 或共享配置。

本机 YAML 位于源码资源目录，修改后需要重新构建或刷新 IDE 资源。构建会把它打入 JAR，**不要公开含有本机密钥的构建产物**；部署时可使用环境变量或外部配置保存密钥。

## 4. 构建和启动

先启动 MySQL、Redis、RabbitMQ，再从项目根目录执行：

```powershell
mvn clean package -DskipTests
java -jar .\sky-server\target\sky-server-1.0-SNAPSHOT.jar
```

也可以用 IDEA 导入根 `pom.xml`，等待 Maven 依赖加载完成，直接运行 `sky-server` 模块中的 `com.sky.SkyApplication`。

日志出现服务启动完成后访问：

| 地址 | 用途 |
| --- | --- |
| <http://localhost:8081/doc.html> | Knife4j 接口文档，包含管理员端和用户端分组 |
| `http://localhost:8081/admin/...` | 管理员接口 |
| `http://localhost:8081/user/...` | 用户接口 |

根路径 `/` 没有商家网页，访问它出现 404 不代表后端未启动。本仓库也没有单独的健康检查接口；可先检查接口文档，再测试登录。若修改端口，后续地址同步替换。停止服务按 `Ctrl+C`。

## 5. 登录并调试接口

### 管理员端

用基础数据库中已存在、已启用的员工账号登录：

```powershell
$baseUrl = 'http://localhost:8081'
$loginBody = @{ username = 'YOUR_ADMIN_USERNAME'; password = 'YOUR_ADMIN_PASSWORD' } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$baseUrl/admin/employee/login" -ContentType 'application/json; charset=utf-8' -Body $loginBody
if ($login.code -ne 1) { throw $login.msg }
$adminHeaders = @{ token = $login.data.token }
Invoke-RestMethod -Uri "$baseUrl/admin/shop/status" -Headers $adminHeaders
```

返回值采用 `code`、`msg`、`data` 结构，`code = 1` 表示业务成功；不能只凭 HTTP 200 判断操作成功。管理员后续请求使用 **`token` 请求头，值是原始 JWT**，不加 `Bearer ` 前缀。

可按“员工 → 分类 → 菜品及口味 → 套餐 → 营业状态 → 订单”的顺序熟悉管理接口；完整请求字段在 `/doc.html` 查看。比如设置营业状态：

```powershell
# 1 为营业，0 为打烊
Invoke-RestMethod -Method Put -Uri "$baseUrl/admin/shop/1" -Headers $adminHeaders
```

### 用户端

用户通过 `POST /user/user/login` 登录，JSON 为 `{"code":"wx.login 获取的有效临时 code"}`。后端会调用微信接口获取 openid，首次登录自动创建用户记录。此流程需要真实小程序配置，不支持用任意字符串模拟登录。

登录成功后读取 `data.token`，在用户接口传 **`authentication` 请求头**，同样使用原始 JWT。用户登录令牌和管理员令牌不能混用。

有对应小程序或 API 调试客户端后，可按“浏览分类/菜品/套餐 → 保存收货地址 → 加入购物车 → 提交订单 → 支付 → 查询订单”使用。普通下单还会调用百度地图检查配送范围，支付也依赖真实第三方配置。

## 6. 体验限量套餐抢购

先完成基础表和抢购增量脚本初始化，准备一个真实套餐 ID，以及用户和其有效收货地址。服务端当前 JSON 时间格式为 **`yyyy-MM-dd HH:mm`**，例如 `2026-10-22 09:30`，不带 `T` 或秒。

使用上节的 `$baseUrl`、`$adminHeaders` 创建活动。把 `setmealId` 替换为数据库中的套餐 ID；示例按当前时间生成有效窗口：

```powershell
$now = Get-Date
$activityBody = @{
  setmealId = 1
  title = 'Flash sale demo'
  price = 19.90
  stock = 10
  beginTime = $now.AddMinutes(-5).ToString('yyyy-MM-dd HH:mm')
  endTime = $now.AddHours(1).ToString('yyyy-MM-dd HH:mm')
  status = 1
} | ConvertTo-Json
$activity = Invoke-RestMethod -Method Post -Uri "$baseUrl/admin/flash-sale" -Headers $adminHeaders -ContentType 'application/json; charset=utf-8' -Body $activityBody
if ($activity.code -ne 1) { throw $activity.msg }
$activityId = $activity.data
```

创建成功返回的 `data` 是活动 ID，并自动预热 Redis 库存。需要手动预热时可调用 `POST /admin/flash-sale/{id}/warm-up`；它会重写库存并删除该活动的用户抢购标记，**仅在流量进入前操作，不要在正在抢购时反复调用**。

取得真实用户登录令牌后提交抢购，把地址 ID 替换为该用户的有效地址：

```powershell
$userHeaders = @{ authentication = 'YOUR_USER_JWT' }
$submitBody = @{ activityId = $activityId; addressBookId = 1 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$baseUrl/user/flash-sale/submit" -Headers $userHeaders -ContentType 'application/json; charset=utf-8' -Body $submitBody
```

成功响应的 `data` 包含 `activityId`、`userId` 和 `status: "flash sale request accepted"`。这表示请求已受理，订单由 RabbitMQ 消费者异步创建；需继续检查数据库的 `flash_sale_order`、`orders` 和 `order_detail`，或用户订单查询接口。`flash_sale_order.status = 1` 表示订单创建完成。

抢购使用 `sky.flash.sale.exchange` 交换机和 `sky.flash.sale.order.queue` 队列；Redis 中库存键为 `flash_sale:stock:{活动ID}`，用户标记为 `flash_sale:users:{活动ID}`。同一用户重复提交会被拒绝。详细原理见 [限量套餐抢购学习说明](docs/flash-sale-guide.md)。

## 7. 常见问题

| 现象 | 检查方向 |
| --- | --- |
| 编译报 JDK、Lombok 或注解处理错误 | 先确认 `mvn -v` 和 IDEA 使用 JDK 17，再检查依赖是否完整下载 |
| Maven 找不到 `com.sky` 模块依赖 | 从根目录执行整项目构建，避免只单独构建 `sky-server` |
| 数据库提示表不存在 | 先导入基础业务表，再执行抢购增量脚本；程序不会自动创建完整基础库 |
| `Duplicate key name` | 抢购增量脚本的索引可能已存在，检查后跳过重复索引语句 |
| Redis 连接失败、`NOAUTH`、`WRONGPASS` | 核对地址、端口和实际密码，确认本机覆盖配置已重新构建生效 |
| RabbitMQ 连接拒绝或认证失败 | 检查服务、5672 端口、账号、虚拟主机和声明队列的权限 |
| 管理员登录失败 | 检查导入的员工账号、密码 MD5、启用状态和请求字段 |
| 接口返回 401 | 检查登录令牌是否过期，管理员用 `token`，用户用 `authentication` |
| 微信登录失败 | 检查小程序 appid/secret 以及临时 code 的有效性 |
| 上传失败或图片不能访问 | 检查七牛 Key、空间、域名、区域和访问模式；上传工具当前使用华东 `Region.region0()` |
| 普通订单提示地址解析/路线规划失败 | 检查百度地图 AK、店铺地址和收货地址 |
| 抢购时间解析失败 | 使用 `yyyy-MM-dd HH:mm`，检查服务端和客户端时区与当前活动窗口 |
| 抢购受理但没有订单 | 检查 RabbitMQ 消费者日志、队列和基础表结构，受理响应不等于订单已落库 |

## 8. 目录与学习资料

| 位置 | 内容 |
| --- | --- |
| `sky-common/` | 常量、异常、JWT、云服务工具、公共配置 |
| `sky-pojo/` | Entity、DTO、VO |
| `sky-server/` | 启动入口、Controller、Service、Mapper、定时任务和消息消费者 |
| `sky-server/src/main/resources/mapper/` | MyBatis XML |
| `sky-server/src/main/resources/db/`、`lua/` | 抢购增量 SQL、Redis Lua 脚本 |
| [抢购模块说明](docs/flash-sale-guide.md) | Redis + Lua + RabbitMQ + Redisson 的实现链路 |
| [MyBatis-Plus 迁移日志](MIGRATION_LOG.md) | 持久层改造记录 |
| [并发学习指南](并发学习指南.md) | 并发知识和学习示例，具体实现以当前源码为准 |

建议阅读顺序：`SkyApplication → WebMvcConfiguration → 登录 Controller / JWT 拦截器 → 业务 Service → Mapper`，再跟踪 `FlashSaleActivityServiceImpl → Lua → FlashSaleOrderListener → FlashSaleOrderServiceImpl` 理解异步抢购。
