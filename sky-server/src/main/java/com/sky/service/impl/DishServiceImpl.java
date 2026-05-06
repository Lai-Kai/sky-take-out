package com.sky.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishFlavorService;
import com.sky.service.DishService;
import com.sky.service.SetMealDishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements DishService {

    @Autowired
    private DishFlavorService dishFlavorService;

    @Autowired
    private SetMealDishService setMealDishService;

    @Override
    @Transactional
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish);
        super.save(dish);
        List<DishFlavor> flavors = dishDTO.getFlavors();
        Long dishId = dish.getId();
        for (DishFlavor flavor : flavors) {
            flavor.setDishId(dishId);
        }
        if (flavors != null && flavors.size() > 0) {
            dishFlavorService.saveBatch(flavors);
        }
    }

    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        Page<DishVO> page = new Page<>(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        baseMapper.pageQuery(dishPageQueryDTO, page);
        return new PageResult(page.getTotal(), page.getRecords());
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        for (Long id : ids) {
            Dish dish = super.getById(id);
            if (dish.getStatus() == StatusConstant.ENABLE){
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }
        List<Long> setMealIds = setMealDishService.list(new LambdaQueryWrapper<SetmealDish>().in(SetmealDish::getDishId, ids))
                .stream().map(SetmealDish::getSetmealId).collect(Collectors.toList());
        if (setMealIds != null && setMealIds.size() > 0) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }
        removeByIds(ids);
        dishFlavorService.remove(new LambdaQueryWrapper<DishFlavor>().in(DishFlavor::getDishId, ids));

    }

    @Override
    public DishVO getByIdWithFlavor(Long id) {
        Dish dish = super.getById(id);
        List<DishFlavor> dishFlavors = dishFlavorService.list(new LambdaQueryWrapper<DishFlavor>().eq(DishFlavor::getDishId, id));
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish,dishVO);
        dishVO.setFlavors(dishFlavors);
        return dishVO;
    }

    @Override
    @Transactional
    public void updateWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish);
        updateById(dish);
        dishFlavorService.remove(new LambdaQueryWrapper<DishFlavor>().eq(DishFlavor::getDishId, dishDTO.getId()));
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            flavors.forEach(flavor -> {
                flavor.setDishId(dishDTO.getId());
            });
            dishFlavorService.saveBatch(flavors);
        }

    }

    @Override
    public List<Dish> getByCategoryId(Long categoryId) {
        return lambdaQuery()
                .eq(Dish::getStatus, StatusConstant.ENABLE)
                .eq(Dish::getCategoryId, categoryId)
                .list();
    }

    @Override
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishes = lambdaQuery()
                .eq(dish.getStatus() != null, Dish::getStatus, dish.getStatus())
                .eq(dish.getCategoryId() != null, Dish::getCategoryId, dish.getCategoryId())
                .like(dish.getName() != null, Dish::getName, dish.getName())
                .orderByAsc(Dish::getSort)
                .orderByDesc(Dish::getCreateTime)
                .list();
        List<DishVO> dishVOList = new ArrayList<>();
        for (Dish d : dishes){
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);
            List<DishFlavor> flavors = dishFlavorService.list(new LambdaQueryWrapper<DishFlavor>().eq(DishFlavor::getDishId, d.getId()));
            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }
        return dishVOList;
    }

    @Override
    public void startOrStop(Integer status, Long id) {
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();
        updateById(dish);
    }
}
