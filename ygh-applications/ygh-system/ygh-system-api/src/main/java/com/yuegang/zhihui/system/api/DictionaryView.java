package com.yuegang.zhihui.system.api;
import java.util.List;
public record DictionaryView(String code,String name,List<Item> items){public record Item(String key,String value,int sortOrder){}}
