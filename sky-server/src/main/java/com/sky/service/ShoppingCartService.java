package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {
    void addShoppingCart(ShoppingCartDTO dto);

    List<ShoppingCart> showShoppingCart(Long userId);

    void deleteShoppingCart();
}
