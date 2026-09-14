package com.yuegang.zhihui.ai.api;public record EvaluationRunView(String runId,String caseId,boolean passed,double score,int citationCount,long durationMs,String failureReason){}
