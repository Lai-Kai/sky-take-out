# MyBatis-Plus 完整迁移日志

## 一、Bug修复（4个）

### 1. confirm() 订单ID用错
- **文件**: `OrderServiceImpl.java:314`
- **问题**: `BaseContext.getCurrentId()` 当订单ID用，更新了错误记录
- **修复**: 改为 `ordersConfirmDTO.getId()`

### 2. ThreadLocal内存泄漏
- **文件**: `JwtTokenAdminInterceptor.java` / `JwtTokenUserInterceptor.java`
- **问题**: `preHandle()` 设置了ThreadLocal值，没有在 `afterCompletion()` 清理
- **修复**: 两个拦截器都加了 `afterCompletion()` 方法

### 3. RedisConfiguration缺@Bean
- **文件**: `RedisConfiguration.java:13`
- **问题**: `redisTemplate()` 方法没有 `@Bean` 注解
- **修复**: 加上 `@Bean` 注解

### 4. WebSocket sessionMap线程不安全
- **文件**: `WebSocketServer.java:22`
- **问题**: `HashMap` 存储WebSocket session
- **修复**: `HashMap` → `ConcurrentHashMap`

---

## 二、MyBatis-Plus 迁移（第一阶段：基础改造）

### 1. POM依赖
- `mybatis-spring-boot-starter` → `mybatis-plus-boot-starter` 3.5.3.1
- 移除 `pagehelper-spring-boot-starter`

### 2. 配置文件
- `application.yml`：`mybatis` → `mybatis-plus` 配置段
- 新增 `MybatisPlusConfig.java`（分页插件）
- 新增 `MyMetaObjectHandler.java`（自动填充，替代 @AutoFill + AOP）

### 3. 实体类注解（11个）
全部加上 `@TableName`、`@TableId(AUTO)`，审计字段加 `@TableField(fill=...)`

### 4. Mapper 继承 BaseMapper（11个）
所有 Mapper 改为 `extends BaseMapper<T>`

### 5. 分页改造（5个 Service）
PageHelper → MyBatis-Plus `Page<T>` + `IPage<T>`

### 6. 自动填充替换
删除 `@AutoFill` 注解 + `AutoFillAspect`，改用 `MyMetaObjectHandler`

---

## 三、MyBatis-Plus 迁移（第二阶段：全面 Wrapper 替换）

### 1. Service 接口继承 IService（8个）

| 接口 | 继承 |
|------|------|
| AddressBookService | `IService<AddressBook>` |
| CategoryService | `IService<Category>` |
| DishService | `IService<Dish>` |
| EmployeeService | `IService<Employee>` |
| OrderService | `IService<Orders>` |
| SetMealService | `IService<Setmeal>` |
| ShoppingCartService | `IService<ShoppingCart>` |
| UserService | `IService<User>` |

### 2. ServiceImpl 继承 ServiceImpl（8个）

| 实现类 | 继承 |
|--------|------|
| AddressBookServiceImpl | `ServiceImpl<AddressBookMapper, AddressBook>` |
| CategoryServiceImpl | `ServiceImpl<CategoryMapper, Category>` |
| DishServiceImpl | `ServiceImpl<DishMapper, Dish>` |
| EmployeeServiceImpl | `ServiceImpl<EmployeeMapper, Employee>` |
| OrderServiceImpl | `ServiceImpl<OrderMapper, Orders>` |
| SetMealServiceImpl | `ServiceImpl<SetmealMapper, Setmeal>` |
| ShoppingCartServiceImpl | `ServiceImpl<ShoppingCartMapper, ShoppingCart>` |
| UserServiceImpl | `ServiceImpl<UserMapper, User>` |

### 3. 新增 Service（3对）

| Service 接口 | ServiceImpl | 用途 |
|-------------|-------------|------|
| DishFlavorService | DishFlavorServiceImpl | 替代 DishFlavorMapper 直接调用 |
| SetMealDishService | SetMealDishServiceImpl | 替代 SetMealDishMapper 直接调用 |
| OrderDetailService | OrderDetailServiceImpl | 替代 OrderDetailMapper 直接调用 |

### 4. Mapper 接口清理（11个）

| Mapper | 清理后自定义方法 | 说明 |
|--------|----------------|------|
| AddressBookMapper | 无 | 纯 BaseMapper |
| DishFlavorMapper | 无 | 纯 BaseMapper |
| SetMealDishMapper | 无 | 纯 BaseMapper |
| OrderDetailMapper | 无 | 纯 BaseMapper |
| ShoppingCartMapper | 无 | 纯 BaseMapper |
| UserMapper | `getByOpenid`（@Select） | 无法替代 |
| EmployeeMapper | `getByUsername`（@Select）+ `pageQuery`（XML） | @Select + 分页 |
| DishMapper | `pageQuery`（LEFT JOIN category） | 多表 JOIN |
| CategoryMapper | `pageQuery`（动态条件） | 分页查询 |
| SetmealMapper | `pagequery`（JOIN）+ `getBySetmealId`（JOIN）+ `getDishItemBySetmealId`（@Select JOIN） | 多表 JOIN |
| OrderMapper | `update` + `pageQuery` + `conditionSearch`（XML）+ `getByNumber` + `countByStatus` + `getByStatusAndOrderTime`（@Select）+ `getTop10`（多表 JOIN） | 动态条件 + 多表 |

