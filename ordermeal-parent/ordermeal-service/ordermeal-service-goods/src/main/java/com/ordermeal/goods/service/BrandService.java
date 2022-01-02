package com.ordermeal.goods.service;

import com.ordermeal.goods.pojo.Brand;

import java.util.List;

public interface BrandService {

    /***
     * 查询所有品牌
     * @return
     */
    List<Brand> findAll();

    /**
     * 根据ID查询
     * @param id
     * @return
     */
    Brand findById(Integer id);


    /***
     * 新增品牌
     * @param brand
     */
    void add(Brand brand);


    /***
     * 修改品牌数据
     * @param brand
     */
    void update(Brand brand);


    /***
     * 删除品牌
     * @param id
     */
    void delete(Integer id);


    /***
     * 多条件搜索品牌方法
     * @param brand
     * @return
     */
    List<Brand> findList(Brand brand);



}
