package com.yuegang.zhihui.training.api;import java.util.List;public record QuestionView(String id,String gateId,String type,String stem,List<String>options,String explanation,int score){}