### 5. XML 文件删除（7个）

| 删除的文件 | 原因 |
|-----------|------|
| AddressBookMapper.xml | 所有 SQL 已用 Wrapper 替代 |
| EmployeeMapper.xml | 所有 SQL 已用 Wrapper 替代 |
| DishFlavorMapper.xml | 所有 SQL 已用 Wrapper 替代 |
| SetMealDishMapper.xml | 所有 SQL 已用 Wrapper 替代 |
| OrderDetailMapper.xml | 所有 SQL 已用 Wrapper 替代 |
| ShoppingCartMapper.xml | 所有 SQL 已用 Wrapper 替代 |
| UserMapper.xml | countByMap 已用 Wrapper 替代 |

### 6. XML 文件精简（4个保留）

| XML 文件 | 保留的 SQL | 不能替换的原因 |
|---------|-----------|--------------|
| CategoryMapper.xml | `pageQuery` | 动态条件分页 |
| DishMapper.xml | `pageQuery` | LEFT JOIN category 表 |
| SetmealMapper.xml | `pagequery` + `getBySetmealId` | 多表 JOIN |
| OrderMapper.xml | `update` + `pageQuery` + `conditionSearch` + `getTop10` | 动态条件 + 多表 JOIN + GROUP BY |

### 7. ServiceImpl 改动详情

#### AddressBookServiceImpl
| 旧调用 | 新调用 |
|--------|--------|
| `addressBookMapper.insert()` | `super.save()` |
| `addressBookMapper.getById()` | `super.getById()` |
| `addressBookMapper.update()` | `updateById()` |
| `addressBookMapper.deleteById()` | `removeById()` |
| `addressBookMapper.list()` | `lambdaQuery().eq().list()` |
| `addressBookMapper.updateIsDefaultByUserId()` | `lambdaUpdate().set().eq().update()` |

#### CategoryServiceImpl
| 旧调用 | 新调用 |
|--------|--------|
| `categoryMapper.insert()` | `super.save()` |
| `categoryMapper.deleteById()` | `removeById()` |
| `categoryMapper.list()` | `lambdaQuery().eq().list()` |
| `categoryMapper.update()` | `updateById()` |
| `categoryMapper.pageQuery()` | `baseMapper.pageQuery()`（保留） |
| `dishMapper.countByCategoryId()` | `dishService.count(lambdaQuery)` |
| `setmealMapper.countByCategoryId()` | `setmealService.count(lambdaQuery)` |

#### DishServiceImpl
| 旧调用 | 新调用 |
|--------|--------|
| `dishMapper.insert()` | `super.save()` |
| `dishMapper.getById()` | `super.getById()` |
| `dishMapper.update()` | `updateById()` |
| `dishMapper.deleteBatch()` | `removeByIds()` |
| `dishMapper.list()` | `lambdaQuery().eq().list()` |
| `dishMapper.pageQuery()` | `baseMapper.pageQuery()`（保留） |
| `dishFlavorMapper.insertBatch()` | `dishFlavorService.saveBatch()` |
| `dishFlavorMapper.deleteByDishIds()` | `dishFlavorService.remove(lambdaQuery)` |
| `dishFlavorMapper.deleteByDishId()` | `dishFlavorService.remove(lambdaQuery)` |
| `dishFlavorMapper.getByDishId()` | `dishFlavorService.list(lambdaQuery)` |
| `setMealDishMapper.getSetMealIdsByDishIds()` | `setMealDishService.list(lambdaQuery).stream().map()` |

#### EmployeeServiceImpl
| 旧调用 | 新调用 |
|--------|--------|
| `employeeMapper.insert()` | `super.save()` |
| `employeeMapper.getById()` | `super.getById()` |
| `employeeMapper.update()` | `updateById()` |
| `employeeMapper.getByUsername()` | `baseMapper.getByUsername()`（保留） |
| `employeeMapper.pageQuery()` | `baseMapper.pageQuery()`（保留） |

#### SetMealServiceImpl
| 旧调用 | 新调用 |
|--------|--------|
| `setmealMapper.insert()` | `super.save()` |
| `setmealMapper.getById()` | `super.getById()` |
| `setmealMapper.deleteById()` | `removeById()` |
| `setmealMapper.update()` | `updateById()` |
| `setmealMapper.list()` | `lambdaQuery().eq().list()` |
| `setmealMapper.pagequery()` | `baseMapper.pagequery()`（保留） |
| `setmealMapper.getBySetmealId()` | `baseMapper.getBySetmealId()`（保留） |
| `setmealMapper.getDishItemBySetmealId()` | `baseMapper.getDishItemBySetmealId()`（保留） |
| `setMealDishMapper.insertBatch()` | `setMealDishService.saveBatch()` |
| `setMealDishMapper.deleteBySetmealId()` | `setMealDishService.remove(lambdaQuery)` |
| `setMealDishMapper.getBySetmealId()` | `setMealDishService.list(lambdaQuery)` |

