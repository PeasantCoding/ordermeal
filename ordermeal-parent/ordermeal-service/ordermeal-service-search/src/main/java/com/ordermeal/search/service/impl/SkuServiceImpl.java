package com.ordermeal.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.ordermeal.entity.Result;
import com.ordermeal.goods.feign.SkuFeign;
import com.ordermeal.goods.pojo.Sku;
import com.ordermeal.search.dao.SkuEsMapper;
import com.ordermeal.search.pojo.SkuInfo;
import com.ordermeal.search.service.SkuService;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class SkuServiceImpl implements SkuService {

    @Autowired
    private SkuFeign skuFeign;

    @Autowired
    private SkuEsMapper skuEsMapper;

    @Autowired
    private ElasticsearchTemplate esTemplate;

    /**
     * 导入sku数据到es
     */
    @Override
    public void importSku(){
        //调用ordermeal-service-goods微服务
        Result<List<Sku>> skuListResult = skuFeign.findByStatus("1");
        //将数据转成SkuInfo
        List<SkuInfo> skuInfos=  JSON.parseArray(JSON.toJSONString(skuListResult.getData()),SkuInfo.class);
        for(SkuInfo skuInfo:skuInfos){
            Map<String, Object> specMap= JSON.parseObject(skuInfo.getSpec()) ;
            skuInfo.setSpecMap(specMap);
        }
        skuEsMapper.saveAll(skuInfos);
    }


    public Map search(Map<String, String> searchMap) {
        //1.获取到关键字
        String keywords = searchMap.get("keywords");

        //2.判断是否为空 如果 为空 给一个默认 值:华为
        if (StringUtils.isEmpty(keywords)) {
            keywords = "华为";
        }
        //3.创建 查询构建对象
        NativeSearchQueryBuilder nativeSearchQueryBuilder = new NativeSearchQueryBuilder();
        //4.设置 查询的条件

        // 4.1 商品分类的列表展示: 按照商品分类的名称来分组
        //terms  指定分组的一个别名
        //field 指定要分组的字段名
        //size 指定查询结果的数量 默认是10个
        nativeSearchQueryBuilder.addAggregation(AggregationBuilders.terms("skuCategorygroup").field("categoryName").size(50));

        //4.2 商品的品牌的列表展示  按照商品品牌来进行分组
        nativeSearchQueryBuilder.addAggregation(AggregationBuilders.terms("skuBrandgroup").field("brandName").size(100));

        //4.3 商品的规格的列表展示   按照商品的规格的字段spec 进行分组
        //规则 要求 字段 是一个keyword类型的  spec.keyword
        nativeSearchQueryBuilder.addAggregation(AggregationBuilders.terms("skuSpecgroup").field("spec.keyword").size(500));


        //4.4 设置高亮的字段 设置前缀 和 后缀

        //设置高亮的字段 针对 商品的名称进行高亮
        nativeSearchQueryBuilder.withHighlightFields(new HighlightBuilder.Field("name"));
        //设置前缀 和 后缀
        nativeSearchQueryBuilder.withHighlightBuilder(new HighlightBuilder().preTags("<em style=\"color:red\">").postTags("</em>"));




        //匹配查询  先分词 再查询  主条件查询
        //参数1 指定要搜索的字段
        //参数2 要搜索的值(先分词 再搜索)
        //nativeSearchQueryBuilder.withQuery(QueryBuilders.matchQuery("name", keywords));
        //从多个字段中搜索数据
        nativeSearchQueryBuilder.withQuery(QueryBuilders.multiMatchQuery(keywords,"name","categoryName","brandName"));



        //========================过滤查询 开始=====================================

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        //4.4 过滤查询的条件设置   商品分类的条件
        String category = searchMap.get("category");

        if(!StringUtils.isEmpty(category)) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("categoryName", category));
        }
        //4.5 过滤查询的条件设置   商品品牌的条件
        String brand = searchMap.get("brand");

        if(!StringUtils.isEmpty(brand)) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("brandName", brand));
        }

        //4.6 过滤查询的条件设置   规格条件

        if(searchMap!=null){
            for (String key : searchMap.keySet()) {//{ brand:"",category:"",spec_网络:"电信4G"}
                if(key.startsWith("spec_"))  {
                    //截取规格的名称
                    boolQueryBuilder.filter(QueryBuilders.termQuery("specMap."+key.substring(5)+".keyword", searchMap.get(key)));
                }
            }
        }
        //4.7 过滤查询的条件设置   价格区间的过滤查询
        String price = searchMap.get("price");// 0-500  3000-*
        if(!StringUtils.isEmpty(price)){
            //获取值 按照- 切割
            String[] split = price.split("-");
            //过滤范围查询
            //0<=price<=500
            if(!split[1].equals("*")) {
                boolQueryBuilder.filter(QueryBuilders.rangeQuery("price").from(split[0], true).to(split[1], true));
            }else{
                boolQueryBuilder.filter(QueryBuilders.rangeQuery("price").gte(split[0]));
            }

        }


        //过滤查询
        nativeSearchQueryBuilder.withFilter(boolQueryBuilder);

        //========================过滤查询 结束=====================================


        //分页查询

        //第一个参数:指定当前的页码  注意: 如果是第一页 数值为0
        //第二个参数:指定当前的页的显示的行
        String pageNum1 = searchMap.get("pageNum");
        Integer pageNum=Integer.valueOf(pageNum1);

        Integer pageSize=30;

        nativeSearchQueryBuilder.withPageable(PageRequest.of(pageNum-1,pageSize));


        //排序操作
        //获取排序的字段 和要排序的规则
        String sortField = searchMap.get("sortField");//price
        String sortRule = searchMap.get("sortRule");//DESC ASC
        if(!StringUtils.isEmpty(sortField) && !StringUtils.isEmpty(sortRule)) {
            //执行排序
            nativeSearchQueryBuilder.withSort(SortBuilders.fieldSort(sortField).order(sortRule.equalsIgnoreCase("ASC")?SortOrder.ASC:SortOrder.DESC));
            //nativeSearchQueryBuilder.withSort(SortBuilders.fieldSort(sortField).order(SortOrder.valueOf(sortRule)));
        }

        //5.构建查询对象(封装了查询的语法)
        NativeSearchQuery nativeSearchQuery = nativeSearchQueryBuilder.build();

        //6.执行查询
        AggregatedPage<SkuInfo> skuInfos = esTemplate.queryForPage(nativeSearchQuery, SkuInfo.class,new SearchResultMapperImpl());


        // 6.2 获取聚合分组结果  获取商品分类的列表数据
        StringTerms stringTermsCategory = (StringTerms) skuInfos.getAggregation("skuCategorygroup");
        List<String> categoryList = getStringsCategoryList(stringTermsCategory);


        //6.3 获取 品牌分组结果 列表数据

        StringTerms stringTermsBrand = (StringTerms) skuInfos.getAggregation("skuBrandgroup");
        List<String> brandList = getStringsBrandList(stringTermsBrand);

        //6.4 获取 规格的分组结果 列表数据map
        StringTerms stringTermsSpec = (StringTerms) skuInfos.getAggregation("skuSpecgroup");
        Map<String, Set<String>> specMap = getStringSetMap(stringTermsSpec);

        //7.获取结果  返回map

        List<SkuInfo> content = skuInfos.getContent();//当前的页的集合
        int totalPages = skuInfos.getTotalPages();//总页数
        long totalElements = skuInfos.getTotalElements();//总记录数

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("categoryList", categoryList);//商品分类的列表数据
        resultMap.put("brandList", brandList);   //商品品牌的列表数据
        resultMap.put("specMap", specMap);   //商品规格的列表数据展示
        resultMap.put("rows", content);
        resultMap.put("total", totalElements);
        resultMap.put("totalPages", totalPages);
        resultMap.put("pageNum",pageNum);
        resultMap.put("pageSize",pageSize);
        return resultMap;
    }


    /**
     * 获取分类列表数据
     *
     * @param stringTerms
     * @return
     */
    private List<String> getStringsCategoryList(StringTerms stringTerms) {
        List<String> categoryList = new ArrayList<>();
        if (stringTerms != null) {
            for (StringTerms.Bucket bucket : stringTerms.getBuckets()) {
                String keyAsString = bucket.getKeyAsString();//分组的值
                categoryList.add(keyAsString);
            }
        }
        return categoryList;
    }

    /**
     * 获取品牌列表
     *
     * @param stringTermsBrand
     * @return
     */
    private List<String> getStringsBrandList(StringTerms stringTermsBrand) {
        List<String> brandList = new ArrayList<>();
        if (stringTermsBrand != null) {
            for (StringTerms.Bucket bucket : stringTermsBrand.getBuckets()) {
                brandList.add(bucket.getKeyAsString());
            }
        }
        return brandList;
    }


    /**
     * 获取规格列表数据
     *
     * @param stringTermsSpec
     * @return
     */
    private Map<String, Set<String>> getStringSetMap(StringTerms stringTermsSpec) {
        Map<String, Set<String>> specMap = new HashMap<String, Set<String>>();
        Set<String> specList = new HashSet<>();
        if (stringTermsSpec != null) {
            for (StringTerms.Bucket bucket : stringTermsSpec.getBuckets()) {
                specList.add(bucket.getKeyAsString());
            }
        }
        for (String specjson : specList) {
            Map<String, String> map = JSON.parseObject(specjson, Map.class);
            for (Map.Entry<String, String> entry : map.entrySet()) {//
                String key = entry.getKey();        //规格名字
                String value = entry.getValue();    //规格选项值
                //获取当前规格名字对应的规格数据
                Set<String> specValues = specMap.get(key);
                if (specValues == null) {
                    specValues = new HashSet<String>();
                }
                //将当前规格加入到集合中
                specValues.add(value);
                //将数据存入到specMap中
                specMap.put(key, specValues);
            }
        }
        return specMap;
    }
}