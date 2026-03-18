package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    List<ShoppingCart> list(ShoppingCart shoppingCart);

    @Update("update shopping_cart set number = #{number} where id = #{id}")
    void update(ShoppingCart cart);

    @Insert("insert into shopping_cart (user_id, dish_id, setmeal_id, name, image, dish_flavor, number, amount,create_time) values (#{userId}, #{dishId}, #{setmealId}, #{name}, #{image}, #{dishFlavor}, #{number}, #{amount}, #{createTime})")
    void insert(ShoppingCart shoppingCart);

    @Delete("delete from shopping_cart where user_id = #{userId}")
    void clearShoppingCart(Long userId);

    void insertBatch(List<ShoppingCart> shoppingCartList);
}