#### OrderServiceImpl
| 旧调用 | 新调用 |
|--------|--------|
| `orderMapper.insert()` | `super.save()` |
| `orderMapper.getById()` | `super.getById()` |
| `orderMapper.update()` | `updateById()` |
| `orderMapper.pageQuery()` | `baseMapper.pageQuery()`（保留） |
| `orderMapper.conditionSearch()` | `baseMapper.conditionSearch()`（保留） |
| `orderMapper.countByStatus()` | `baseMapper.countByStatus()`（保留） |
| `orderMapper.getByNumber()` | `baseMapper.getByNumber()`（保留） |
| `addressBookMapper.getById()` | `addressBookService.getById()` |
| `orderDetailMapper.insertBatch()` | `orderDetailService.saveBatch()` |
| `orderDetailMapper.listByOrderId()` | `orderDetailService.list(lambdaQuery)` |
| `shoppingCartMapper.list()` | `shoppingCartService.list(lambdaQuery)` |
| `shoppingCartMapper.clearShoppingCart()` | `shoppingCartService.remove(lambdaQuery)` |
| `shoppingCartMapper.insertBatch()` | `shoppingCartService.saveBatch()` |
| `userMapper.getById()` | `userService.getById()` |

#### ShoppingCartServiceImpl
| 旧调用 | 新调用 |
|--------|--------|
| `shoppingCartMapper.insert()` | `super.save()` |
| `shoppingCartMapper.update()` | `updateById()` |
| `shoppingCartMapper.list()` | `lambdaQuery().eq().list()` |
| `shoppingCartMapper.clearShoppingCart()` | `remove(lambdaQuery)` |
| `dishMapper.getById()` | `dishService.getById()` |
| `setmealMapper.getById()` | `setmealService.getById()` |

#### UserServiceImpl
| 旧调用 | 新调用 |
|--------|--------|
| `userMapper.insert()` | `super.save()` |
| `userMapper.getByOpenid()` | `baseMapper.getByOpenid()`（保留） |

#### WorkspaceServiceImpl
| 旧调用 | 新调用 |
|--------|--------|
| `orderMapper.countByMap(map)` | `orderService.count(lambdaQuery)` |
| `orderMapper.sumByMap(map)` | `orderService.list(lambdaQuery).stream().sum()` |
| `userMapper.countByMap(map)` | `userService.count(lambdaQuery)` |
| `dishMapper.countByMap(map)` | `dishService.count(lambdaQuery)` |
| `setmealMapper.countByMap(map)` | `setmealService.count(lambdaQuery)` |

#### ReportServiceImpl
| 旧调用 | 新调用 |
|--------|--------|
| `orderMapper.countByMap(map)` | `orderService.count(lambdaQuery)` |
| `orderMapper.sumByMap(map)` | `orderService.list(lambdaQuery).stream().sum()` |
| `userMapper.countByMap(map)` | `userService.count(lambdaQuery)` |
| `orderMapper.getTop10()` | 保留（多表 JOIN） |

### 8. 其他修复
- `SetMealServiceImpl`：`@Controller` → `@Service`（注解错误）
- `ShoppingCartController`：删除无用 `import org.apache.ibatis.annotations.Delete`

---

## 四、文件变更统计

| 类型 | 数量 |
|------|------|
| 修改的 Service 接口 | 8 |
| 修改的 ServiceImpl | 10（含 WorkspaceServiceImpl、ReportServiceImpl） |
| 修改的 Mapper 接口 | 11 |
| 修改的 XML 文件 | 4（精简） |
| 删除的 XML 文件 | 7 |
| 新增的 Service 接口 | 3（DishFlavorService、SetMealDishService、OrderDetailService） |
| 新增的 ServiceImpl | 3 |
| 其他修改 | 2（SetMealServiceImpl 注解、ShoppingCartController import） |
| **总计** | **48** |

---

## 五、保留 XML 的原因

以下查询涉及**多表 JOIN** 或**复杂动态 SQL**，MyBatis-Plus Wrapper 无法替代：

1. **DishMapper.pageQuery** — `LEFT JOIN category` 获取分类名称
2. **SetmealMapper.pagequery** — `LEFT JOIN category` 获取分类名称
3. **SetmealMapper.getBySetmealId** — `LEFT JOIN dish + setmeal_dish` 获取套餐菜品
4. **SetmealMapper.getDishItemBySetmealId** — `@Select LEFT JOIN` 获取菜品详情
5. **OrderMapper.pageQuery** — 多条件动态分页查询
6. **OrderMapper.conditionSearch** — 多条件动态查询
7. **OrderMapper.getTop10** — 多表 `JOIN + GROUP BY + ORDER BY + LIMIT`
8. **OrderMapper.update** — 动态 `SET` 更新（OrderTask 定时任务使用）
9. **EmployeeMapper.pageQuery** — 动态条件分页查询
10. **CategoryMapper.pageQuery** — 动态条件分页查询
